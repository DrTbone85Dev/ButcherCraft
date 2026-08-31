package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.RecoveryMutationGate;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.execution.persistence.ExecutionStorage;
import com.butchercraft.world.execution.persistence.MachineRunStorage;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.materialhandling.MaterialHandlingSchema;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;
import com.butchercraft.world.planning.PlanningRecoveryState;
import com.butchercraft.world.planning.PlanningRecoveryStorage;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.SimulationStateStorage;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.persistence.SchedulerRecoveryStorage;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;

public final class LiveOwnerCoherenceAnalyzer {
    private static final RecoveryMutationGate OPEN_GATE = new RecoveryMutationGate(1, false, List.of(), List.of());
    private static final Map<String, Set<Integer>> SUPPORTED_SCHEMAS = supportedSchemas();

    public LiveOwnerCoherenceReport analyze(
            MinecraftServer server,
            Path ownerRoot,
            WorldIdentityRootReference worldIdentity
    ) {
        long started = System.nanoTime();
        Path root = ownerRoot.toAbsolutePath().normalize();
        List<StartupRecoveryIssue> issues = new ArrayList<>();
        Map<String, JsonObject> documents = readDocuments(root, issues);
        validateInterruptedPublications(root, issues);
        validateSchemas(documents, issues);
        validateWorldIdentity(documents, worldIdentity, issues);

        long clockTick = readClockTick(server, root, documents, issues);
        long schedulerTick = readSchedulerTick(root, documents, clockTick, issues);
        if (clockTick != schedulerTick) {
            issues.add(issue(
                    StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT,
                    CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                    "Clock tick " + clockTick + " differs from Scheduler tick " + schedulerTick));
        }
        validateExecutionAndWorkstation(server, root, documents, issues);
        validateMaterialHandling(root, documents, issues);
        RecoveryMutationGate gate = readRecoveryGate(root, documents, schedulerTick, issues);
        boolean empty = documents.isEmpty();
        LiveOwnerCoherenceStatus status = issues.isEmpty()
                ? (empty ? LiveOwnerCoherenceStatus.COHERENT_EMPTY : LiveOwnerCoherenceStatus.COHERENT)
                : LiveOwnerCoherenceStatus.INCOHERENT;
        return new LiveOwnerCoherenceReport(
                status,
                OptionalLong.of(clockTick),
                OptionalLong.of(schedulerTick),
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                issues,
                gate,
                System.nanoTime() - started
        );
    }

