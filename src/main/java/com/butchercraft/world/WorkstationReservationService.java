package com.butchercraft.world;

import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.cuttingtable.CuttingTableBlock;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.reservation.WorkstationReservationFailure;
import com.butchercraft.workstation.reservation.WorkstationReservationFailureCode;
import com.butchercraft.workstation.reservation.WorkstationReservationManager;
import com.butchercraft.workstation.reservation.WorkstationReservationEndpointScope;
import com.butchercraft.workstation.reservation.WorkstationReservationMigrationEvidence;
import com.butchercraft.workstation.reservation.LegacyWorkstationReservation;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationRequest;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.workstation.reservation.WorkstationReservationRole;
import com.butchercraft.workstation.reservation.WorkstationReservationSchema;
import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.materialhandling.MaterialTransferView;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentId;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentState;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
    private static final double OPERATING_HORIZONTAL_MARGIN = 0.1D;
    private static final double OPERATING_VERTICAL_MARGIN = 0.25D;
    private static final String GRINDER_TYPE = "grinder";
    private static final String PATTY_FORMER_TYPE = "patty_former";
    private static final String CUTTING_TABLE_TYPE = "cutting_table";

    private final EmployeeService employeeService;
    private final AtomicReference<ActiveWorkstationReservations> active = new AtomicReference<>();

    WorkstationReservationService(EmployeeService employeeService) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService");
    }

    public void initialize(ServerStartedEvent event) {
        ActiveWorkstationReservations runtime = load(event.getServer());
        if (mutationPermitted(event.getServer())) {
            reconcileLoadedReservations(event.getServer(), runtime);
            runtime.storage().save(runtime.manager().directory());
        }
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
        requireMutation(server);
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
        ActiveWorkstationReservations runtime = load(server);
        Optional<WorkstationReservationRecord> existing = runtime.manager().findByEmployee(employeeId.value());
        if (existing.filter(record -> record.role() == WorkstationReservationRole.MACHINE_OPERATOR
                && record.workstationIdentity().equals(value.workstationIdentity())).isPresent()) {
            return WorkstationReservationResult.succeeded(
                    existing.orElseThrow(),
                    com.butchercraft.workstation.reservation.WorkstationReservationSuccessCode
                            .EXISTING_RESERVATION_OBSERVED
            );
        }
        WorldIdentityRootIdentity worldIdentity = worldIdentity(server);
        Optional<String> assignmentReference = Optional.of(
                "butchercraft:transient_operator_assignment/v1/" + level.getGameTime() + "/"
                        + employeeId.value().replace(':', '/'));
        String requestIdentity = WorkstationReservationRequest.canonicalRequestIdentity(
                worldIdentity,
                value.workstationIdentity(),
                employee.employeeId().value(),
                WorkstationReservationRole.MACHINE_OPERATOR,
                assignmentReference,
                Optional.empty(),
                WorkstationReservationEndpointScope.none()
        );
        WorkstationReservationRequest request = WorkstationReservationRequest.machineOperator(
                worldIdentity,
                requestIdentity,
                value.workstationIdentity(),
                value.workstationGeneration(),
                value.workstationType(),
                employee.employeeId().value(),
                assignmentReference,
                level.getGameTime(),
                value.dimensionIdentity(),
                value.workstationPos().getX(), value.workstationPos().getY(), value.workstationPos().getZ(),
                value.operatingPos().getX(), value.operatingPos().getY(), value.operatingPos().getZ(),
                OPERATING_ANCHOR_RADIUS
        );
        WorkstationReservationResult<WorkstationReservationRecord> result = runtime.manager().reserve(request);
        if (result.succeeded()) {
            runtime.storage().save(runtime.manager().directory());
        }
        return result;
    }

    public WorkstationReservationResult<WorkstationReservationRecord> assignMaterialHandler(
            ServerLevel level,
            EmployeeId employeeId,
            BlockPos workstationPos,
            String assignmentReference,
            String transferReference,
            WorkstationReservationEndpointScope endpointScope,
            String lifecycleEvidence,
            long lifecycleEvidenceRevision
    ) {
        Objects.requireNonNull(endpointScope, "endpointScope");
        WorkstationReservationResult<ResolvedWorkstationTarget> target = resolveSupportedWorkstation(level, workstationPos);
        if (!target.succeeded()) {
            return WorkstationReservationResult.failed(
                    target.failure().orElseThrow().code(), target.failure().orElseThrow().detail());
        }
        EmployeeRecord employee = employeeService.managerFor(level.getServer()).find(employeeId).orElse(null);
        if (employee == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.UNKNOWN_EMPLOYEE, "Unknown employee: " + employeeId.value());
        }
        EmployeePresenceObservation observation = employeeService.observe(level.getServer(), employeeId).value().orElse(null);
        if (observation == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.MISSING_BUSINESS_RUNTIME,
                    "Business Runtime calendar is unavailable for workstation assignment");
        }
        Optional<WorkstationReservationFailure> employeeFailure = validateEmployeeForAssignment(level, employee, observation);
        if (employeeFailure.isPresent()) {
            WorkstationReservationFailure failure = employeeFailure.orElseThrow();
            return WorkstationReservationResult.failed(failure.code(), failure.detail());
        }
        ResolvedWorkstationTarget value = target.orThrow();
        if (!employee.entityLink().orElseThrow().dimensionIdentity().equals(value.dimensionIdentity())) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.EMPLOYEE_DIFFERENT_WORLD,
                    "Employee and workstation are in different worlds");
        }
        Optional<WorkstationReservationFailure> handlerFailure = validateMaterialHandlerEvidence(
                level,
                employeeId,
                value,
                assignmentReference,
                transferReference,
                endpointScope,
                lifecycleEvidence,
                lifecycleEvidenceRevision
        );
        if (handlerFailure.isPresent()) {
            WorkstationReservationFailure failure = handlerFailure.orElseThrow();
            return WorkstationReservationResult.failed(failure.code(), failure.detail());
        }
        WorldIdentityRootIdentity worldIdentity = worldIdentity(level.getServer());
        Optional<String> assignment = Optional.of(assignmentReference);
        Optional<String> transfer = Optional.of(transferReference);
        String requestIdentity = WorkstationReservationRequest.canonicalRequestIdentity(
                worldIdentity,
                value.workstationIdentity(),
                employeeId.value(),
                WorkstationReservationRole.MATERIAL_HANDLER,
                assignment,
                transfer,
                endpointScope
        );
        WorkstationReservationRequest request = WorkstationReservationRequest.materialHandler(
                worldIdentity,
                requestIdentity,
                value.workstationIdentity(),
                value.workstationGeneration(),
                value.workstationType(),
                employeeId.value(),
                assignmentReference,
                transferReference,
                endpointScope,
                lifecycleEvidence,
                lifecycleEvidenceRevision,
                level.getGameTime(),
                value.dimensionIdentity(),
                value.workstationPos().getX(), value.workstationPos().getY(), value.workstationPos().getZ(),
                value.operatingPos().getX(), value.operatingPos().getY(), value.operatingPos().getZ(),
                OPERATING_ANCHOR_RADIUS
        );
        requireMutation(level.getServer());
        ActiveWorkstationReservations runtime = load(level.getServer());
        WorkstationReservationResult<WorkstationReservationRecord> result = runtime.manager().reserve(request);
        if (result.succeeded()) runtime.storage().save(runtime.manager().directory());
        return result;
    }

    private Optional<WorkstationReservationFailure> validateMaterialHandlerEvidence(
            ServerLevel level,
            EmployeeId employeeId,
            ResolvedWorkstationTarget target,
            String assignmentReference,
            String transferReference,
            WorkstationReservationEndpointScope endpointScope,
            String lifecycleEvidence,
            long lifecycleEvidenceRevision
    ) {
        EmployeeMaterialHandlingAssignment assignment;
        MaterialTransferView transfer;
        try {
            assignment = EmployeeMaterialHandlingService.INSTANCE.managerFor(level.getServer())
                    .find(new EmployeeMaterialHandlingAssignmentId(assignmentReference))
                    .orElse(null);
            transfer = MaterialHandlingService.INSTANCE.findTransfer(
                    level.getServer(), new MaterialTransferId(transferReference)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return invalidHandlerEvidence("Handler reservation identity evidence is malformed");
        }
        if (assignment == null || transfer == null) {
            return invalidHandlerEvidence("Handler reservation requires an authoritative assignment and transfer");
        }
        if (!assignment.active()
                || !assignment.employeeId().equals(employeeId)
                || !assignment.transferId().value().equals(transfer.transferIdentity())
                || !assignment.binds(transfer.source(), transfer.destination())
                || transfer.employeeReference().filter(employeeId.value()::equals).isEmpty()) {
            return invalidHandlerEvidence(
                    "Handler reservation does not bind the authoritative employee, assignment, and transfer");
        }
        if (assignment.revision() != lifecycleEvidenceRevision
                || !transfer.lifecycle().name().equalsIgnoreCase(lifecycleEvidence)) {
            return invalidHandlerEvidence("Handler reservation lifecycle evidence is stale");
        }
        if (endpointScope.purpose()
                == com.butchercraft.workstation.reservation.WorkstationReservationEndpointPurpose.SOURCE_RETURN
                && (assignment.state() != EmployeeMaterialHandlingAssignmentState.CANCELLATION_REQUESTED
                || !transfer.hasProvenMaterialHandlingCustody())) {
            return invalidHandlerEvidence(
                    "Source-return access requires an explicit Workforce cancellation and proven Material Handling custody");
        }
        WorkstationEndpointReference expectedEndpoint = switch (endpointScope.purpose()) {
            case SOURCE, SOURCE_RETURN -> assignment.source();
            case DESTINATION -> assignment.destination();
            case NONE -> null;
        };
        if (expectedEndpoint == null
                || !endpointScope.endpointIdentity().orElseThrow()
                        .equals(expectedEndpoint.endpointKey().canonicalValue())
                || !transferEndpointMatchesTarget(expectedEndpoint, target)
                || !handlerLifecycleSupports(endpointScope, transfer.lifecycle())) {
            return invalidHandlerEvidence(
                    "Handler reservation endpoint scope is not authorized by the current transfer lifecycle");
        }
        return Optional.empty();
    }

    private static boolean transferEndpointMatchesTarget(
            WorkstationEndpointReference endpoint,
            ResolvedWorkstationTarget target
    ) {
        return endpoint.instanceId().value().equals(target.workstationIdentity())
                && endpoint.generation() == target.workstationGeneration()
                && endpoint.endpointKey().dimensionIdentity().equals(target.dimensionIdentity())
                && endpoint.endpointKey().x() == target.workstationPos().getX()
                && endpoint.endpointKey().y() == target.workstationPos().getY()
                && endpoint.endpointKey().z() == target.workstationPos().getZ();
    }

    private static boolean handlerLifecycleSupports(
            WorkstationReservationEndpointScope endpointScope,
            MaterialTransferLifecycle lifecycle
    ) {
        return switch (endpointScope.purpose()) {
            case SOURCE -> lifecycle == MaterialTransferLifecycle.REQUESTED
                    || lifecycle == MaterialTransferLifecycle.SOURCE_BOUND
                    || lifecycle == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED;
            case DESTINATION -> lifecycle == MaterialTransferLifecycle.IN_TRANSIT
                    || lifecycle == MaterialTransferLifecycle.DESTINATION_BOUND
                    || lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED;
            case SOURCE_RETURN -> lifecycle == MaterialTransferLifecycle.CANCELLATION_REQUESTED
                    || lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                    || lifecycle == MaterialTransferLifecycle.RECOVERY_REQUIRED
                    || lifecycle == MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED
                    || lifecycle == MaterialTransferLifecycle.IN_TRANSIT
                    || lifecycle == MaterialTransferLifecycle.DESTINATION_BOUND
                    || lifecycle == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED;
            case NONE -> false;
        };
    }

    private static Optional<WorkstationReservationFailure> invalidHandlerEvidence(String detail) {
        return Optional.of(new WorkstationReservationFailure(
                WorkstationReservationFailureCode.INVALID_HANDLER_TRANSFER,
                detail
        ));
    }

    public WorkstationReservationResult<WorkstationReservationRecord> release(
            MinecraftServer server,
            EmployeeId employeeId,
            String reason
    ) {
        requireMutation(server);
        ActiveWorkstationReservations runtime = load(server);
        WorkstationReservationResult<WorkstationReservationRecord> result =
                runtime.manager().releaseByEmployee(employeeId.value(), reason);
        if (result.succeeded()) {
            runtime.storage().save(runtime.manager().directory());
        }
        return result;
    }

    public WorkstationReservationResult<WorkstationReservationRecord> release(
            MinecraftServer server,
            WorkstationReservationRecord reservation,
            String reason
    ) {
        requireMutation(server);
        ActiveWorkstationReservations runtime = load(server);
        WorkstationReservationResult<WorkstationReservationRecord> result = runtime.manager().release(
                reservation.reservationId(), reservation.role(), reason);
        if (result.succeeded()) runtime.storage().save(runtime.manager().directory());
        return result;
    }

    public WorkstationReservationResult<WorkstationReservationRecord> invalidate(
            MinecraftServer server,
            WorkstationReservationRecord reservation,
            String reason
    ) {
        requireMutation(server);
        ActiveWorkstationReservations runtime = load(server);
        WorkstationReservationResult<WorkstationReservationRecord> result = runtime.manager().invalidate(
                reservation.reservationId(), reservation.role(), reason);
        if (result.succeeded()) runtime.storage().save(runtime.manager().directory());
        return result;
    }

    public Optional<WorkstationReservationRecord> invalidateByEmployee(
            MinecraftServer server,
            EmployeeId employeeId,
            String reason
    ) {
        requireMutation(server);
        ActiveWorkstationReservations runtime = load(server);
        Optional<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByEmployee(employeeId.value(), reason);
        invalidated.ifPresent(ignored -> runtime.storage().save(runtime.manager().directory()));
        return invalidated;
    }

    public List<WorkstationReservationRecord> invalidateByWorkstation(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        requireMutation(level.getServer());
        WorkstationReservationResult<ResolvedWorkstationTarget> target = resolveSupportedWorkstation(level, workstationPos);
        if (!target.succeeded()) {
            return List.of();
        }
        ActiveWorkstationReservations runtime = load(level.getServer());
        List<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByWorkstation(target.orThrow().workstationIdentity(), reason);
        if (!invalidated.isEmpty()) runtime.storage().save(runtime.manager().directory());
        return invalidated;
    }

    public List<WorkstationReservationRecord> invalidateGrinder(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        return invalidateResolvedWorkstation(level, workstationPos, reason);
    }

    public List<WorkstationReservationRecord> invalidatePattyFormer(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        return invalidateResolvedWorkstation(level, workstationPos, reason);
    }

    public List<WorkstationReservationRecord> invalidateCuttingTable(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        WorkstationEndpointReferenceResult reference = WorkstationEndpointService.INSTANCE.referenceFor(
                level,
                workstationPos
        );
        if (!reference.succeeded()) {
            return List.of();
        }
        return invalidateKnownWorkstation(
                level,
                reference.reference().orElseThrow().instanceId().value(),
                reason
        );
    }

    public Optional<WorkstationNavigationTarget> navigationTargetFor(
            ServerLevel level,
            EmployeeRecord employee,
            EmployeePresenceObservation observation,
            Vec3 entityPosition
    ) {
        requireMutation(level.getServer());
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(entityPosition, "entityPosition");
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
        boolean inside = withinOperatingTolerance(
                entityPosition,
                target.approachCandidates(),
                current.anchorRadius()
        );
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

    public List<WorkstationReservationRecord> reservationsForWorkstation(ServerLevel level, BlockPos workstationPos) {
        return resolveSupportedWorkstation(level, workstationPos)
                .value()
                .map(target -> managerFor(level.getServer()).reservationsForWorkstation(target.workstationIdentity()))
                .orElse(List.of());
    }

    public boolean hasActiveReservationAt(ServerLevel level, BlockPos workstationPos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(workstationPos, "workstationPos");
        String dimensionIdentity = EmployeeService.dimensionIdentity(level);
        return activeReservations(level.getServer()).stream().anyMatch(reservation ->
                reservation.dimensionIdentity().equals(dimensionIdentity)
                        && reservation.workstationX() == workstationPos.getX()
                        && reservation.workstationY() == workstationPos.getY()
                        && reservation.workstationZ() == workstationPos.getZ());
    }

    public boolean isWithinOperatingTolerance(
            ServerLevel level,
            WorkstationReservationRecord reservation,
            Vec3 employeePosition
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(employeePosition, "employeePosition");
        if (!reservation.dimensionIdentity().equals(EmployeeService.dimensionIdentity(level))) {
            return false;
        }
        return resolvePersistedTarget(level, reservation)
                .map(target -> withinOperatingTolerance(
                        employeePosition,
                        target.approachCandidates(),
                        reservation.anchorRadius()
                ))
                .orElse(false);
    }

    public static double operatingHorizontalTolerance(int anchorRadius) {
        return anchorRadius + OPERATING_HORIZONTAL_MARGIN;
    }

    public static double operatingVerticalTolerance(int anchorRadius) {
        return anchorRadius + OPERATING_VERTICAL_MARGIN;
    }

    private static boolean withinOperatingTolerance(
            Vec3 position,
            List<BlockPos> candidates,
            int anchorRadius
    ) {
        double horizontalTolerance = operatingHorizontalTolerance(anchorRadius);
        double verticalTolerance = operatingVerticalTolerance(anchorRadius);
        return candidates.stream().anyMatch(candidate -> {
            double dx = candidate.getX() + 0.5D - position.x;
            double dz = candidate.getZ() + 0.5D - position.z;
            return dx * dx + dz * dz <= horizontalTolerance * horizontalTolerance
                    && Math.abs(candidate.getY() - position.y) <= verticalTolerance;
        });
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
                managerFor(level.getServer()).operatorForWorkstation(target.orThrow().workstationIdentity()),
                managerFor(level.getServer()).handlerForWorkstation(target.orThrow().workstationIdentity())
        ));
    }

    public void resetGameTestReservations(MinecraftServer server) {
        requireGameTestServer(server);
        requireMutation(server);
        ActiveWorkstationReservations runtime = load(server);
        ActiveWorkstationReservations reset = new ActiveWorkstationReservations(
                server,
                runtime.storage(),
                WorkstationReservationManager.empty(worldIdentity(server))
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
        WorkstationEndpointReferenceResult referenceResult = WorkstationEndpointService.INSTANCE.referenceFor(
                level,
                workstationPos
        );
        if (!referenceResult.succeeded()) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.UNSUPPORTED_WORKSTATION,
                    "Workstation endpoint is unavailable: " + referenceResult.detail()
            );
        }
        WorkstationEndpointReference reference = referenceResult.reference().orElseThrow();
        if (blockEntity instanceof CuttingTableBlockEntity) {
            Direction facing = state.hasProperty(CuttingTableBlock.FACING)
                    ? state.getValue(CuttingTableBlock.FACING)
                    : Direction.NORTH;
            return WorkstationReservationResult.succeeded(new ResolvedWorkstationTarget(
                    reference.instanceId().value(),
                    reference.generation(),
                    reference.endpointKey().canonicalValue(),
                    CUTTING_TABLE_TYPE,
                    EmployeeService.dimensionIdentity(level),
                    workstationPos.immutable(),
                    operatingPosition(workstationPos, facing),
                    approachCandidates(workstationPos, facing)
            ));
        }
        if (blockEntity instanceof GrinderBlockEntity) {
            Direction facing = state.hasProperty(GrinderBlock.FACING)
                    ? state.getValue(GrinderBlock.FACING)
                    : Direction.NORTH;
            return WorkstationReservationResult.succeeded(new ResolvedWorkstationTarget(
                    reference.instanceId().value(),
                    reference.generation(),
                    reference.endpointKey().canonicalValue(),
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
                    reference.instanceId().value(),
                    reference.generation(),
                    reference.endpointKey().canonicalValue(),
                    PATTY_FORMER_TYPE,
                    EmployeeService.dimensionIdentity(level),
                    workstationPos.immutable(),
                    operatingPosition(workstationPos, facing),
                    approachCandidates(workstationPos, facing)
            ));
        }
        return WorkstationReservationResult.failed(
                WorkstationReservationFailureCode.UNSUPPORTED_WORKSTATION,
                "Target block is not a supported Cutting Table, Grinder, or Patty Former workstation"
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
            if (record.role() == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
                if (!target.workstationType().equals(record.workstationType())
                        || !target.dimensionIdentity().equals(record.dimensionIdentity())) {
                    return Optional.empty();
                }
                return Optional.of(new ResolvedWorkstationTarget(
                        record.workstationIdentity(),
                        record.workstationGeneration(),
                        "legacy-exclusive",
                        target.workstationType(),
                        target.dimensionIdentity(),
                        target.workstationPos(),
                        target.operatingPos(),
                        target.approachCandidates()
                ));
            }
            if (!target.workstationIdentity().equals(record.workstationIdentity())
                    || target.workstationGeneration() != record.workstationGeneration()) {
                return Optional.empty();
            }
            return Optional.of(target);
        }
        if (!level.hasChunkAt(workstationPos)) {
            return Optional.of(new ResolvedWorkstationTarget(
                    record.workstationIdentity(),
                    record.workstationGeneration(),
                    record.endpointScope().endpointIdentity().orElse("legacy-unavailable"),
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
        WorkstationReservationManager manager = new WorkstationReservationManager(storage.load(
                worldIdentity(server),
                legacy -> migrationEvidence(server, legacy)
        ));
        ActiveWorkstationReservations created = new ActiveWorkstationReservations(server, storage, manager);
        active.set(created);
        return created;
    }

    private List<WorkstationReservationRecord> invalidateKnownWorkstation(
            ServerLevel level,
            String workstationIdentity,
            String reason
    ) {
        requireMutation(level.getServer());
        ActiveWorkstationReservations runtime = load(level.getServer());
        List<WorkstationReservationRecord> invalidated =
                runtime.manager().invalidateByWorkstation(workstationIdentity, reason);
        if (!invalidated.isEmpty()) runtime.storage().save(runtime.manager().directory());
        return invalidated;
    }

    private List<WorkstationReservationRecord> invalidateResolvedWorkstation(
            ServerLevel level,
            BlockPos workstationPos,
            String reason
    ) {
        WorkstationEndpointReferenceResult reference = WorkstationEndpointService.INSTANCE.referenceFor(level, workstationPos);
        if (!reference.succeeded()) return List.of();
        return invalidateKnownWorkstation(level, reference.reference().orElseThrow().instanceId().value(), reason);
    }

    private Optional<WorkstationReservationMigrationEvidence> migrationEvidence(
            MinecraftServer server,
            LegacyWorkstationReservation legacy
    ) {
        if (!legacy.state().active()) return Optional.empty();
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(legacy.employeeIdentity());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        EmployeeMaterialHandlingAssignment assignment = EmployeeMaterialHandlingService.INSTANCE
                .managerFor(server)
                .activeFor(employeeId)
                .orElse(null);
        if (assignment == null) return Optional.empty();
        MaterialTransferView transfer = MaterialHandlingService.INSTANCE
                .findTransfer(server, assignment.transferId())
                .orElse(null);
        if (transfer == null
                || transfer.lifecycle().terminal()
                || transfer.employeeReference().filter(legacy.employeeIdentity()::equals).isEmpty()) {
            return Optional.empty();
        }
        WorkstationEndpointReference endpoint;
        WorkstationReservationEndpointScope scope;
        if (assignment.state() == EmployeeMaterialHandlingAssignmentState.CANCELLATION_REQUESTED
                && transfer.hasProvenMaterialHandlingCustody()) {
            endpoint = assignment.source();
            scope = WorkstationReservationEndpointScope.sourceReturn(endpoint.endpointKey().canonicalValue());
        } else if (transfer.hasProvenMaterialHandlingCustody()) {
            endpoint = assignment.destination();
            scope = WorkstationReservationEndpointScope.destination(endpoint.endpointKey().canonicalValue());
        } else {
            endpoint = assignment.source();
            scope = WorkstationReservationEndpointScope.source(endpoint.endpointKey().canonicalValue());
        }
        if (!legacyMatchesEndpoint(legacy, endpoint)) return Optional.empty();
        String lifecycle = transfer.lifecycle().name().toLowerCase(java.util.Locale.ROOT);
        String requestIdentity = WorkstationReservationRequest.canonicalRequestIdentity(
                worldIdentity(server),
                endpoint.instanceId().value(),
                legacy.employeeIdentity(),
                WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignment.assignmentId().value()),
                Optional.of(assignment.transferId().value()),
                scope
        );
        return Optional.of(new WorkstationReservationMigrationEvidence(
                requestIdentity,
                endpoint.instanceId().value(),
                endpoint.generation(),
                assignment.assignmentId().value(),
                assignment.transferId().value(),
                scope,
                lifecycle,
                assignment.revision()
        ));
    }

    private static boolean legacyMatchesEndpoint(
            LegacyWorkstationReservation legacy,
            WorkstationEndpointReference endpoint
    ) {
        String type = endpoint.endpointKey().workstationTypeIdentity();
        int separator = type.indexOf(':');
        String localType = separator >= 0 ? type.substring(separator + 1) : type;
        return legacy.workstationType().equals(localType)
                && legacy.dimensionIdentity().equals(endpoint.endpointKey().dimensionIdentity())
                && legacy.workstationX() == endpoint.endpointKey().x()
                && legacy.workstationY() == endpoint.endpointKey().y()
                && legacy.workstationZ() == endpoint.endpointKey().z();
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
            if (record.role() == WorkstationReservationRole.MATERIAL_HANDLER) {
                HandlerReconciliation reconciliation = reconcileHandlerBinding(server, record);
                if (reconciliation == HandlerReconciliation.TERMINAL) {
                    runtime.manager().release(
                            record.reservationId(),
                            WorkstationReservationRole.MATERIAL_HANDLER,
                            "authoritative Material Handling transfer is terminal after reload"
                    );
                    changed = true;
                    continue;
                }
                if (reconciliation == HandlerReconciliation.INVALID) {
                    runtime.manager().invalidateByEmployee(
                            record.employeeIdentity(),
                            "Material Handler reservation binding is invalid after reload"
                    );
                    changed = true;
                    continue;
                }
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

    private HandlerReconciliation reconcileHandlerBinding(
            MinecraftServer server,
            WorkstationReservationRecord record
    ) {
        EmployeeMaterialHandlingAssignment assignment;
        MaterialTransferView transfer;
        try {
            assignment = EmployeeMaterialHandlingService.INSTANCE.managerFor(server)
                    .find(new com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentId(
                            record.assignmentReference().orElseThrow()))
                    .orElse(null);
            transfer = MaterialHandlingService.INSTANCE.findTransfer(
                    server,
                    new com.butchercraft.world.materialhandling.MaterialTransferId(
                            record.transferReference().orElseThrow())
            ).orElse(null);
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            return HandlerReconciliation.INVALID;
        }
        if (assignment == null || transfer == null
                || !assignment.employeeId().value().equals(record.employeeIdentity())
                || !assignment.transferId().value().equals(record.transferReference().orElseThrow())
                || !transfer.transferIdentity().equals(assignment.transferId().value())
                || transfer.employeeReference().filter(record.employeeIdentity()::equals).isEmpty()
                || !assignment.source().equals(transfer.source())
                || !assignment.destination().equals(transfer.destination())) {
            return HandlerReconciliation.INVALID;
        }
        if (transfer.lifecycle().terminal() || assignment.state().terminal()) {
            return HandlerReconciliation.TERMINAL;
        }
        WorkstationEndpointReference endpoint = switch (record.endpointScope().purpose()) {
            case SOURCE, SOURCE_RETURN -> assignment.source();
            case DESTINATION -> assignment.destination();
            case NONE -> null;
        };
        return endpoint != null
                && record.endpointScope().endpointIdentity()
                        .filter(endpoint.endpointKey().canonicalValue()::equals).isPresent()
                && record.workstationIdentity().equals(endpoint.instanceId().value())
                && record.workstationGeneration() == endpoint.generation()
                ? HandlerReconciliation.VALID
                : HandlerReconciliation.INVALID;
    }

    private enum HandlerReconciliation {
        VALID,
        TERMINAL,
        INVALID
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

    private static boolean mutationPermitted(MinecraftServer server) {
        return StartupMutationGateService.INSTANCE.permits(server, LegacySplitRecoveryParticipants.WORKFORCE);
    }

    private static void requireMutation(MinecraftServer server) {
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKFORCE);
    }

    private static WorldIdentityRootIdentity worldIdentity(MinecraftServer server) {
        return WorldIdentityRootIdentities.from(WorldIdentityService.INSTANCE.getOrCreate(server));
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
            Optional<WorkstationReservationRecord> operatorReservation,
            Optional<WorkstationReservationRecord> handlerReservation
    ) {
        public ResolvedWorkstationStatus {
            target = Objects.requireNonNull(target, "target");
            operatorReservation = Objects.requireNonNull(operatorReservation, "operatorReservation");
            handlerReservation = Objects.requireNonNull(handlerReservation, "handlerReservation");
        }
    }

    public record ResolvedWorkstationTarget(
            String workstationIdentity,
            long workstationGeneration,
            String endpointIdentity,
            String workstationType,
            String dimensionIdentity,
            BlockPos workstationPos,
            BlockPos operatingPos,
            List<BlockPos> approachCandidates
    ) {
        public ResolvedWorkstationTarget {
            workstationIdentity = Objects.requireNonNull(workstationIdentity, "workstationIdentity");
            if (workstationGeneration < 0L) {
                throw new IllegalArgumentException("Workstation generation must not be negative");
            }
            endpointIdentity = Objects.requireNonNull(endpointIdentity, "endpointIdentity");
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
