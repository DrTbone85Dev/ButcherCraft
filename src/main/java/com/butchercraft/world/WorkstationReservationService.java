package com.butchercraft.world;

import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerWorkstationReference;
import com.butchercraft.workstation.reservation.WorkstationReservationFailure;
import com.butchercraft.workstation.reservation.WorkstationReservationFailureCode;
import com.butchercraft.workstation.reservation.WorkstationReservationManager;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationRequest;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.workstation.reservation.WorkstationReservationSchema;
import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorkstationReservationService {
    public static final WorkstationReservationService INSTANCE = new WorkstationReservationService(
            EmployeeService.INSTANCE
    );

    private static final int OPERATING_ANCHOR_RADIUS = 1;
    private static final String GRINDER_TYPE = "grinder";
    private static final String PATTY_FORMER_TYPE = "patty_former";

    private final EmployeeService employeeService;
    private final AtomicReference<ActiveWorkstationReservations> active = new AtomicReference<>();

    WorkstationReservationService(EmployeeService employeeService) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService");
    }

    public void initialize(ServerStartedEvent event) {
        ActiveWorkstationReservations runtime = load(event.getServer());
        reconcileLoadedReservations(event.getServer(), runtime);
    }

    public void save(ServerStoppingEvent event) {
        ActiveWorkstationReservations current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.storage().save(current.manager().directory());
            active.compareAndSet(current, null);
        }
    }

    public WorkstationReservationManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public Optional<WorkstationReservationManager> currentManager() {
        return Optional.ofNullable(active.get()).map(ActiveWorkstationReservations::manager);
    }

    public WorkstationReservationResult<WorkstationReservationRecord> assign(
            ServerLevel level,
            EmployeeId employeeId,
            BlockPos workstationPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(workstationPos, "workstationPos");
        MinecraftServer server = level.getServer();
        EmployeeRecord employee = employeeService.managerFor(server).find(employeeId).orElse(null);
        if (employee == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value()
            );
        }
        EmployeePresenceObservation observation = employeeService.observe(server, employeeId).value().orElse(null);
        if (observation == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.MISSING_BUSINESS_RUNTIME,
                    "Business Runtime calendar is unavailable for workstation assignment"
            );
        }
        Optional<WorkstationReservationFailure> employeeFailure =
                validateEmployeeForAssignment(level, employee, observation);
        if (employeeFailure.isPresent()) {
            return WorkstationReservationResult.failed(
                    employeeFailure.orElseThrow().code(),
                    employeeFailure.orElseThrow().detail()
            );
        }
        WorkstationReservationResult<ResolvedWorkstationTarget> target = resolveSupportedWorkstation(level, workstationPos);
        if (!target.succeeded()) {
            return WorkstationReservationResult.failed(
                    target.failure().orElseThrow().code(),
                    target.failure().orElseThrow().detail()
            );
        }
        ResolvedWorkstationTarget value = target.orThrow();
        EmployeeEntityLink link = employee.entityLink().orElseThrow();
        if (!link.dimensionIdentity().equals(value.dimensionIdentity())) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.EMPLOYEE_DIFFERENT_WORLD,
                    "Employee and workstation are in different worlds"
            );
        }
        WorkstationReservationRequest request = new WorkstationReservationRequest(
                value.workstationIdentity(),
                value.workstationType(),
                employee.employeeId().value(),
                level.getGameTime(),
                value.dimensionIdentity(),
                value.workstationPos().getX(),
                value.workstationPos().getY(),
                value.workstationPos().getZ(),
                value.operatingPos().getX(),
                value.operatingPos().getY(),
                value.operatingPos().getZ(),
                OPERATING_ANCHOR_RADIUS
        );
        ActiveWorkstationReservations runtime = load(server);
        WorkstationReservationResult<WorkstationReservationRecord> result = runtime.manager().reserve(request);
        if (result.succeeded()) {
            runtime.storage().save(runtime.manager().directory());
        }
        return result;
    }

    public WorkstationReservationResult<WorkstationReservationRecord> release(
            MinecraftServer server,
            EmployeeId employeeId,
            String reason
    ) {
        ActiveWorkstationReservations runtime = load(server);
        WorkstationReservationResult<WorkstationReservationRecord> result =
                runtime.manager().releaseByEmployee(employeeId.value(), reason);
        if (result.succeeded()) {
            runtime.storage().save(runtime.manager().directory());
        }
        return result;
    }

    public Optional<WorkstationReservationRecord> invalidateByEmployee(
            MinecraftServer server,
            EmployeeId employeeId,
            String reason
    ) {
        ActiveWorkstationReservations runtime = load(server);
        Optional<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByEmployee(employeeId.value(), reason);
        invalidated.ifPresent(ignored -> runtime.storage().save(runtime.manager().directory()));
        return invalidated;
    }

    public Optional<WorkstationReservationRecord> invalidateByWorkstation(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        WorkstationReservationResult<ResolvedWorkstationTarget> target = resolveSupportedWorkstation(level, workstationPos);
        if (!target.succeeded()) {
            return Optional.empty();
        }
        ActiveWorkstationReservations runtime = load(level.getServer());
        Optional<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByWorkstation(target.orThrow().workstationIdentity(), reason);
        invalidated.ifPresent(ignored -> runtime.storage().save(runtime.manager().directory()));
        return invalidated;
    }

    public Optional<WorkstationReservationRecord> invalidateGrinder(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        return invalidateKnownWorkstation(
                level,
                GrinderWorkstationReference.of(level, workstationPos).identity(),
                reason
        );
    }

    public Optional<WorkstationReservationRecord> invalidatePattyFormer(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        return invalidateKnownWorkstation(
                level,
                PattyFormerWorkstationReference.of(level, workstationPos).identity(),
                reason
        );
    }

    public Optional<WorkstationNavigationTarget> navigationTargetFor(
            ServerLevel level,
            EmployeeRecord employee,
            EmployeePresenceObservation observation,
            BlockPos entityPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(entityPos, "entityPos");
        ActiveWorkstationReservations runtime = load(level.getServer());
        WorkstationReservationRecord record = runtime.manager()
                .findByEmployee(employee.employeeId().value())
                .orElse(null);
        if (record == null) {
            return Optional.empty();
        }
        if (!observation.plantOpen()
                || observation.presenceState() != EmployeePresenceState.PRESENT
                || observation.assignedDepartmentId().isEmpty()) {
            runtime.manager().invalidateByEmployee(
                    employee.employeeId().value(),
                    "employee no longer available for workstation reservation"
            );
            runtime.storage().save(runtime.manager().directory());
            return Optional.empty();
        }
        if (!record.dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))) {
            return Optional.empty();
        }
        Optional<ResolvedWorkstationTarget> resolved = resolvePersistedTarget(level, record);
        if (resolved.isEmpty()) {
            runtime.manager().invalidateByEmployee(
                    employee.employeeId().value(),
                    "reserved workstation is missing or invalid"
            );
            runtime.storage().save(runtime.manager().directory());
            return Optional.empty();
        }
        ResolvedWorkstationTarget target = resolved.orElseThrow();
        WorkstationReservationRecord current = record;
        if (current.operatingX() != target.operatingPos().getX()
                || current.operatingY() != target.operatingPos().getY()
                || current.operatingZ() != target.operatingPos().getZ()) {
            current = runtime.manager().updateOperatingPosition(
                    employee.employeeId().value(),
                    current.workstationIdentity(),
                    target.operatingPos().getX(),
                    target.operatingPos().getY(),
                    target.operatingPos().getZ()
            ).orElse(current);
            runtime.storage().save(runtime.manager().directory());
        }
        int arrivalRadius = current.anchorRadius();
        boolean inside = target.approachCandidates().stream()
                .anyMatch(candidate -> entityPos.distManhattan(candidate) <= arrivalRadius);
        Optional<WorkstationReservationRecord> transitioned = inside
                ? runtime.manager().markArrived(employee.employeeId().value(), current.workstationIdentity())
                : runtime.manager().markEnRoute(employee.employeeId().value(), current.workstationIdentity());
        WorkstationReservationRecord navigated = transitioned.orElse(current);
        if (transitioned.isPresent() && transitioned.orElseThrow().state() != current.state()) {
            runtime.storage().save(runtime.manager().directory());
        }
        EmployeeAnchor anchor = new EmployeeAnchor(
                target.dimensionIdentity(),
                target.operatingPos().getX(),
                target.operatingPos().getY(),
                target.operatingPos().getZ(),
                navigated.anchorRadius()
        );
        EmployeeNavigationState navigationState = inside
                ? EmployeeNavigationState.WAITING_AT_WORKSTATION
                : EmployeeNavigationState.WALKING_TO_WORKSTATION;
        return Optional.of(new WorkstationNavigationTarget(
                anchor,
                target.workstationPos(),
                target.approachCandidates(),
                navigationState,
                navigated
        ));
    }

    public List<WorkstationReservationRecord> activeReservations(MinecraftServer server) {
        return managerFor(server).activeReservations();
    }

    public Optional<WorkstationReservationRecord> findByWorkstation(ServerLevel level, BlockPos workstationPos) {
        return resolveSupportedWorkstation(level, workstationPos)
                .value()
                .flatMap(target -> managerFor(level.getServer()).findByWorkstation(target.workstationIdentity()));
    }

    public WorkstationReservationResult<ResolvedWorkstationStatus> status(ServerLevel level, BlockPos workstationPos) {
        WorkstationReservationResult<ResolvedWorkstationTarget> target = resolveSupportedWorkstation(level, workstationPos);
        if (!target.succeeded()) {
            return WorkstationReservationResult.failed(
                    target.failure().orElseThrow().code(),
                    target.failure().orElseThrow().detail()
            );
        }
        return WorkstationReservationResult.succeeded(new ResolvedWorkstationStatus(
                target.orThrow(),
                managerFor(level.getServer()).findByWorkstation(target.orThrow().workstationIdentity())
        ));
    }

    public void resetGameTestReservations(MinecraftServer server) {
        requireGameTestServer(server);
        ActiveWorkstationReservations runtime = load(server);
        ActiveWorkstationReservations reset = new ActiveWorkstationReservations(
                server,
                runtime.storage(),
                WorkstationReservationManager.empty()
        );
        active.set(reset);
        runtime.storage().save(reset.manager().directory());
    }

    public static Path reservationFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(WorkstationReservationSchema.DIRECTORY_NAME)
                .resolve(WorkstationReservationSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private Optional<WorkstationReservationFailure> validateEmployeeForAssignment(
            ServerLevel level,
            EmployeeRecord employee,
            EmployeePresenceObservation observation
    ) {
        if (!observation.plantOpen()) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.PLANT_CLOSED,
                    "Plant is closed; employee cannot begin a workstation trip"
            ));
        }
        if (observation.presenceState() != EmployeePresenceState.PRESENT) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.EMPLOYEE_NOT_PRESENT,
                    "Employee must be explicitly present before workstation assignment"
            ));
        }
        if (observation.assignedDepartmentId().isEmpty()) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.EMPLOYEE_MISSING_DEPARTMENT,
                    "Employee must have an assigned department before workstation assignment"
            ));
        }
        if (employee.entityLink().isEmpty()) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.EMPLOYEE_ENTITY_MISSING,
                    "Employee must have a live entity before workstation assignment"
            ));
        }
        EmployeeEntityLink link = employee.entityLink().orElseThrow();
        if (!link.dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.EMPLOYEE_DIFFERENT_WORLD,
                    "Employee entity is in a different world"
            ));
        }
        Entity entity = level.getEntity(link.entityUuid());
        if (!(entity instanceof EmployeeEntity) || entity.isRemoved()) {
            return Optional.of(new WorkstationReservationFailure(
                    WorkstationReservationFailureCode.EMPLOYEE_ENTITY_MISSING,
                    "Employee entity is not present in the target world"
            ));
        }
        return Optional.empty();
    }

    private WorkstationReservationResult<ResolvedWorkstationTarget> resolveSupportedWorkstation(
            ServerLevel level,
            BlockPos workstationPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(workstationPos, "workstationPos");
        BlockEntity blockEntity = level.getBlockEntity(workstationPos);
        BlockState state = level.getBlockState(workstationPos);
        if (blockEntity instanceof GrinderBlockEntity) {
            Direction facing = state.hasProperty(GrinderBlock.FACING)
                    ? state.getValue(GrinderBlock.FACING)
                    : Direction.NORTH;
            return WorkstationReservationResult.succeeded(new ResolvedWorkstationTarget(
                    GrinderWorkstationReference.of(level, workstationPos).identity(),
                    GRINDER_TYPE,
                    EmployeeService.dimensionIdentity(level),
                    workstationPos.immutable(),
                    operatingPosition(workstationPos, facing),
                    approachCandidates(workstationPos, facing)
            ));
        }
        if (blockEntity instanceof PattyFormerBlockEntity) {
            Direction facing = state.hasProperty(PattyFormerBlock.FACING)
                    ? state.getValue(PattyFormerBlock.FACING)
                    : Direction.NORTH;
            return WorkstationReservationResult.succeeded(new ResolvedWorkstationTarget(
                    PattyFormerWorkstationReference.of(level, workstationPos).identity(),
                    PATTY_FORMER_TYPE,
                    EmployeeService.dimensionIdentity(level),
                    workstationPos.immutable(),
                    operatingPosition(workstationPos, facing),
                    approachCandidates(workstationPos, facing)
            ));
        }
        return WorkstationReservationResult.failed(
                WorkstationReservationFailureCode.UNSUPPORTED_WORKSTATION,
                "Target block is not a supported Grinder or Patty Former workstation"
        );
    }

    private Optional<ResolvedWorkstationTarget> resolvePersistedTarget(
            ServerLevel level,
            WorkstationReservationRecord record
    ) {
        BlockPos workstationPos = new BlockPos(record.workstationX(), record.workstationY(), record.workstationZ());
        WorkstationReservationResult<ResolvedWorkstationTarget> resolved =
                resolveSupportedWorkstation(level, workstationPos);
        if (resolved.succeeded()) {
            ResolvedWorkstationTarget target = resolved.orThrow();
            if (!target.workstationIdentity().equals(record.workstationIdentity())) {
                return Optional.empty();
            }
            return Optional.of(target);
        }
        if (!level.hasChunkAt(workstationPos)) {
            return Optional.of(new ResolvedWorkstationTarget(
                    record.workstationIdentity(),
                    record.workstationType(),
                    record.dimensionIdentity(),
                    workstationPos,
                    new BlockPos(record.operatingX(), record.operatingY(), record.operatingZ()),
                    List.of(new BlockPos(record.operatingX(), record.operatingY(), record.operatingZ()))
            ));
        }
        return Optional.empty();
    }

    private ActiveWorkstationReservations load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveWorkstationReservations existing = active.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().directory());
        }
        WorkstationReservationStorage storage = new WorkstationReservationStorage(reservationFile(server));
        WorkstationReservationManager manager = new WorkstationReservationManager(storage.load());
        ActiveWorkstationReservations created = new ActiveWorkstationReservations(server, storage, manager);
        active.set(created);
        return created;
    }

    private Optional<WorkstationReservationRecord> invalidateKnownWorkstation(
            ServerLevel level,
            String workstationIdentity,
            String reason
    ) {
        ActiveWorkstationReservations runtime = load(level.getServer());
        Optional<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByWorkstation(workstationIdentity, reason);
        invalidated.ifPresent(ignored -> runtime.storage().save(runtime.manager().directory()));
        return invalidated;
    }

    private void reconcileLoadedReservations(
            MinecraftServer server,
            ActiveWorkstationReservations runtime
    ) {
        boolean changed = false;
        for (WorkstationReservationRecord record : runtime.manager().activeReservations()) {
            EmployeeId employeeId;
            try {
                employeeId = new EmployeeId(record.employeeIdentity());
            } catch (IllegalArgumentException exception) {
                runtime.manager().invalidateByEmployee(record.employeeIdentity(), "reservation employee identity is invalid");
                changed = true;
                continue;
            }
            Optional<EmployeeRecord> employee = employeeService.managerFor(server).find(employeeId);
            if (employee.isEmpty() || employee.orElseThrow().entityLink().isEmpty()) {
                runtime.manager().invalidateByEmployee(record.employeeIdentity(), "reserved employee is missing");
                changed = true;
                continue;
            }
            Optional<EmployeePresenceObservation> observation = employeeService.observe(server, employeeId).value();
            if (observation.isEmpty()
                    || !observation.orElseThrow().plantOpen()
                    || observation.orElseThrow().presenceState() != EmployeePresenceState.PRESENT
                    || observation.orElseThrow().assignedDepartmentId().isEmpty()) {
                runtime.manager().invalidateByEmployee(
                        record.employeeIdentity(),
                        "reserved employee is not available after reload"
                );
                changed = true;
                continue;
            }
            Optional<ServerLevel> level = loadedLevel(server, record.dimensionIdentity());
            if (level.isPresent()) {
                BlockPos workstationPos = new BlockPos(
                        record.workstationX(),
                        record.workstationY(),
                        record.workstationZ()
                );
                if (level.orElseThrow().hasChunkAt(workstationPos)
                        && resolvePersistedTarget(level.orElseThrow(), record).isEmpty()) {
                    runtime.manager().invalidateByEmployee(
                            record.employeeIdentity(),
                            "reserved workstation is missing after reload"
                    );
                    changed = true;
                }
            }
        }
        if (changed) {
            runtime.storage().save(runtime.manager().directory());
        }
    }

    private static BlockPos operatingPosition(BlockPos workstationPos, Direction facing) {
        Direction horizontal = Objects.requireNonNull(facing, "facing").getAxis().isHorizontal()
                ? facing
                : Direction.NORTH;
        return workstationPos.relative(horizontal).immutable();
    }

    private static List<BlockPos> approachCandidates(BlockPos workstationPos, Direction facing) {
        Direction front = Objects.requireNonNull(facing, "facing").getAxis().isHorizontal()
                ? facing
                : Direction.NORTH;
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        List<BlockPos> candidates = new ArrayList<>();
        addCandidate(candidates, workstationPos.relative(front));
        addCandidate(candidates, workstationPos.relative(front).relative(left));
        addCandidate(candidates, workstationPos.relative(front).relative(right));
        addCandidate(candidates, workstationPos.relative(left));
        addCandidate(candidates, workstationPos.relative(right));
        addCandidate(candidates, workstationPos.relative(front).relative(front));
        return List.copyOf(candidates);
    }

    private static void addCandidate(List<BlockPos> candidates, BlockPos candidate) {
        BlockPos immutable = candidate.immutable();
        if (!candidates.contains(immutable)) {
            candidates.add(immutable);
        }
    }

    private static void requireGameTestServer(MinecraftServer server) {
        String className = Objects.requireNonNull(server, "server").getClass().getName();
        if (!className.contains("GameTestServer")) {
            throw new IllegalStateException("Workstation reservation GameTest helpers may only run on the GameTest server");
        }
    }

    private static Optional<ServerLevel> loadedLevel(MinecraftServer server, String dimensionIdentity) {
        for (ServerLevel level : Objects.requireNonNull(server, "server").getAllLevels()) {
            if (EmployeeService.dimensionIdentity(level).equals(dimensionIdentity)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    public record WorkstationNavigationTarget(
            EmployeeAnchor anchor,
            BlockPos workstationPos,
            List<BlockPos> approachCandidates,
            EmployeeNavigationState navigationState,
            WorkstationReservationRecord reservation
    ) {
        public WorkstationNavigationTarget {
            anchor = Objects.requireNonNull(anchor, "anchor");
            workstationPos = Objects.requireNonNull(workstationPos, "workstationPos").immutable();
            approachCandidates = Objects.requireNonNull(approachCandidates, "approachCandidates").stream()
                    .map(BlockPos::immutable)
                    .toList();
            navigationState = Objects.requireNonNull(navigationState, "navigationState");
            reservation = Objects.requireNonNull(reservation, "reservation");
        }
    }

    public record ResolvedWorkstationStatus(
            ResolvedWorkstationTarget target,
            Optional<WorkstationReservationRecord> reservation
    ) {
        public ResolvedWorkstationStatus {
            target = Objects.requireNonNull(target, "target");
            reservation = Objects.requireNonNull(reservation, "reservation");
        }
    }

    public record ResolvedWorkstationTarget(
            String workstationIdentity,
            String workstationType,
            String dimensionIdentity,
            BlockPos workstationPos,
            BlockPos operatingPos,
            List<BlockPos> approachCandidates
    ) {
        public ResolvedWorkstationTarget {
            workstationIdentity = Objects.requireNonNull(workstationIdentity, "workstationIdentity");
            workstationType = Objects.requireNonNull(workstationType, "workstationType");
            dimensionIdentity = Objects.requireNonNull(dimensionIdentity, "dimensionIdentity");
            workstationPos = Objects.requireNonNull(workstationPos, "workstationPos").immutable();
            operatingPos = Objects.requireNonNull(operatingPos, "operatingPos").immutable();
            approachCandidates = Objects.requireNonNull(approachCandidates, "approachCandidates").stream()
                    .map(BlockPos::immutable)
                    .toList();
        }
    }

    private record ActiveWorkstationReservations(
            MinecraftServer server,
            WorkstationReservationStorage storage,
            WorkstationReservationManager manager
    ) {
        private ActiveWorkstationReservations {
            server = Objects.requireNonNull(server, "server");
            storage = Objects.requireNonNull(storage, "storage");
            manager = Objects.requireNonNull(manager, "manager");
        }
    }
}
