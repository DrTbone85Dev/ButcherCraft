package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationAdapter;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.butchercraft.world.checkpoint.RecoveryMutationGate;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.execution.persistence.ExecutionStorage;
import com.butchercraft.world.execution.persistence.MachineRunStorage;
import com.butchercraft.world.materialhandling.MaterialHandlingSchema;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;
import com.butchercraft.world.planning.PlanningRecoveryState;
import com.butchercraft.world.planning.PlanningRecoveryStorage;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.checkpoint.SimulationClockNativeRestorationAdapter;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerNativeRestorationAdapter;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class NativeOwnerRestorationAdapters {
    private static final Path UNUSED = Path.of("native_restoration_validation.json");
    private static final List<String> WORKSTATION_NATIVE_FILES = List.of(
            "machine_operating_states.json",
            "workstation_endpoint_journal.json",
            "workstation_instances.json",
            "workstation_reservations.json"
    );
    private static final Set<String> WORKSTATION_VIRTUAL_FILES = Set.of("workstation_projections.json");

    private NativeOwnerRestorationAdapters() {
    }

    public static List<OwnerNativeRestorationAdapter> forServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<OwnerNativeRestorationAdapter> adapters = new ArrayList<>();
        adapters.add(new SimulationClockNativeRestorationAdapter(
                SimulationClockService.INSTANCE.configuration()));
        adapters.add(new SimulationSchedulerNativeRestorationAdapter(
                SimulationSchedulerService.INSTANCE.configuredHandlerRegistry()));
        adapters.add(fileAdapter(
                LegacySplitRecoveryParticipants.EXECUTION,
                Set.of(1),
                List.of("execution_operations.json", "execution_machine_runs.json"),
                Set.of(),
                true,
                (context, state) -> policyBExecution(server, context, state),
                (context, state) -> validateExecution(server, state),
                NativeOwnerRestorationAdapters::executionMetadata
        ));
        adapters.add(new FileBundleNativeRestorationAdapter(
                LegacySplitRecoveryParticipants.WORKSTATION,
                Set.of(1, 2, 3),
                WORKSTATION_NATIVE_FILES,
                WORKSTATION_VIRTUAL_FILES,
                true,
                NativeOwnerRestorationAdapters::policyBWorkstation,
                NativeOwnerRestorationAdapters::validateWorkstation,
                (context, state) -> FileBundleNativeRestorationAdapter.RestorationMetadata.open(),
                (context, plan) -> WorkstationCheckpointProjectionService.restore(server, context, plan),
                NativeOwnerRestorationAdapters::workstationProjectionFiles
        ));
        adapters.add(fileAdapter(
                LegacySplitRecoveryParticipants.MATERIAL_HANDLING,
                Set.of(1, 2),
                List.of("material_handling.json"), Set.of(), true,
                NativeOwnerRestorationAdapters::identity,
                NativeOwnerRestorationAdapters::validateMaterialHandling
        ));
        adapters.add(fileAdapter(
                LegacySplitRecoveryParticipants.PLANNING,
                Set.of(1),
                List.of(
                        "planning_observations.json",
                        "planning_needs.json",
                        "planning_opportunities.json",
                        "planning_candidates.json",
                        "planning_approved_plans.json",
                        "planning_runtime.json",
                        "planning_cadence.json"
                ),
                Set.of(), true,
                NativeOwnerRestorationAdapters::planningRecovery,
                NativeOwnerRestorationAdapters::validatePlanning,
                NativeOwnerRestorationAdapters::planningMetadata
        ));
        adapters.add(simple(LegacySplitRecoveryParticipants.PRODUCTION,
                List.of("production_processes.json", "production_plans.json", "production_runs.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.TRANSACTIONS, List.of("transactions.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.INVENTORY, List.of("inventory.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.BUSINESS_RUNTIME,
                List.of("business_calendar_runtime.json", "business_runtime.json", "world_time.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.WORKFORCE,
                List.of("departments.json", "employee_records.json",
                        "employee_material_handling_assignments.json", "workforce_definitions.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.GOODS, List.of("goods.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.ECONOMIC_ACTORS, List.of("economic_actors.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.ORDERS, List.of("orders.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.CONTRACTS, List.of("contracts.json")));
        adapters.add(simple(LegacySplitRecoveryParticipants.PLAYER_IDENTITY, List.of("player_identities.json")));
        adapters.add(fileAdapter(
                LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY,
                Set.of(1), List.of(), Set.of("checkpoint_recovery_evidence_index.json"), false,
                NativeOwnerRestorationAdapters::identity,
                (context, state) -> {
                    if (!state.files().isEmpty()) {
                        throw new IllegalArgumentException("Checkpoint Recovery snapshot is evidence, not native owner state");
                    }
                }
        ));
        List<OwnerNativeRestorationAdapter> ordered = adapters.stream()
                .sorted(java.util.Comparator.comparing(OwnerNativeRestorationAdapter::ownerId)).toList();
        if (!ordered.stream().map(OwnerNativeRestorationAdapter::ownerId).toList()
                .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS)) {
            throw new IllegalStateException("Native restoration adapter registry must contain the exact 17 owners");
        }
        return ordered;
    }

    private static OwnerNativeRestorationAdapter simple(
            com.butchercraft.world.checkpoint.CheckpointOwnerId owner,
            List<String> legacyFiles
    ) {
        return fileAdapter(owner, Set.of(1), legacyFiles, Set.of(), true,
                NativeOwnerRestorationAdapters::identity, (context, state) -> { });
    }

    private static OwnerNativeRestorationAdapter fileAdapter(
            com.butchercraft.world.checkpoint.CheckpointOwnerId owner,
            Set<Integer> schemas,
            List<String> legacyFiles,
            Set<String> virtualFiles,
            boolean legacySourceRequired,
            FileBundleNativeRestorationAdapter.NativeTransform transform,
            FileBundleNativeRestorationAdapter.NativeValidation validation
    ) {
        return fileAdapter(owner, schemas, legacyFiles, virtualFiles, legacySourceRequired,
                transform, validation, (context, state) -> FileBundleNativeRestorationAdapter.RestorationMetadata.open());
    }

    private static OwnerNativeRestorationAdapter fileAdapter(
            com.butchercraft.world.checkpoint.CheckpointOwnerId owner,
            Set<Integer> schemas,
            List<String> legacyFiles,
            Set<String> virtualFiles,
            boolean legacySourceRequired,
            FileBundleNativeRestorationAdapter.NativeTransform transform,
            FileBundleNativeRestorationAdapter.NativeValidation validation,
            FileBundleNativeRestorationAdapter.RestorationMetadataExtractor metadataExtractor
    ) {
        return new FileBundleNativeRestorationAdapter(
                owner, schemas, legacyFiles, virtualFiles, legacySourceRequired,
                transform, validation, metadataExtractor, (context, plan) -> { });
    }

    private static FileBundleNativeRestorationAdapter.PreparedNativeState identity(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        return state;
    }

    private static FileBundleNativeRestorationAdapter.PreparedNativeState policyBExecution(
            MinecraftServer server,
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        Map<String, byte[]> files = mutable(state.files());
        MachineRunStorage storage = new MachineRunStorage(UNUSED);
        MachineRunRegistry registry = storage.deserialize(text(files, "execution_machine_runs.json"));
        for (var run : registry.runs()) {
            if (run.lifecycle() != MachineRunLifecycle.AUTHORIZED) continue;
            var mutation = registry.suspendForRestart(
                    run.runIdentity(), context.generationManifest().authoritativeSimulationTick());
            if (!mutation.accepted()) {
                throw new IllegalArgumentException("Execution rejected Policy B restoration: " + mutation.detail());
            }
            registry = mutation.registry();
        }
        files.put("execution_machine_runs.json", storage.serialize(registry).getBytes(StandardCharsets.UTF_8));
        return new FileBundleNativeRestorationAdapter.PreparedNativeState(
                state.ownerSchemaVersion(), files, state.workstationProjection());
    }

    private static void validateExecution(
            MinecraftServer server,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        new ExecutionStorage(
                UNUSED,
                ExecutionService.INSTANCE.configuredHandlerRegistry(server),
                ExecutionService.INSTANCE.configuration()
        ).deserialize(text(state.files(), "execution_operations.json"));
        MachineRunRegistry runs = new MachineRunStorage(UNUSED)
                .deserialize(text(state.files(), "execution_machine_runs.json"));
        if (runs.runs().stream().anyMatch(run -> run.lifecycle() == MachineRunLifecycle.AUTHORIZED)) {
            throw new IllegalArgumentException("Restored active Machine Run did not enter Policy B");
        }
    }

    private static FileBundleNativeRestorationAdapter.RestorationMetadata executionMetadata(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        List<String> policyB = new MachineRunStorage(UNUSED)
                .deserialize(text(state.files(), "execution_machine_runs.json"))
                .runs().stream()
                .filter(run -> run.lifecycle() == MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED)
                .map(run -> run.runIdentity().value())
                .sorted().toList();
        return new FileBundleNativeRestorationAdapter.RestorationMetadata(
                new RecoveryMutationGate(1, false, List.of(), List.of()), policyB);
    }

    private static FileBundleNativeRestorationAdapter.PreparedNativeState policyBWorkstation(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        Map<String, byte[]> files = mutable(state.files());
        MachineOperatingStorage storage = new MachineOperatingStorage(UNUSED);
        MachineOperatingRegistry registry = storage.deserialize(text(files, "machine_operating_states.json"));
        for (var record : registry.records()) {
            if (!record.state().powered()) continue;
            var mutation = registry.suspendForRestart(
                    record.currentRunIdentity().orElseThrow(),
                    context.generationManifest().authoritativeSimulationTick());
            if (!mutation.accepted()) {
                throw new IllegalArgumentException("Workstation rejected Policy B restoration: " + mutation.detail());
            }
            registry = mutation.registry();
        }
        files.put("machine_operating_states.json", storage.serialize(registry).getBytes(StandardCharsets.UTF_8));
        return new FileBundleNativeRestorationAdapter.PreparedNativeState(
                state.ownerSchemaVersion(), files, state.workstationProjection());
    }

    private static void validateWorkstation(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        MachineOperatingRegistry operating = new MachineOperatingStorage(UNUSED)
                .deserialize(text(state.files(), "machine_operating_states.json"));
        if (operating.records().stream().anyMatch(record -> record.state().powered())) {
            throw new IllegalArgumentException("Restored powered Workstation did not enter Policy B");
        }
        new WorkstationInstanceStorage(UNUSED)
                .deserialize(text(state.files(), "workstation_instances.json"));
        new WorkstationReservationStorage(UNUSED)
                .deserialize(text(state.files(), "workstation_reservations.json"));
        String journal = text(state.files(), "workstation_endpoint_journal.json");
        int schema = JsonParser.parseString(journal).getAsJsonObject().get("schema_version").getAsInt();
        if (schema == WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION) {
            new WorkstationEndpointJournalStorage(UNUSED).deserialize(journal);
        } else if (schema == WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION) {
            new WorkstationEndpointJournalV2Storage(UNUSED).deserialize(journal);
        } else {
            throw new IllegalArgumentException("Unsupported Workstation endpoint journal schema: " + schema);
        }
    }

    private static List<OwnerNativeRestorationPlan.NativeFile> workstationProjectionFiles(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        byte[] projection = state.workstationProjection().orElseThrow(() ->
                new IllegalArgumentException("Complete-restorable Workstation snapshot omits durable projections"));
        WorkstationInstanceRegistry instances = new WorkstationInstanceStorage(UNUSED)
                .deserialize(text(state.files(), "workstation_instances.json"));
        return WorkstationCheckpointProjectionService.prepareRestorationProjectionFiles(
                context, instances, projection);
    }

    static Set<String> workstationCheckpointFileNames() {
        TreeSet<String> files = new TreeSet<>(WORKSTATION_NATIVE_FILES);
        files.addAll(WORKSTATION_VIRTUAL_FILES);
        return Set.copyOf(files);
    }

    private static void validateMaterialHandling(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        String json = text(state.files(), "material_handling.json");
        int schema = JsonParser.parseString(json).getAsJsonObject().get("schema_version").getAsInt();
        if (schema == MaterialHandlingSchema.LEGACY_SCHEMA_VERSION) {
            new MaterialHandlingStorage(UNUSED).deserialize(json);
        } else if (schema == MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
            new MaterialHandlingStorageV2(UNUSED).deserialize(json);
        } else {
            throw new IllegalArgumentException("Unsupported Material Handling schema: " + schema);
        }
    }

    private static FileBundleNativeRestorationAdapter.PreparedNativeState planningRecovery(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        if (context.legacyRecoveryResult().isEmpty()) return state;
        Map<String, byte[]> files = mutable(state.files());
        PlanningRecoveryState recoveryState = PlanningRecoveryState.fromResult(
                context.legacyRecoveryResult().orElseThrow());
        files.put(PlanningRecoveryState.FILE_NAME, new PlanningRecoveryStorage(UNUSED)
                .serialize(recoveryState).getBytes(StandardCharsets.UTF_8));
        return new FileBundleNativeRestorationAdapter.PreparedNativeState(
                state.ownerSchemaVersion(), files, state.workstationProjection());
    }

    private static void validatePlanning(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        byte[] recovery = state.files().get(PlanningRecoveryState.FILE_NAME);
        if (context.legacyRecoveryResult().isPresent()) {
            if (recovery == null) throw new IllegalArgumentException("Planning recovery authority state is absent");
            PlanningRecoveryState parsed = new PlanningRecoveryStorage(UNUSED)
                    .deserialize(new String(recovery, StandardCharsets.UTF_8));
            if (!parsed.mutationGate().equals(context.legacyRecoveryResult().orElseThrow().mutationGate())) {
                throw new IllegalArgumentException("Planning recovery mutation gate differs from Recovery Result");
            }
        }
    }

    private static FileBundleNativeRestorationAdapter.RestorationMetadata planningMetadata(
            OwnerNativeRestorationContext context,
            FileBundleNativeRestorationAdapter.PreparedNativeState state
    ) {
        byte[] recovery = state.files().get(PlanningRecoveryState.FILE_NAME);
        RecoveryMutationGate gate = recovery == null
                ? new RecoveryMutationGate(1, false, List.of(), List.of())
                : new PlanningRecoveryStorage(UNUSED)
                .deserialize(new String(recovery, StandardCharsets.UTF_8)).mutationGate();
        return new FileBundleNativeRestorationAdapter.RestorationMetadata(gate, List.of());
    }

    private static Map<String, byte[]> mutable(Map<String, byte[]> source) {
        Map<String, byte[]> values = new LinkedHashMap<>();
        source.forEach((name, bytes) -> values.put(name, bytes.clone()));
        return values;
    }

    private static String text(Map<String, byte[]> files, String name) {
        byte[] bytes = files.get(name);
        if (bytes == null) throw new IllegalArgumentException("Owner restoration omits native file: " + name);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