    private Map<String, JsonObject> readDocuments(Path root, List<StartupRecoveryIssue> issues) {
        Map<String, JsonObject> documents = new HashMap<>();
        if (!Files.isDirectory(root)) return documents;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                String name = path.getFileName().toString();
                try {
                    JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                    if (!parsed.isJsonObject()) throw new IllegalArgumentException("root is not an object");
                    documents.put(name, parsed.getAsJsonObject());
                } catch (RuntimeException | IOException exception) {
                    issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID, ownerFor(name),
                            "Live owner persistence is malformed: " + name));
                }
            }
        } catch (IOException exception) {
            issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                    LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY,
                    "Live owner persistence directory cannot be read"));
        }
        return documents;
    }

    private void validateInterruptedPublications(Path root, List<StartupRecoveryIssue> issues) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path artifact : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(".json.tmp"))
                    .sorted().toList()) {
                String name = artifact.getFileName().toString();
                int marker = name.indexOf(".json.tmp");
                Path target = root.resolve(name.substring(0, marker + ".json".length()));
                if (name.endsWith(".json.tmp") && Files.isRegularFile(target)
                        && Arrays.equals(read(target), read(artifact))) {
                    continue;
                }
                issues.add(issue(StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT,
                        ownerFor(target.getFileName().toString()),
                        "Interrupted or differing owner publication requires checkpoint recovery: " + name));
            }
        } catch (IOException exception) {
            issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                    LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY,
                    "Owner publication artifacts cannot be inspected"));
        }
    }

    private void validateSchemas(Map<String, JsonObject> documents, List<StartupRecoveryIssue> issues) {
        documents.forEach((name, root) -> {
            Set<Integer> supported = SUPPORTED_SCHEMAS.get(name);
            if (supported == null) return;
            JsonElement value = root.get("schema_version");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || !supported.contains(value.getAsInt())) {
                issues.add(issue(StartupRecoveryFailureCode.OWNER_SCHEMA_UNSUPPORTED, ownerFor(name),
                        "Unsupported live owner schema: " + name));
            }
        });
    }

    private void validateWorldIdentity(
            Map<String, JsonObject> documents,
            WorldIdentityRootReference expected,
            List<StartupRecoveryIssue> issues
    ) {
        documents.forEach((name, document) -> {
            try {
                validateWorldElement(document, expected);
            } catch (IllegalArgumentException exception) {
                issues.add(issue(StartupRecoveryFailureCode.WORLD_IDENTITY_MISMATCH, ownerFor(name),
                        "Live owner World Identity mismatch: " + name));
            }
        });
    }

    private long readClockTick(
            MinecraftServer server,
            Path root,
            Map<String, JsonObject> documents,
            List<StartupRecoveryIssue> issues
    ) {
        if (!documents.containsKey("simulation_state.json")) return 0L;
        try {
            return new SimulationStateStorage(root.resolve("simulation_state.json"),
                    SimulationClockService.INSTANCE.configuration())
                    .deserialize(documents.get("simulation_state.json").toString()).simulationTick();
        } catch (RuntimeException exception) {
            issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                    CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                    "Clock live state failed owner validation"));
            return 0L;
        }
    }

    private long readSchedulerTick(
            Path root,
            Map<String, JsonObject> documents,
            long clockTick,
            List<StartupRecoveryIssue> issues
    ) {
        if (!documents.containsKey(SchedulerSchema.FILE_NAME)) return clockTick;
        try {
            return new SimulationSchedulerStorage(
                    root.resolve(SchedulerSchema.FILE_NAME),
                    SimulationSchedulerService.INSTANCE.configuredHandlerRegistry(),
                    clockTick
            ).deserialize(documents.get(SchedulerSchema.FILE_NAME).toString()).lastFinalizedSimulationTick();
        } catch (RuntimeException exception) {
            issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                    CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                    "Scheduler live state failed owner validation"));
            return clockTick;
        }
    }

    private void validateExecutionAndWorkstation(
            MinecraftServer server,
            Path root,
            Map<String, JsonObject> documents,
            List<StartupRecoveryIssue> issues
    ) {
        try {
            Optional<ExecutionManager> execution = documents.containsKey("execution_operations.json")
                    ? Optional.of(new ExecutionStorage(
                    root.resolve("execution_operations.json"),
                    ExecutionService.INSTANCE.configuredHandlerRegistry(server),
                    ExecutionService.INSTANCE.configuration()
            ).deserialize(documents.get("execution_operations.json").toString()))
                    : Optional.empty();
            Optional<MachineRunRegistry> runs = documents.containsKey("execution_machine_runs.json")
                    ? Optional.of(new MachineRunStorage(root.resolve("execution_machine_runs.json"))
                    .deserialize(documents.get("execution_machine_runs.json").toString()))
                    : Optional.empty();
            Optional<WorkstationInstanceRegistry> instances = documents.containsKey("workstation_instances.json")
                    ? Optional.of(new WorkstationInstanceStorage(root.resolve("workstation_instances.json"))
                    .deserialize(documents.get("workstation_instances.json").toString()))
                    : Optional.empty();
            Optional<MachineOperatingRegistry> operating = documents.containsKey("machine_operating_states.json")
                    ? Optional.of(new MachineOperatingStorage(root.resolve("machine_operating_states.json"))
                    .deserialize(documents.get("machine_operating_states.json").toString()))
                    : Optional.empty();
            if (execution.isPresent() && runs.isPresent()) {
                runs.orElseThrow().runs().forEach(run -> run.currentChild().ifPresent(child -> {
                    if (execution.orElseThrow().find(child.operationId()).isEmpty()) {
                        throw new IllegalArgumentException("Machine Run child references an unknown Execution operation");
                    }
                }));
            }
            if (instances.isPresent() && operating.isPresent()) {
                operating.orElseThrow().records().forEach(record -> {
                    if (instances.orElseThrow().find(record.workstation().instanceId()).isEmpty()) {
                        throw new IllegalArgumentException("Machine operating state references an unknown Workstation");
                    }
                    record.currentRunIdentity().ifPresent(runIdentity -> {
                        if (runs.isEmpty() || runs.orElseThrow().find(runIdentity).isEmpty()) {
                            throw new IllegalArgumentException("Machine operating state references an unknown Run");
                        }
                    });
                });
            }
        } catch (RuntimeException exception) {
            issues.add(issue(StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT,
                    LegacySplitRecoveryParticipants.EXECUTION,
                    "Execution, Machine Run, or Workstation live relationship is incoherent"));
        }
    }

    private void validateMaterialHandling(
            Path root,
            Map<String, JsonObject> documents,
            List<StartupRecoveryIssue> issues
    ) {
        JsonObject document = documents.get("material_handling.json");
        if (document == null) return;
        try {
            int schema = document.get("schema_version").getAsInt();
            if (schema == MaterialHandlingSchema.LEGACY_SCHEMA_VERSION) {
                new MaterialHandlingStorage(root.resolve("material_handling.json")).deserialize(document.toString());
            } else if (schema == MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
                new MaterialHandlingStorageV2(root.resolve("material_handling.json")).deserialize(document.toString());
            }
        } catch (RuntimeException exception) {
            issues.add(issue(StartupRecoveryFailureCode.UNKNOWN_OUTCOME,
                    LegacySplitRecoveryParticipants.MATERIAL_HANDLING,
                    "Material Handling custody failed live owner validation"));
        }
    }

    private RecoveryMutationGate readRecoveryGate(
            Path root,
            Map<String, JsonObject> documents,
            long schedulerTick,
            List<StartupRecoveryIssue> issues
    ) {
        Optional<com.butchercraft.world.simulation.scheduler.SchedulerRecoveryState> schedulerRecovery =
                Optional.empty();
        if (documents.containsKey(SchedulerSchema.RECOVERY_FILE_NAME)) {
            try {
                var state = new SchedulerRecoveryStorage(root.resolve(SchedulerSchema.RECOVERY_FILE_NAME))
                        .deserialize(documents.get(SchedulerSchema.RECOVERY_FILE_NAME).toString());
                schedulerRecovery = Optional.of(state);
                if (state.admissionCursorTick() != schedulerTick) {
                    issues.add(issue(StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT,
                            CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                            "Scheduler recovery cursor differs from live Scheduler tick"));
                }
            } catch (RuntimeException exception) {
                issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                        CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                        "Scheduler recovery evidence failed validation"));
            }
        }
        JsonObject planning = documents.get(PlanningRecoveryState.FILE_NAME);
        if (planning == null) {
            if (schedulerRecovery.isPresent()) {
                issues.add(issue(StartupRecoveryFailureCode.AUTHORITY_BLOCK,
                        LegacySplitRecoveryParticipants.PLANNING,
                        "Scheduler recovery evidence exists without Planning authority evidence"));
            }
            return OPEN_GATE;
        }
        try {
            PlanningRecoveryState planningRecovery = new PlanningRecoveryStorage(
                    root.resolve(PlanningRecoveryState.FILE_NAME)).deserialize(planning.toString());
            if (schedulerRecovery.isEmpty()) {
                issues.add(issue(StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT,
                        CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
                        "Planning recovery authority exists without Scheduler discontinuity evidence"));
            } else {
                var schedulerState = schedulerRecovery.orElseThrow();
                if (!planningRecovery.recoveryIdentity().equals(schedulerState.recoveryIdentity().value())
                        || !planningRecovery.recoveryResultIdentity()
                        .equals(schedulerState.recoveryResultIdentity())
                        || !planningRecovery.recoveryResultContentDigest()
                        .equals(schedulerState.recoveryResultContentDigest())) {
                    issues.add(issue(StartupRecoveryFailureCode.AUTHORITY_BLOCK,
                            LegacySplitRecoveryParticipants.PLANNING,
                            "Scheduler and Planning recovery evidence bind different Recovery Results"));
                }
            }
            return planningRecovery.mutationGate();
        } catch (RuntimeException exception) {
            issues.add(issue(StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                    LegacySplitRecoveryParticipants.PLANNING,
                    "Planning recovery authority evidence failed validation"));
            return OPEN_GATE;
        }
    }

    private static void validateWorldElement(JsonElement element, WorldIdentityRootReference expected) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> validateWorldElement(value, expected));
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("world_identity_root")
                    && value.isJsonPrimitive() && !value.getAsString().equals(expected.identity())) {
                throw new IllegalArgumentException("World Identity differs");
            }
            if (entry.getKey().equals("world_identity_root_digest") && value.isJsonPrimitive()
                    && !value.getAsString().equals(expected.rootDigest())) {
                throw new IllegalArgumentException("World Identity digest differs");
            }
            if (entry.getKey().equals("world_identity") && value.isJsonObject()) {
                JsonObject identity = value.getAsJsonObject();
                if (identity.has("identity") && !identity.get("identity").getAsString().equals(expected.identity())) {
                    throw new IllegalArgumentException("World Identity differs");
                }
                if (identity.has("root_digest")
                        && !identity.get("root_digest").getAsString().equals(expected.rootDigest())) {
                    throw new IllegalArgumentException("World Identity digest differs");
                }
            }
            validateWorldElement(value, expected);
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static StartupRecoveryIssue issue(
            StartupRecoveryFailureCode code,
            CheckpointOwnerId owner,
            String detail
    ) {
        return new StartupRecoveryIssue(code, Optional.of(owner), detail);
    }

    private static CheckpointOwnerId ownerFor(String fileName) {
        if (fileName.startsWith("simulation_state")) return CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER;
        if (fileName.startsWith("simulation_scheduler")) return CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER;
        if (fileName.startsWith("execution_")) return LegacySplitRecoveryParticipants.EXECUTION;
        if (fileName.startsWith("workstation_") || fileName.startsWith("machine_operating")) {
            return LegacySplitRecoveryParticipants.WORKSTATION;
        }
        if (fileName.startsWith("material_handling")) return LegacySplitRecoveryParticipants.MATERIAL_HANDLING;
        if (fileName.startsWith("planning_")) return LegacySplitRecoveryParticipants.PLANNING;
        if (fileName.startsWith("production_")) return LegacySplitRecoveryParticipants.PRODUCTION;
        if (fileName.startsWith("transactions")) return LegacySplitRecoveryParticipants.TRANSACTIONS;
        if (fileName.startsWith("inventory")) return LegacySplitRecoveryParticipants.INVENTORY;
        if (fileName.startsWith("business_") || fileName.startsWith("world_time")) {
            return LegacySplitRecoveryParticipants.BUSINESS_RUNTIME;
        }
        if (fileName.startsWith("departments") || fileName.startsWith("employee_")
                || fileName.startsWith("workforce_")) return LegacySplitRecoveryParticipants.WORKFORCE;
        if (fileName.startsWith("goods")) return LegacySplitRecoveryParticipants.GOODS;
        if (fileName.startsWith("economic_actors")) return LegacySplitRecoveryParticipants.ECONOMIC_ACTORS;
        if (fileName.startsWith("orders")) return LegacySplitRecoveryParticipants.ORDERS;
        if (fileName.startsWith("contracts")) return LegacySplitRecoveryParticipants.CONTRACTS;
        if (fileName.startsWith("player_identities")) return LegacySplitRecoveryParticipants.PLAYER_IDENTITY;
        return LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY;
    }

    private static Map<String, Set<Integer>> supportedSchemas() {
        Map<String, Set<Integer>> values = new HashMap<>();
        for (String name : List.of(
                "simulation_state.json", "execution_operations.json", "execution_machine_runs.json",
                "machine_operating_states.json", "workstation_instances.json", "workstation_reservations.json",
                "planning_observations.json", "planning_needs.json", "planning_opportunities.json",
                "planning_candidates.json", "planning_approved_plans.json", "planning_runtime.json",
                "planning_cadence.json", "production_processes.json", "production_plans.json",
                "production_runs.json", "transactions.json", "inventory.json",
                "business_calendar_runtime.json", "business_runtime.json", "world_time.json",
                "departments.json", "employee_records.json", "employee_material_handling_assignments.json",
                "workforce_definitions.json", "goods.json", "economic_actors.json", "orders.json",
                "contracts.json", "player_identities.json", PlanningRecoveryState.FILE_NAME,
                SchedulerSchema.RECOVERY_FILE_NAME
        )) values.put(name, Set.of(1));
        values.put(SchedulerSchema.FILE_NAME, Set.of(1, 2));
        values.put("workstation_endpoint_journal.json", Set.of(1, 2));
        values.put("material_handling.json", Set.of(1, 2));
        return Map.copyOf(values);
    }
}
