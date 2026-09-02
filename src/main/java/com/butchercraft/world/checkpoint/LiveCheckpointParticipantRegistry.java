package com.butchercraft.world.checkpoint;

import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.integration.checkpoint.LiveWorkstationCheckpointDependencyCollector;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationEndpointRuntimeService;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.projection.WorkstationProjectionReadCode;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.BusinessRuntimeCalendarService;
import com.butchercraft.world.BusinessRuntimeService;
import com.butchercraft.world.EconomicActorService;
import com.butchercraft.world.EconomicPlanningService;
import com.butchercraft.world.EmployeeMaterialHandlingService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.GoodService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.MachineOperatingStateService;
import com.butchercraft.world.OrderContractService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.TransactionService;
import com.butchercraft.world.WorkforceService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.business.runtime.BusinessRuntimeStorage;
import com.butchercraft.world.economy.actor.EconomicActorStorage;
import com.butchercraft.world.execution.persistence.ExecutionStorage;
import com.butchercraft.world.execution.persistence.MachineRunStorage;
import com.butchercraft.world.goods.GoodStorage;
import com.butchercraft.world.inventory.InventoryStorage;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.player.runtime.PlayerIdentityStorage;
import com.butchercraft.world.player.runtime.PlayerJoinInitializer;
import com.butchercraft.world.simulation.checkpoint.SimulationClockCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.time.WorldTimeService;
import com.butchercraft.world.transaction.TransactionStorage;
import com.butchercraft.world.workforce.WorkforceStorage;
import com.butchercraft.world.workforce.department.DepartmentStorage;
import com.butchercraft.world.workforce.employee.EmployeeStorage;
import com.butchercraft.world.workforce.materialhandling.persistence.EmployeeMaterialHandlingAssignmentStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class LiveCheckpointParticipantRegistry {
    private static final Path UNUSED_PATH = Path.of("checkpoint-owner-snapshot.json");

    private LiveCheckpointParticipantRegistry() {
    }

    public static List<CheckpointOwnerId> requiredOwners() {
        return LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS;
    }

    public static List<CheckpointOwnerSnapshotProvider> providers(MinecraftServer server, long checkpointTick) {
        Objects.requireNonNull(server, "server");
        String configurationIdentity = LivePlatformDeterminismManifest.IDENTITY;
        List<CheckpointOwnerSnapshotProvider> providers = new ArrayList<>();
        providers.add(new SimulationClockCheckpointSnapshotProvider(SimulationClockService.INSTANCE.clock(server)));
        providers.add(new SimulationSchedulerCheckpointSnapshotProvider(
                SimulationSchedulerService.INSTANCE.managerFor(server),
                SimulationSchedulerService.INSTANCE.currentRecoveryState(server)));
        providers.add(provider(LegacySplitRecoveryParticipants.EXECUTION, configurationIdentity,
                () -> execution(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.WORKSTATION, configurationIdentity,
                () -> workstation(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.MATERIAL_HANDLING, configurationIdentity,
                () -> materialHandling(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.PLANNING, configurationIdentity,
                () -> planning(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.PRODUCTION, configurationIdentity,
                () -> strings(LegacySplitRecoveryParticipants.PRODUCTION, 1, checkpointTick, false,
                        ProductionService.INSTANCE.checkpointSnapshotFiles(server))));
        providers.add(provider(LegacySplitRecoveryParticipants.TRANSACTIONS, configurationIdentity,
                () -> transactions(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.INVENTORY, configurationIdentity,
                () -> inventory(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.BUSINESS_RUNTIME, configurationIdentity,
                () -> businessRuntime(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.WORKFORCE, configurationIdentity,
                () -> workforce(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.GOODS, configurationIdentity,
                () -> goods(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.ECONOMIC_ACTORS, configurationIdentity,
                () -> economicActors(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.ORDERS, configurationIdentity,
                () -> strings(LegacySplitRecoveryParticipants.ORDERS, 1, checkpointTick,
                        OrderContractService.INSTANCE.orderManagerFor(server).definitions().isEmpty(),
                        Map.of("orders.json", OrderContractService.INSTANCE.checkpointSnapshotFiles(server)
                                .get("orders.json")))));
        providers.add(provider(LegacySplitRecoveryParticipants.CONTRACTS, configurationIdentity,
                () -> strings(LegacySplitRecoveryParticipants.CONTRACTS, 1, checkpointTick,
                        OrderContractService.INSTANCE.contractManagerFor(server).definitions().isEmpty(),
                        Map.of("contracts.json", OrderContractService.INSTANCE.checkpointSnapshotFiles(server)
                                .get("contracts.json")))));
        providers.add(provider(LegacySplitRecoveryParticipants.PLAYER_IDENTITY, configurationIdentity,
                () -> playerIdentity(server, checkpointTick)));
        providers.add(provider(LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY, configurationIdentity,
                () -> checkpointRecovery(server, checkpointTick)));
        return List.copyOf(providers);
    }

    private static CheckpointOwnerSnapshotProvider provider(
            CheckpointOwnerId owner,
            String configurationIdentity,
            java.util.function.Supplier<CheckpointOwnerFileSnapshot> source
    ) {
        return new CheckpointOwnerFileSnapshotProvider(owner, configurationIdentity, source);
    }

    private static CheckpointOwnerFileSnapshot execution(MinecraftServer server, long tick) {
        var manager = ExecutionService.INSTANCE.managerFor(server);
        var runs = ExecutionMachineRunService.INSTANCE.snapshot(server);
        Map<String, String> files = Map.of(
                "execution_operations.json",
                new ExecutionStorage(UNUSED_PATH, manager.handlerRegistry(), manager.configuration()).serialize(manager),
                "execution_machine_runs.json",
                new MachineRunStorage(UNUSED_PATH).serialize(runs)
        );
        return strings(LegacySplitRecoveryParticipants.EXECUTION, 1, tick,
                manager.operations().isEmpty() && runs.runs().isEmpty(), files);
    }

    private static CheckpointOwnerFileSnapshot workstation(MinecraftServer server, long tick) {
        var operating = MachineOperatingStateService.INSTANCE.snapshot(server);
        var instances = WorkstationEndpointService.INSTANCE.instanceRegistrySnapshot(server);
        var projections = WorkstationCheckpointProjectionService.capture(
                server,
                instances,
                LiveWorkstationCheckpointDependencyCollector.collect(server)
        );
        if (!projections.restorable()) {
            throw new CheckpointOwnerSnapshotRejectedException(projections.blockers().stream()
                    .map(blocker -> new CheckpointFailure(
                            workstationFailureCode(blocker.projectionState()),
                            blocker.instanceId().value(),
                            blocker.detail()))
                    .toList());
        }
        Map<String, String> files = new TreeMap<>();
        files.put("machine_operating_states.json", new MachineOperatingStorage(UNUSED_PATH).serialize(operating));
        files.put("workstation_instances.json", new WorkstationInstanceStorage(UNUSED_PATH).serialize(instances));
        files.put("workstation_projections.json", projections.json());
        boolean endpointJournalEmpty;
        var stackAware = StackAwareWorkstationEndpointRuntimeService.INSTANCE.currentJournal(server);
        if (stackAware.isPresent()) {
            files.put("workstation_endpoint_journal.json",
                    new WorkstationEndpointJournalV2Storage(UNUSED_PATH).serialize(stackAware.orElseThrow()));
            endpointJournalEmpty = stackAware.orElseThrow().records().isEmpty();
        } else {
            var legacy = WorkstationEndpointService.INSTANCE.legacyJournalSnapshot(server);
            files.put("workstation_endpoint_journal.json",
                    new WorkstationEndpointJournalStorage(UNUSED_PATH).serialize(legacy));
            endpointJournalEmpty = legacy.records().isEmpty();
        }
        boolean empty = operating.records().isEmpty()
                && instances.records().isEmpty()
                && endpointJournalEmpty
                && projections.requiredProjectionCount() == 0;
        return strings(LegacySplitRecoveryParticipants.WORKSTATION, 4, tick, empty, files);
    }

    private static CheckpointFailureCode workstationFailureCode(WorkstationProjectionReadCode code) {
        return switch (code) {
            case IDENTITY_CONFLICT -> CheckpointFailureCode.WORKSTATION_PROJECTION_IDENTITY_CONFLICT;
            case CORRUPT -> CheckpointFailureCode.WORKSTATION_PROJECTION_CORRUPT;
            case UNSUPPORTED_SCHEMA -> CheckpointFailureCode.WORKSTATION_PROJECTION_UNSUPPORTED_SCHEMA;
            case RECOVERY_REQUIRED -> CheckpointFailureCode.WORKSTATION_PROJECTION_RECOVERY_REQUIRED;
            case LEGACY_UNAVAILABLE, AVAILABLE, RETIRED ->
                    CheckpointFailureCode.WORKSTATION_REQUIRED_PROJECTION_UNAVAILABLE;
        };
    }

    private static CheckpointOwnerFileSnapshot materialHandling(MinecraftServer server, long tick) {
        var stackAware = MaterialHandlingService.INSTANCE.currentRuntimeV2(server);
        if (stackAware.isPresent()) {
            var runtime = stackAware.orElseThrow();
            return strings(LegacySplitRecoveryParticipants.MATERIAL_HANDLING, 2, runtime.ownerRevision(),
                    runtime.transfers().isEmpty(), Map.of("material_handling.json",
                            new MaterialHandlingStorageV2(UNUSED_PATH).serialize(runtime)));
        }
        var runtime = MaterialHandlingService.INSTANCE.currentRuntime().orElseThrow(() ->
                new IllegalStateException("Material Handling is not initialized"));
        return strings(LegacySplitRecoveryParticipants.MATERIAL_HANDLING, 1, runtime.ownerRevision(),
                runtime.transfers().isEmpty(), Map.of("material_handling.json",
                        new MaterialHandlingStorage(UNUSED_PATH).serialize(runtime)));
    }

    private static CheckpointOwnerFileSnapshot transactions(MinecraftServer server, long tick) {
        var inventory = InventoryService.INSTANCE.managerFor(server);
        var manager = TransactionService.INSTANCE.managerFor(server);
        return strings(LegacySplitRecoveryParticipants.TRANSACTIONS, 1, tick, manager.history().isEmpty(),
                Map.of("transactions.json", new TransactionStorage(UNUSED_PATH, inventory).serialize(manager)));
    }

    private static CheckpointOwnerFileSnapshot inventory(MinecraftServer server, long tick) {
        var actors = EconomicActorService.INSTANCE.managerFor(server).registry();
        var manager = InventoryService.INSTANCE.managerFor(server);
        return strings(LegacySplitRecoveryParticipants.INVENTORY, 1, tick, manager.registry().stream().findAny().isEmpty(),
                Map.of("inventory.json", new InventoryStorage(UNUSED_PATH, actors.goodRegistry(), actors)
                        .serialize(manager)));
    }

    private static CheckpointOwnerFileSnapshot businessRuntime(MinecraftServer server, long tick) {
        var manager = BusinessRuntimeService.INSTANCE.managerFor(server);
        return strings(LegacySplitRecoveryParticipants.BUSINESS_RUNTIME, 1, tick, false, Map.of(
                "business_runtime.json", new BusinessRuntimeStorage(UNUSED_PATH).serialize(manager.registry()),
                "business_calendar_runtime.json",
                BusinessRuntimeCalendarService.INSTANCE.checkpointSnapshotJson(server),
                "world_time.json", WorldTimeService.INSTANCE.checkpointSnapshotJson(server)
        ));
    }

    private static CheckpointOwnerFileSnapshot workforce(MinecraftServer server, long tick) {
        var workforce = WorkforceService.INSTANCE.managerFor(server);
        var employees = EmployeeService.INSTANCE.managerFor(server);
        var departments = EmployeeService.INSTANCE.departmentManagerFor(server);
        var assignments = EmployeeMaterialHandlingService.INSTANCE.managerFor(server);
        var reservations = WorkstationReservationService.INSTANCE.managerFor(server).directory();
        boolean empty = employees.registry().records().isEmpty()
                && assignments.assignments().isEmpty()
                && reservations.records().isEmpty();
        Map<String, String> files = new TreeMap<>();
        files.put("workforce_definitions.json", new WorkforceStorage(UNUSED_PATH).serialize(workforce.registry()));
        files.put("employee_records.json", new EmployeeStorage(UNUSED_PATH).serialize(employees.directory()));
        files.put("departments.json", new DepartmentStorage(UNUSED_PATH).serialize(departments.directory()));
        files.put("employee_material_handling_assignments.json",
                new EmployeeMaterialHandlingAssignmentStorage(UNUSED_PATH).serialize(assignments.directory()));
        files.put("workstation_reservations.json",
                new WorkstationReservationStorage(UNUSED_PATH).serialize(reservations));
        return strings(LegacySplitRecoveryParticipants.WORKFORCE, 2, tick, empty, files);
    }

    private static CheckpointOwnerFileSnapshot planning(MinecraftServer server, long tick) {
        Map<String, String> files = new TreeMap<>(EconomicPlanningService.INSTANCE.checkpointSnapshotFiles(server));
        Path recovery = EconomicPlanningService.rootDirectory(server)
                .resolve(com.butchercraft.world.planning.PlanningRecoveryState.FILE_NAME);
        if (Files.isRegularFile(recovery)) {
            files.put(com.butchercraft.world.planning.PlanningRecoveryState.FILE_NAME,
                    new String(read(recovery), StandardCharsets.UTF_8));
        }
        return strings(
                LegacySplitRecoveryParticipants.PLANNING,
                1,
                tick,
                EconomicPlanningService.INSTANCE.managerFor(server).cycles().isEmpty()
                        && !files.containsKey(com.butchercraft.world.planning.PlanningRecoveryState.FILE_NAME),
                files
        );
    }

    private static CheckpointOwnerFileSnapshot goods(MinecraftServer server, long tick) {
        var registry = GoodService.INSTANCE.managerFor(server).registry();
        return strings(LegacySplitRecoveryParticipants.GOODS, 1, tick, registry.definitions().isEmpty(),
                Map.of("goods.json", new GoodStorage(UNUSED_PATH, registry.knownIndustries()).serialize(registry)));
    }

    private static CheckpointOwnerFileSnapshot economicActors(MinecraftServer server, long tick) {
        var goods = GoodService.INSTANCE.managerFor(server).registry();
        var registry = EconomicActorService.INSTANCE.managerFor(server).registry();
        return strings(LegacySplitRecoveryParticipants.ECONOMIC_ACTORS, 1, tick, registry.definitions().isEmpty(),
                Map.of("economic_actors.json", new EconomicActorStorage(
                        UNUSED_PATH, goods, registry.knownIndustries()).serialize(registry)));
    }

    private static CheckpointOwnerFileSnapshot playerIdentity(MinecraftServer server, long tick) {
        var worldIdentity = WorldIdentityService.INSTANCE.getOrCreate(server);
        var registry = PlayerJoinInitializer.INSTANCE.managerFor(server).registry(worldIdentity);
        return strings(LegacySplitRecoveryParticipants.PLAYER_IDENTITY, 1, tick, registry.identities().isEmpty(),
                Map.of("player_identities.json", new PlayerIdentityStorage(UNUSED_PATH).serialize(registry)));
    }

    private static CheckpointOwnerFileSnapshot checkpointRecovery(MinecraftServer server, long tick) {
        Path publications = checkpointRoot(server).resolve(LegacySplitRecoveryPublicationStorage.DIRECTORY_NAME);
        List<String> evidence = new ArrayList<>();
        if (Files.isDirectory(publications)) {
            try (Stream<Path> paths = Files.walk(publications)) {
                paths.filter(Files::isRegularFile).sorted().forEach(path -> evidence.add(
                        publications.relativize(path).toString().replace('\\', '/') + "="
                                + CheckpointSnapshotDigest.sha256(read(path))));
            } catch (IOException exception) {
                throw new IllegalStateException("Checkpoint Recovery evidence index could not be frozen", exception);
            }
        }
        String json = "{\n  \"schema_version\": 1,\n  \"evidence\": ["
                + evidence.stream().map(value -> "\"" + value + "\"").collect(java.util.stream.Collectors.joining(","))
                + "]\n}\n";
        return strings(LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY, 1, tick, evidence.isEmpty(),
                Map.of("checkpoint_recovery_evidence_index.json", json));
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Checkpoint Recovery evidence could not be read", exception);
        }
    }

    private static CheckpointOwnerFileSnapshot strings(
            CheckpointOwnerId owner,
            int ownerSchema,
            long ownerSequence,
            boolean canonicalEmpty,
            Map<String, String> files
    ) {
        List<CheckpointOwnerFileSnapshot.FilePayload> payloads = files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CheckpointOwnerFileSnapshot.FilePayload(
                        entry.getKey(),
                        canonical(entry.getValue()).getBytes(StandardCharsets.UTF_8)
                ))
                .toList();
        return new CheckpointOwnerFileSnapshot(owner, ownerSchema, ownerSequence, canonicalEmpty, payloads);
    }

    private static String canonical(String value) {
        String normalized = Objects.requireNonNull(value, "ownerSnapshotJson").replace("\r\n", "\n");
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    public static Path checkpointRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve("butchercraft").resolve("checkpoints").toAbsolutePath().normalize();
    }
}
