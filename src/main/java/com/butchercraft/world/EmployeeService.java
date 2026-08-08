package com.butchercraft.world;

import com.butchercraft.config.CommonConfig;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.registration.ModEntityTypes;
import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.department.BuiltInDepartmentDefinitions;
import com.butchercraft.world.workforce.department.DepartmentAnchor;
import com.butchercraft.world.workforce.department.DepartmentId;
import com.butchercraft.world.workforce.department.DepartmentManager;
import com.butchercraft.world.workforce.department.DepartmentRecord;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.department.DepartmentStorage;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeDirectory;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeFailureCode;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeManager;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeeOperationResult;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeRegistry;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeShiftAssignment;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.workforce.employee.EmployeeStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EmployeeService {
    public static final EmployeeService INSTANCE = new EmployeeService(
            WorldIdentityService.INSTANCE,
            BusinessRuntimeCalendarService.INSTANCE,
            WorkforceService.INSTANCE
    );

    public static final String COMMAND_CREATION_SOURCE = "butchercraft:employee_creation/dev_command";
    public static final String GAME_TEST_CREATION_SOURCE = "butchercraft:employee_creation/gametest";
    private static final String ENTITY_TYPE_ID = "butchercraft:employee";
    private static final String LEGACY_GAME_TEST_DISPLAY_PREFIX = "Foundation ";

    private final WorldIdentityService worldIdentityService;
    private final BusinessRuntimeCalendarService businessRuntimeCalendarService;
    private final WorkforceService workforceService;
    private final AtomicReference<ActiveEmployeeRuntime> active = new AtomicReference<>();

    public EmployeeService(
            WorldIdentityService worldIdentityService,
            BusinessRuntimeCalendarService businessRuntimeCalendarService,
            WorkforceService workforceService
    ) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.businessRuntimeCalendarService = Objects.requireNonNull(
                businessRuntimeCalendarService,
                "businessRuntimeCalendarService"
        );
        this.workforceService = Objects.requireNonNull(workforceService, "workforceService");
    }

    public void initialize(ServerStartedEvent event) {
        managerFor(event.getServer());
    }

    public void save(ServerStoppingEvent event) {
        ActiveEmployeeRuntime current = active.get();
        if (current != null && current.server() == event.getServer()) {
            current.storage().save(current.manager().directory());
            current.departmentStorage().save(current.departmentManager().directory());
            active.compareAndSet(current, null);
        }
    }

    public EmployeeManager managerFor(MinecraftServer server) {
        return load(server).manager();
    }

    public DepartmentManager departmentManagerFor(MinecraftServer server) {
        return load(server).departmentManager();
    }

    public Optional<EmployeeManager> currentManager() {
        return Optional.ofNullable(active.get()).map(ActiveEmployeeRuntime::manager);
    }

    public EmployeeOperationResult<EmployeeRecord> createEmployee(
            ServerLevel level,
            Optional<String> displayName,
            Optional<BlockPos> anchorPos,
        boolean spawnEntity
    ) {
        return createEmployee(level, displayName, anchorPos, spawnEntity, COMMAND_CREATION_SOURCE);
    }

    public EmployeeOperationResult<EmployeeRecord> createGameTestEmployee(
            ServerLevel level,
            Optional<String> displayName,
            Optional<BlockPos> anchorPos,
            boolean spawnEntity
    ) {
        requireGameTestServer(level.getServer());
        return createEmployee(level, displayName, anchorPos, spawnEntity, GAME_TEST_CREATION_SOURCE);
    }

    public void resetGameTestEmployees(MinecraftServer server) {
        requireGameTestServer(server);
        ActiveEmployeeRuntime runtime = load(server);
        List<EmployeeRecord> retained = runtime.manager().registry().records().stream()
                .filter(record -> !isGameTestRecord(record))
                .toList();
        long nextSequence = retained.stream()
                .mapToLong(EmployeeRecord::sequence)
                .max()
                .orElse(-1L) + 1L;
        EmployeeDirectory directory = new EmployeeDirectory(nextSequence, EmployeeRegistry.of(retained));
        EmployeeManager manager = new EmployeeManager(directory, CommonConfig.EMPLOYEE_MAX_RECORDS.get());
        manager.validateBusinessReferences(worldIdentityService.getOrCreate(server).businesses());
        WorldIdentityRootIdentity rootIdentity = WorldIdentityRootIdentities.from(worldIdentityService.getOrCreate(server));
        DepartmentManager departmentManager = new DepartmentManager(BuiltInDepartmentDefinitions.defaults(rootIdentity));
        departmentManager.validateCanonicalDefinitions(rootIdentity);
        manager.validateDepartmentReferences(departmentManager.registry());
        ActiveEmployeeRuntime reset = new ActiveEmployeeRuntime(
                server,
                runtime.storage(),
                manager,
                runtime.departmentStorage(),
                departmentManager
        );
        active.set(reset);
        runtime.storage().save(reset.manager().directory());
        runtime.departmentStorage().save(reset.departmentManager().directory());
        WorkstationReservationService.INSTANCE.resetGameTestReservations(server);
        EmployeeMaterialHandlingService.INSTANCE.resetGameTestAssignments(server);
    }

    private EmployeeOperationResult<EmployeeRecord> createEmployee(
            ServerLevel level,
            Optional<String> displayName,
            Optional<BlockPos> anchorPos,
            boolean spawnEntity,
            String creationSourceIdentity
    ) {
        Objects.requireNonNull(level, "level");
        MinecraftServer server = level.getServer();
        ActiveEmployeeRuntime runtime = load(server);
        WorldIdentity worldIdentity = worldIdentityService.getOrCreate(server);
        Business business = defaultBusiness(worldIdentity).orElse(null);
        if (business == null) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.MISSING_BUSINESS,
                    "World Identity contains no business for employee creation"
            );
        }
        Optional<BusinessRuntimeObservationSnapshot> snapshot =
                businessRuntimeCalendarService.currentSnapshot(server);
        Optional<BusinessRuntimeCalendarConfiguration> configuration =
                businessRuntimeCalendarService.currentConfiguration(server);
        if (snapshot.isEmpty() || configuration.isEmpty()) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.MISSING_BUSINESS_RUNTIME,
                    "Business Runtime calendar is unavailable for employee creation"
            );
        }
        Optional<EmployeeShiftAssignment> defaultShift = defaultShift(configuration.orElseThrow());
        EmployeeOperationResult<EmployeeRecord> result = runtime.manager().createEmployee(
                WorldIdentityRootIdentities.from(worldIdentity),
                business,
                displayName,
                defaultShift,
                Optional.empty(),
                snapshot.orElseThrow().calendar(),
                creationSourceIdentity,
                configuration.orElseThrow().identity().value()
        );
        if (!result.succeeded() || !spawnEntity) {
            return result;
        }
        BlockPos spawnPos = anchorPos.orElse(BlockPos.containing(0.5D, level.getSharedSpawnPos().getY(), 0.5D));
        EmployeeOperationResult<EmployeeRecord> spawned = spawnAndBind(level, result.orThrow().employeeId(), spawnPos);
        if (!spawned.succeeded()) {
            return spawned;
        }
        return managerFor(server).find(result.orThrow().employeeId())
                .map(EmployeeOperationResult::succeeded)
                .orElse(spawned);
    }

    public EmployeeOperationResult<EmployeeRecord> spawnAndBind(ServerLevel level, EmployeeId employeeId, BlockPos anchorPos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(anchorPos, "anchorPos");
        EmployeeManager manager = managerFor(level.getServer());
        Optional<EmployeeRecord> existing = manager.find(employeeId);
        if (existing.isEmpty()) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Cannot spawn entity for unknown employee: " + employeeId.value()
            );
        }
        EmployeeEntity entity = ModEntityTypes.EMPLOYEE.get().create(level);
        if (entity == null) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.ENTITY_LINK_CONFLICT,
                    "Employee entity type could not create an entity"
            );
        }
        entity.moveTo(anchorPos.getX() + 0.5D, anchorPos.getY(), anchorPos.getZ() + 0.5D, 0.0F, 0.0F);
        EmployeeAnchor anchor = anchorFor(level, anchorPos);
        EmployeeEntityLink link = new EmployeeEntityLink(
                entity.getUUID(),
                ENTITY_TYPE_ID,
                dimensionIdentity(level)
        );
        EmployeeOperationResult<EmployeeRecord> bound = manager.bindEntity(employeeId, link, anchor);
        if (!bound.succeeded()) {
            entity.discard();
            return bound;
        }
        entity.applyEmployeeRecord(bound.orThrow(), anchor);
        level.addFreshEntity(entity);
        manager.setPresence(employeeId, EmployeePresenceState.PRESENT);
        return manager.find(employeeId)
                .map(EmployeeOperationResult::succeeded)
                .orElse(bound);
    }

    public EmployeeOperationResult<EmployeeRecord> assignShift(MinecraftServer server, EmployeeId employeeId, String shiftId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(employeeId, "employeeId");
        Optional<EmployeeShiftAssignment> shift = shiftAssignment(server, shiftId);
        if (shift.isEmpty()) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.MISSING_SHIFT,
                    "Unknown configured shift: " + shiftId
            );
        }
        EmployeeOperationResult<EmployeeRecord> result = managerFor(server).assignShift(employeeId, shift);
        if (result.succeeded()) {
            WorkstationReservationService.INSTANCE.invalidateByEmployee(
                    server,
                    employeeId,
                    "employee shift assignment changed"
            );
        }
        return result;
    }

    public EmployeeOperationResult<EmployeeRecord> transitionStatus(
            MinecraftServer server,
            EmployeeId employeeId,
            EmployeeStatus status
    ) {
        EmployeeOperationResult<EmployeeRecord> result = managerFor(server).transitionStatus(employeeId, status);
        if (result.succeeded() && !status.permitsPresence()) {
            WorkstationReservationService.INSTANCE.invalidateByEmployee(
                    server,
                    employeeId,
                    "employee status no longer permits workstation reservation"
            );
        }
        return result;
    }

    public EmployeeOperationResult<EmployeeRecord> setPresence(
            MinecraftServer server,
            EmployeeId employeeId,
            EmployeePresenceState state
    ) {
        EmployeeOperationResult<EmployeeRecord> result = managerFor(server).setPresence(employeeId, state);
        if (result.succeeded() && state != EmployeePresenceState.PRESENT) {
            WorkstationReservationService.INSTANCE.invalidateByEmployee(
                    server,
                    employeeId,
                    "employee presence no longer permits workstation reservation"
            );
        }
        return result;
    }

    public EmployeeOperationResult<EmployeeRecord> assignDepartment(
            MinecraftServer server,
            EmployeeId employeeId,
            String departmentIdValue
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(employeeId, "employeeId");
        DepartmentId departmentId;
        try {
            departmentId = new DepartmentId(departmentIdValue);
        } catch (IllegalArgumentException exception) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.INVALID_DEPARTMENT,
                    "Invalid department: " + departmentIdValue
            );
        }
        ActiveEmployeeRuntime runtime = load(server);
        Optional<DepartmentId> previous = runtime.manager().find(employeeId)
                .flatMap(EmployeeRecord::assignedDepartmentId);
        EmployeeOperationResult<EmployeeRecord> result = runtime.manager().assignDepartment(
                employeeId,
                Optional.of(departmentId),
                runtime.departmentManager().registry()
        );
        if (result.succeeded() && !previous.equals(result.orThrow().assignedDepartmentId())) {
            WorkstationReservationService.INSTANCE.invalidateByEmployee(
                    server,
                    employeeId,
                    "employee department assignment changed"
            );
        }
        return result;
    }

    public DepartmentAnchorUpdate assignDepartmentAnchor(
            ServerLevel level,
            DepartmentId departmentId,
            BlockPos anchorPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(departmentId, "departmentId");
        Objects.requireNonNull(anchorPos, "anchorPos");
        if (!level.isInWorldBounds(anchorPos) || !level.isLoaded(anchorPos)) {
            throw new IllegalArgumentException("Department anchor position must be loaded and inside world bounds: "
                    + anchorPos.getX() + " " + anchorPos.getY() + " " + anchorPos.getZ());
        }
        ActiveEmployeeRuntime runtime = load(level.getServer());
        DepartmentRecord previous = runtime.departmentManager()
                .find(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + departmentId.value()));
        int radius = previous.anchor()
                .map(DepartmentAnchor::radius)
                .orElseGet(() -> CommonConfig.EMPLOYEE_IDLE_ANCHOR_RADIUS.get());
        DepartmentAnchor nextAnchor = new DepartmentAnchor(
                dimensionIdentity(level),
                anchorPos.getX(),
                anchorPos.getY(),
                anchorPos.getZ(),
                radius
        );
        if (previous.anchor().filter(nextAnchor::equals).isPresent()) {
            return new DepartmentAnchorUpdate(previous, previous, previous.anchor(), nextAnchor, false);
        }
        DepartmentRecord updated = runtime.departmentManager().assignAnchor(departmentId, nextAnchor);
        runtime.departmentStorage().save(runtime.departmentManager().directory());
        return new DepartmentAnchorUpdate(previous, updated, previous.anchor(), nextAnchor, true);
    }

    public EmployeeOperationResult<EmployeePresenceObservation> observe(MinecraftServer server, EmployeeId employeeId) {
        Optional<BusinessRuntimeObservationSnapshot> snapshot = businessRuntimeCalendarService.currentSnapshot(server);
        Optional<BusinessRuntimeCalendarConfiguration> configuration =
                businessRuntimeCalendarService.currentConfiguration(server);
        if (snapshot.isEmpty() || configuration.isEmpty()) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.MISSING_BUSINESS_RUNTIME,
                    "Business Runtime calendar is unavailable for employee observation"
            );
        }
        return managerFor(server).observe(employeeId, snapshot.orElseThrow(), configuration.orElseThrow());
    }

    public boolean synchronizeEntity(EmployeeEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(entity.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        EmployeeManager manager = managerFor(level.getServer());
        Optional<EmployeeRecord> record = manager.find(employeeId);
        if (record.isEmpty()) {
            return false;
        }
        EmployeeRecord value = record.orElseThrow();
        EmployeeEntityLink link = new EmployeeEntityLink(entity.getUUID(), ENTITY_TYPE_ID, dimensionIdentity(level));
        EmployeeAnchor persistentAnchor = value.anchor().orElseGet(() ->
                anchorFor(level, entity.blockPosition()));
        EmployeeOperationResult<EmployeeRecord> bound = manager.bindEntity(employeeId, link, persistentAnchor);
        if (!bound.succeeded()) {
            return false;
        }
        Optional<EmployeePresenceObservation> observation = observe(level.getServer(), employeeId).value();
        EmployeeRecord boundRecord = bound.orThrow();
        Optional<EmployeeAnchor> departmentAnchor = observation
                .flatMap(valueObservation -> activeDepartmentAnchor(level, boundRecord, valueObservation));
        Optional<WorkstationReservationService.WorkstationNavigationTarget> workstationTarget = observation
                .flatMap(valueObservation -> WorkstationReservationService.INSTANCE.navigationTargetFor(
                        level,
                        boundRecord,
                        valueObservation,
                        entity.position()
                ));
        EmployeeAnchor activeAnchor = workstationTarget
                .map(WorkstationReservationService.WorkstationNavigationTarget::anchor)
                .or(() -> departmentAnchor)
                .or(() -> fallbackMovementAnchor(level, boundRecord))
                .orElse(persistentAnchor);
        entity.applyEmployeeRecord(boundRecord, activeAnchor);
        entity.applyWorkstationTravelTarget(workstationTarget);
        observation.ifPresent(entity::applyEmployeeObservation);
        entity.applyNavigationState(workstationTarget
                .map(WorkstationReservationService.WorkstationNavigationTarget::navigationState)
                .or(() -> observation
                .map(valueObservation -> navigationState(entity, activeAnchor, valueObservation, departmentAnchor.isPresent())))
                .orElse(EmployeeNavigationState.RETURNING_TO_ANCHOR));
        return true;
    }

    public void handleNavigationFailure(EmployeeEntity entity, String failureReason) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(failureReason, "failureReason");
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        EmployeeId employeeId;
        try {
            employeeId = new EmployeeId(entity.employeeIdValue());
        } catch (IllegalArgumentException exception) {
            return;
        }
        EmployeeMaterialHandlingService.INSTANCE.handleNavigationFailure(entity, failureReason);
        WorkstationReservationService.INSTANCE.invalidateByEmployee(
                level.getServer(),
                employeeId,
                "navigation_unreachable:" + failureReason
        );
    }

    public static Path employeeFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(EmployeeSchema.DIRECTORY_NAME)
                .resolve(EmployeeSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public static Path departmentFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(DepartmentSchema.DIRECTORY_NAME)
                .resolve(DepartmentSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public static String dimensionIdentity(ServerLevel level) {
        return Objects.requireNonNull(level, "level").dimension().location().toString();
    }

    private ActiveEmployeeRuntime load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveEmployeeRuntime existing = active.get();
        if (existing != null && existing.server() == server) {
            return existing;
        }
        if (existing != null) {
            existing.storage().save(existing.manager().directory());
            existing.departmentStorage().save(existing.departmentManager().directory());
        }
        WorldIdentity identity = worldIdentityService.getOrCreate(server);
        workforceService.managerFor(server);
        EmployeeStorage storage = new EmployeeStorage(employeeFile(server));
        DepartmentStorage departmentStorage = new DepartmentStorage(departmentFile(server));
        WorldIdentityRootIdentity rootIdentity = WorldIdentityRootIdentities.from(identity);
        DepartmentManager departmentManager = new DepartmentManager(departmentStorage.load(rootIdentity));
        departmentManager.validateCanonicalDefinitions(rootIdentity);
        EmployeeDirectory directory = storage.load();
        EmployeeManager manager = new EmployeeManager(
                directory,
                CommonConfig.EMPLOYEE_MAX_RECORDS.get()
        );
        manager.validateBusinessReferences(identity.businesses());
        manager.validateDepartmentReferences(departmentManager.registry());
        ActiveEmployeeRuntime created = new ActiveEmployeeRuntime(server, storage, manager, departmentStorage, departmentManager);
        active.set(created);
        return created;
    }

    private Optional<EmployeeAnchor> activeDepartmentAnchor(
            ServerLevel level,
            EmployeeRecord record,
            EmployeePresenceObservation observation
    ) {
        ActiveEmployeeRuntime runtime = load(level.getServer());
        if (observation.plantOpen() && observation.presenceState() == EmployeePresenceState.PRESENT) {
            return record.assignedDepartmentId()
                    .flatMap(departmentId -> runtime.departmentManager().find(departmentId))
                    .flatMap(DepartmentRecord::anchor)
                    .filter(anchor -> anchor.sameDimension(dimensionIdentity(level)))
                    .map(EmployeeService::employeeAnchor);
        }
        return Optional.empty();
    }

    private Optional<EmployeeAnchor> fallbackMovementAnchor(ServerLevel level, EmployeeRecord record) {
        ActiveEmployeeRuntime runtime = load(level.getServer());
        return runtime.departmentManager().directory().plantEntranceAnchor()
                .filter(anchor -> anchor.sameDimension(dimensionIdentity(level)))
                .map(EmployeeService::employeeAnchor)
                .or(() -> record.anchor());
    }

    private static EmployeeNavigationState navigationState(
            EmployeeEntity entity,
            EmployeeAnchor activeAnchor,
            EmployeePresenceObservation observation,
            boolean hasDepartmentAnchor
    ) {
        boolean inside = entity.blockPosition().distManhattan(
                new BlockPos(activeAnchor.x(), activeAnchor.y(), activeAnchor.z())
        ) <= activeAnchor.radius();
        if (observation.plantOpen() && observation.presenceState() == EmployeePresenceState.PRESENT
                && hasDepartmentAnchor) {
            return inside ? EmployeeNavigationState.IDLE : EmployeeNavigationState.WALKING_TO_DEPARTMENT;
        }
        if (observation.plantOpen() && observation.presenceState() == EmployeePresenceState.PRESENT) {
            return inside ? EmployeeNavigationState.IDLE : EmployeeNavigationState.RETURNING_TO_ANCHOR;
        }
        return inside ? EmployeeNavigationState.OFF_SHIFT : EmployeeNavigationState.RETURNING_TO_ANCHOR;
    }

    private static EmployeeAnchor employeeAnchor(DepartmentAnchor anchor) {
        return new EmployeeAnchor(anchor.dimensionIdentity(), anchor.x(), anchor.y(), anchor.z(), anchor.radius());
    }

    private Optional<EmployeeShiftAssignment> shiftAssignment(MinecraftServer server, String shiftId) {
        BusinessRuntimeCalendarConfiguration configuration = businessRuntimeCalendarService
                .currentConfiguration(server)
                .orElse(null);
        if (configuration == null) {
            return Optional.empty();
        }
        String normalized = Objects.requireNonNull(shiftId, "shiftId").strip();
        return configuration.shiftSet().shifts().stream()
                .filter(shift -> shift.id().equals(normalized))
                .findFirst()
                .map(shift -> EmployeeShiftAssignment.from(
                        shift,
                        configuration.shiftSet(),
                        configuration.identity()
                ));
    }

    private Optional<EmployeeShiftAssignment> defaultShift(BusinessRuntimeCalendarConfiguration configuration) {
        return Objects.requireNonNull(configuration, "configuration").shiftSet().shifts().stream()
                .sorted(Comparator.comparing(BusinessShiftDefinition::id))
                .findFirst()
                .map(shift -> EmployeeShiftAssignment.from(shift, configuration.shiftSet(), configuration.identity()));
    }

    private Optional<Business> defaultBusiness(WorldIdentity worldIdentity) {
        return Objects.requireNonNull(worldIdentity, "worldIdentity").businesses().stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .findFirst();
    }

    private static boolean isGameTestRecord(EmployeeRecord record) {
        return record.creationSourceIdentity().equals(GAME_TEST_CREATION_SOURCE)
                || record.displayName().startsWith(LEGACY_GAME_TEST_DISPLAY_PREFIX);
    }

    private static void requireGameTestServer(MinecraftServer server) {
        String className = Objects.requireNonNull(server, "server").getClass().getName();
        if (!className.contains("GameTestServer")) {
            throw new IllegalStateException("Employee GameTest helpers may only run on the GameTest server");
        }
    }

    private static EmployeeAnchor anchorFor(ServerLevel level, BlockPos pos) {
        return new EmployeeAnchor(
                dimensionIdentity(level),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                CommonConfig.EMPLOYEE_IDLE_ANCHOR_RADIUS.get()
        );
    }

    private record ActiveEmployeeRuntime(
            MinecraftServer server,
            EmployeeStorage storage,
            EmployeeManager manager,
            DepartmentStorage departmentStorage,
            DepartmentManager departmentManager
    ) {
    }

    public record DepartmentAnchorUpdate(
            DepartmentRecord previousRecord,
            DepartmentRecord updatedRecord,
            Optional<DepartmentAnchor> previousAnchor,
            DepartmentAnchor newAnchor,
            boolean changed
    ) {
        public DepartmentAnchorUpdate {
            previousRecord = Objects.requireNonNull(previousRecord, "previousRecord");
            updatedRecord = Objects.requireNonNull(updatedRecord, "updatedRecord");
            previousAnchor = Objects.requireNonNull(previousAnchor, "previousAnchor");
            newAnchor = Objects.requireNonNull(newAnchor, "newAnchor");
        }
    }
}
