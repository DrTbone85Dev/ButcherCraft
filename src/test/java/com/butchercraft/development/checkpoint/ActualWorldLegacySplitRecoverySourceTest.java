package com.butchercraft.development.checkpoint;

import com.butchercraft.workstation.operation.checkpoint.WorkstationLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.ExactUnchangedLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryDryRunPreview;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreviewRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationReport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationPhase;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationService;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationStorage;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryStatusSnapshot;
import com.butchercraft.world.checkpoint.LegacyTempArtifactFinding;
import com.butchercraft.world.checkpoint.RecoveryOperatorAuthority;
import com.butchercraft.world.checkpoint.RecoveryOperatorAuthorization;
import com.butchercraft.world.checkpoint.RecoveryOperatorEvidence;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;
import com.butchercraft.world.checkpoint.SplitSnapshotRecoveryAnalyzer;
import com.butchercraft.world.checkpoint.SplitSnapshotRecoveryPlan;
import com.butchercraft.world.execution.checkpoint.ExecutionLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.planning.checkpoint.PlanningLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.simulation.checkpoint.SimulationClockLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.checkpoint.SchedulerLegacyRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.CheckpointRecoveryLegacyRecoveryOwnerPreparer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualWorldLegacySplitRecoverySourceTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String WORLD_ID = "butchercraft:world_identity/sanitized_r2a";
    private static final String WORLD_DIGEST = digest(900);
    private static final String RUN_ID = id("machine_run", 500);
    private static final String WORKSTATION_ID = id("workstation_instance", 501);
    private static final long CLOCK_TICK = 39_872L;
    private static final long SCHEDULER_TICK = 39_084L;
    private static final String ACTUAL_RUN_ID =
            "butchercraft:machine_run/v1/92d1b5a4f85f9e46907c4ba80bc4becfe0d5cd76600d4056156063d08bb32b3c";
    private static final String ACTUAL_WORKSTATION_ID =
            "butchercraft:workstation_instance/v1/243a13bf683621dc6d48a20ed39fdc81f96bc9fd776ba316feb236bf7b771996";
    private static final String ACTUAL_CHILD_ID =
            "butchercraft:machine_run_child/v1/e5e6e6d41fd78bed2642c7325276940d3ba231008fc58ad55e8b66c2b408ce73";
    private static final String ACTUAL_OPERATION_ID =
            "butchercraft:execution_operation/v1/dc08ca2fc1b1e083f8fe0f21a5842c8a092e389a699cec6618cd4e635721c3f4";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void sanitizedActualFormatsAreReadOnlyPathIndependentAndEnumerationIndependent() throws IOException {
        Path first = fixture(temporaryDirectory.resolve("first-copy"), false);
        Path second = fixture(temporaryDirectory.resolve("renamed-copy"), true);
        String firstBefore = treeDigest(first);
        String secondBefore = treeDigest(second);

        ActualWorldLegacySplitRecoverySource firstSource = new ActualWorldLegacySplitRecoverySource(first);
        ActualWorldLegacySplitRecoverySource secondSource = new ActualWorldLegacySplitRecoverySource(second);
        SplitSnapshotRecoveryPlan firstPlan = new SplitSnapshotRecoveryAnalyzer().analyze(firstSource.reloadReadOnly());
        SplitSnapshotRecoveryPlan secondPlan = new SplitSnapshotRecoveryAnalyzer().analyze(secondSource.reloadReadOnly());

        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS,
                firstPlan.eligibility(), firstPlan.issues().toString());
        assertEquals(CLOCK_TICK, firstPlan.authoritativeClockTick());
        assertEquals(SCHEDULER_TICK, firstPlan.schedulerLastNormallyFinalizedTick());
        assertEquals(9, firstPlan.historicalAcknowledgements().size());
        assertEquals(0L, firstPlan.ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility()
                        == OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE)
                .count());
        assertEquals(1, firstPlan.preservedAuthorizedWork().size());
        assertEquals(10L, firstPlan.preservedAuthorizedWork().getFirst().childSequence());
        assertEquals(RUN_ID, firstPlan.preservedAuthorizedWork().getFirst().machineRunIdentity());
        assertEquals(WORKSTATION_ID,
                firstPlan.preservedAuthorizedWork().getFirst().workstationInstanceIdentity());
        assertEquals(1, firstPlan.planningAuthorityBlocks().size());
        assertTrue(firstPlan.planningAuthorityBlocks().getFirst().wholeWorldMutationBlocked());
        assertTrue(firstPlan.materialHandlingReferences().isEmpty());
        assertEquals(16, firstPlan.sourceSnapshots().size());
        assertEquals(WORLD_ID, firstPlan.worldIdentityRoot().identity());
        assertEquals(firstPlan.recoveryIdentity(), secondPlan.recoveryIdentity());
        assertEquals(firstPlan.analysisDigest(), secondPlan.analysisDigest());
        assertEquals(firstPlan.sourceSnapshots(), secondPlan.sourceSnapshots());

        Files.setLastModifiedTime(second.resolve("butchercraft/execution_operations.json"),
                FileTime.fromMillis(1_000L));
        SplitSnapshotRecoveryPlan metadataChanged = new SplitSnapshotRecoveryAnalyzer()
                .analyze(secondSource.reloadReadOnly());
        assertEquals(firstPlan.recoveryIdentity(), metadataChanged.recoveryIdentity());
        assertEquals(firstPlan.analysisDigest(), metadataChanged.analysisDigest());
        assertEquals(firstBefore, treeDigest(first));
        assertEquals(secondBefore, treeDigest(second));
    }

    @Test
    void malformedAndUnsupportedOwnerPersistenceFailVisibly() throws IOException {
        Path malformed = fixture(temporaryDirectory.resolve("malformed"), false);
        Files.writeString(malformed.resolve("butchercraft/simulation_state.json"), "not-json\n");
        assertThrows(IllegalArgumentException.class,
                () -> new ActualWorldLegacySplitRecoverySource(malformed).reloadReadOnly());

        Path unsupported = fixture(temporaryDirectory.resolve("unsupported"), false);
        JsonObject scheduler = read(unsupported, "simulation_scheduler.json");
        scheduler.addProperty("schema_version", 99);
        write(unsupported, "simulation_scheduler.json", scheduler);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ActualWorldLegacySplitRecoverySource(unsupported).reloadReadOnly());
        assertTrue(exception.getMessage().contains("Unsupported actual-world owner schema"));
    }

    @Test
    void configuredDisposableActualWorldPublishesAndReloadsOfflineWithoutNativeMutation() throws IOException {
        String configuredRoot = System.getenv("BUTCHERCRAFT_R2A_WORLD_ROOT");
        Assumptions.assumeTrue(configuredRoot != null && !configuredRoot.isBlank(),
                "Set BUTCHERCRAFT_R2A_WORLD_ROOT for the explicit actual-world offline validation");
        Path world = Path.of(configuredRoot).toAbsolutePath().normalize();
        Path checkpointRoot = world.resolve("butchercraft/checkpoints");
        List<Path> protectedRoots = protectedRoots();
        ActualWorldLegacySplitRecoverySource source = new ActualWorldLegacySplitRecoverySource(world);
        LegacySplitRecoveryAdminTool tool = new LegacySplitRecoveryAdminTool();
        String dryRunBefore = treeDigest(world);

        LegacySplitRecoveryDryRunPreview preview = tool.analyze(new LegacySplitRecoveryPreviewRequest(
                source, world, checkpointRoot, protectedRoots
        ));

        assertTrue(preview.publicationEligible(), preview.plan().issues().toString());
        assertEquals(CLOCK_TICK, preview.plan().authoritativeClockTick());
        assertEquals(SCHEDULER_TICK, preview.plan().schedulerLastNormallyFinalizedTick());
        assertEquals(9, preview.plan().historicalAcknowledgements().size());
        assertEquals(0L, preview.plan().ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility()
                        == OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE)
                .count());
        assertEquals(16, preview.plan().sourceSnapshots().size());
        assertEquals(SplitSnapshotRecoveryPlan.Eligibility.RECOVERABLE_WITH_AUTHORITY_BLOCKS,
                preview.plan().eligibility());
        assertEquals(1, preview.plan().preservedAuthorizedWork().size());
        var preserved = preview.plan().preservedAuthorizedWork().getFirst();
        assertEquals(10L, preserved.childSequence());
        assertEquals(ACTUAL_RUN_ID, preserved.machineRunIdentity());
        assertEquals(1L, preserved.machineRunGeneration());
        assertEquals(ACTUAL_WORKSTATION_ID, preserved.workstationInstanceIdentity());
        assertEquals(6L, preserved.workstationInstanceGeneration());
        assertEquals(ACTUAL_CHILD_ID, preserved.childIdentity());
        assertEquals(ACTUAL_OPERATION_ID, preserved.executionOperationIdentity());
        assertEquals(39_085L, preview.plan().recoveryDiscontinuity().orElseThrow().inclusiveMissingStartTick());
        assertEquals(39_872L, preview.plan().recoveryDiscontinuity().orElseThrow().inclusiveMissingEndTick());
        assertEquals(39_873L, preview.plan().nextNormalSchedulerAdmissionTick().orElseThrow());
        assertEquals(1, preview.plan().authorityBlocks().size());
        assertTrue(preview.mutationGate().wholeWorldConsequentialMutationBlocked());
        assertFalse(preview.plan().authorityBlocks().getFirst().dependencyClosureProven());
        assertEquals(LegacyTempArtifactFinding.Classification.IDENTICAL_STALE_DEBRIS,
                preview.plan().legacyTempFindings().getFirst().classification());
        assertTrue(preview.plan().materialHandlingReferences().isEmpty());

        SplitSnapshotRecoveryPlan repeated = new SplitSnapshotRecoveryAnalyzer().analyze(source.reloadReadOnly());
        assertEquals(preview.plan().recoveryIdentity(), repeated.recoveryIdentity());
        assertEquals(preview.plan().analysisDigest(), repeated.analysisDigest());
        Map<String, byte[]> actualNative = nativeFiles(world);
        Path relocated = writeNativeCopy(temporaryDirectory.resolve("relocated-actual-world"), actualNative);
        Files.setLastModifiedTime(relocated.resolve("butchercraft/execution_operations.json"),
                FileTime.fromMillis(1_000L));
        SplitSnapshotRecoveryPlan relocatedPlan = new SplitSnapshotRecoveryAnalyzer().analyze(
                new ActualWorldLegacySplitRecoverySource(relocated).reloadReadOnly()
        );
        assertEquals(preview.plan().recoveryIdentity(), relocatedPlan.recoveryIdentity());
        assertEquals(preview.plan().analysisDigest(), relocatedPlan.analysisDigest());
        assertEquals(dryRunBefore, treeDigest(world), "Actual dry run mutated the disposable copy");

        RecoveryOperatorEvidence operator = actualOperatorEvidence(preview.plan());
        RecoveryOperatorAuthorization authorization = tool.authorizeExact(
                preview,
                RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                operator,
                Optional.of("2026-08-21T19:45:00-05:00")
        );
        Map<String, byte[]> nativeBefore = nativeFiles(world);
        LegacySplitRecoveryPublicationRequest request = new LegacySplitRecoveryPublicationRequest(
                source,
                authorization,
                world,
                checkpointRoot,
                protectedRoots,
                preparers(),
                Optional.of("2026-08-21T19:45:00-05:00")
        );
        LegacySplitRecoveryPublicationReport first = tool.publishExact(request);
        assertTrue(first.successful(), first.failures().toString());
        LegacySplitRecoveryResult result = first.recoveryResult().orElseThrow();
        assertEquals(17, result.ownerSnapshots().size());
        assertEquals(authorization.authorizationIdentity(), result.authorizationIdentity());
        assertEquals(preview.plan().analysisDigest(), result.analysisDigest());
        assertEquals(preview.plan().recoveryIdentity(), result.recoveryIdentity());
        assertEquals(9, result.publishedAcknowledgements().size());
        assertEquals(1, result.preservedAuthorizedWork().size());
        Map<String, byte[]> nativeAfter = nativeFiles(world);
        assertEquals(nativeBefore.keySet(), nativeAfter.keySet());
        for (Map.Entry<String, byte[]> entry : nativeBefore.entrySet()) {
            assertArrayEquals(entry.getValue(), nativeAfter.get(entry.getKey()), entry.getKey());
        }

        CheckpointRecoveredGeneration generation = new CheckpointFilesystemStore(checkpointRoot)
                .loadSelectedGeneration(new CheckpointFilesystemRecoveryRequest(
                        LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                        preview.plan().worldIdentityRoot(),
                        preview.plan().platformDeterminismManifest()
                )).recoveredGeneration().orElseThrow();
        assertEquals(result.recoveryGenerationId(), generation.manifest().generationId());
        assertEquals(17, generation.ownerSnapshots().size());
        Map<CheckpointOwnerId, Map<String, String>> ownerFields = ownerFields(generation);
        assertEquals("39872", ownerFields.get(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER)
                .get("authoritative_clock_tick"));
        assertEquals("9", ownerFields.get(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER)
                .get("acknowledgement_count"));
        assertEquals("0", ownerFields.get(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER)
                .get("handler_invocation_count"));
        assertEquals("39873", ownerFields.get(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER)
                .get("next_normal_admission_tick"));
        assertEquals("AUTHORIZED", ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                .get("preserved_child.0.operation_state"));
        assertEquals("9", ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                .get("existing_terminal_result_count"));
        assertEquals("0", ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                .get("new_operation_count"));
        assertEquals(ACTUAL_OPERATION_ID, ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                .get("preserved_child.0.execution_operation_identity"));
        assertEquals(ACTUAL_CHILD_ID, ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                .get("preserved_child.0.child_identity"));
        assertEquals("SUSPENDED_RESTART_REQUIRED",
                ownerFields.get(LegacySplitRecoveryParticipants.EXECUTION)
                        .get("preserved_child.0.machine_run_lifecycle"));
        assertEquals("RESTART_REQUIRED", ownerFields.get(LegacySplitRecoveryParticipants.WORKSTATION)
                .get("machine.0.operating_state"));
        assertEquals(ACTUAL_WORKSTATION_ID, ownerFields.get(LegacySplitRecoveryParticipants.WORKSTATION)
                .get("machine.0.instance_identity"));
        assertEquals("0", ownerFields.get(LegacySplitRecoveryParticipants.WORKSTATION)
                .get("inventory_effect_count"));
        assertEquals("true", ownerFields.get(LegacySplitRecoveryParticipants.PLANNING)
                .get("whole_world_mutation_blocked"));
        assertEquals("0", ownerFields.get(LegacySplitRecoveryParticipants.PLANNING)
                .get("planning_replay_count"));

        LegacySplitRecoveryResult reloaded = new LegacySplitRecoveryPublicationStorage(checkpointRoot)
                .loadResult(result.recoveryIdentity()).orElseThrow();
        assertEquals(result, reloaded);
        LegacySplitRecoveryStatusSnapshot freshStatus = new LegacySplitRecoveryAdminTool().status(
                new ActualWorldLegacySplitRecoverySource(world), checkpointRoot
        );
        assertEquals(LegacySplitRecoveryStatusSnapshot.State.RECOVERY_COMMITTED, freshStatus.state());
        assertEquals(result.recoveryGenerationId(), freshStatus.committedRecoveryGeneration().orElseThrow());
        assertFalse(freshStatus.worldFullyMutationUnblocked());

        long artifactCount = regularFileCount(checkpointRoot);
        LegacySplitRecoveryPublicationReport duplicate = new LegacySplitRecoveryAdminTool().publishExact(request);
        assertEquals(LegacySplitRecoveryPublicationReport.Outcome.EXISTING_RESULT_OBSERVED, duplicate.outcome());
        assertEquals(result.resultIdentity(), duplicate.recoveryResult().orElseThrow().resultIdentity());
        assertEquals(artifactCount, regularFileCount(checkpointRoot));
        assertEquals(nativeBefore.keySet(), nativeFiles(world).keySet());

        System.out.println("R2A_RECOVERY_IDENTITY=" + result.recoveryIdentity().value());
        System.out.println("R2A_ANALYSIS_DIGEST=" + result.analysisDigest());
        System.out.println("R2A_AUTHORIZATION_IDENTITY=" + authorization.authorizationIdentity());
        System.out.println("R2A_AUTHORIZATION_DIGEST=" + authorization.contentDigest());
        System.out.println("R2A_GENERATION_ID=" + result.recoveryGenerationId().canonicalValue());
        System.out.println("R2A_RESULT_IDENTITY=" + result.resultIdentity());
        System.out.println("R2A_PARTICIPANTS=" + result.ownerSnapshots().size());
        preview.plan().sourceSnapshots().forEach(snapshot -> System.out.println(
                "R2A_SOURCE_SNAPSHOT=" + snapshot.ownerId().value() + "|"
                        + snapshot.snapshotIdentity() + "|" + snapshot.contentDigest()
        ));
        result.publishedAcknowledgements().forEach(acknowledgement -> System.out.println(
                "R2A_ACKNOWLEDGEMENT=" + acknowledgement.identity() + "|"
                        + acknowledgement.executionOperationIdentity() + "|"
                        + acknowledgement.terminalOutcome()
        ));
        result.authorityBlocks().forEach(block -> System.out.println(
                "R2A_AUTHORITY_BLOCK=" + block.blockIdentity() + "|" + block.ownerId().value()
                        + "|" + block.scope() + "|closure=" + block.dependencyClosureProven()
        ));
    }

    @Test
    void configuredActualEvidenceShapeSurvivesEveryOfflinePublicationFaultBoundary() throws IOException {
        String configuredRoot = System.getenv("BUTCHERCRAFT_R2A_WORLD_ROOT");
        Assumptions.assumeTrue(configuredRoot != null && !configuredRoot.isBlank(),
                "Set BUTCHERCRAFT_R2A_WORLD_ROOT for actual-world fault-boundary validation");
        Map<String, byte[]> sourceFiles = nativeFiles(Path.of(configuredRoot).toAbsolutePath().normalize());
        for (LegacySplitRecoveryPublicationPhase phase
                : EnumSet.allOf(LegacySplitRecoveryPublicationPhase.class)) {
            Path world = writeNativeCopy(
                    temporaryDirectory.resolve("fault-" + phase.name().toLowerCase(java.util.Locale.ROOT)),
                    sourceFiles
            );
            Path checkpointRoot = world.resolve("butchercraft/checkpoints");
            ActualWorldLegacySplitRecoverySource source = new ActualWorldLegacySplitRecoverySource(world);
            LegacySplitRecoveryPublicationService normal = new LegacySplitRecoveryPublicationService();
            LegacySplitRecoveryDryRunPreview preview = normal.preview(new LegacySplitRecoveryPreviewRequest(
                    source, world, checkpointRoot, List.of()
            ));
            RecoveryOperatorAuthorization authorization = RecoveryOperatorAuthorization.authorize(
                    preview.plan(),
                    RecoveryOperatorAuthorization.Disposition.AUTHORIZE_RECOVERY_WITH_AUTHORITY_BLOCKS,
                    actualOperatorEvidence(preview.plan()),
                    Optional.empty()
            );
            LegacySplitRecoveryPublicationRequest request = new LegacySplitRecoveryPublicationRequest(
                    source, authorization, world, checkpointRoot, List.of(), preparers(), Optional.empty()
            );
            AtomicBoolean injected = new AtomicBoolean();
            LegacySplitRecoveryPublicationReport interrupted = new LegacySplitRecoveryPublicationService(
                    reached -> {
                        if (reached == phase && injected.compareAndSet(false, true)) {
                            throw new IOException("simulated actual-world " + phase);
                        }
                    }
            ).publish(request);
            assertFalse(interrupted.successful(), phase.name());

            LegacySplitRecoveryPublicationReport recovered = normal.publish(request);
            assertTrue(recovered.successful(), phase.name() + " " + recovered.failures());
            assertEquals(9, recovered.recoveryResult().orElseThrow().publishedAcknowledgements().size());
            assertEquals(1L, Files.list(checkpointRoot.resolve("generations")).count(), phase.name());
            Map<String, byte[]> after = nativeFiles(world);
            assertEquals(sourceFiles.keySet(), after.keySet(), phase.name());
            for (Map.Entry<String, byte[]> entry : sourceFiles.entrySet()) {
                assertArrayEquals(entry.getValue(), after.get(entry.getKey()), phase.name() + " " + entry.getKey());
            }
        }
    }

    private Path fixture(Path root, boolean reverseCreationOrder) throws IOException {
        Files.createDirectories(root.resolve("butchercraft"));
        Map<String, JsonObject> files = fixtureDocuments();
        List<Map.Entry<String, JsonObject>> entries = new ArrayList<>(files.entrySet());
        if (reverseCreationOrder) java.util.Collections.reverse(entries);
        for (Map.Entry<String, JsonObject> entry : entries) write(root, entry.getKey(), entry.getValue());
        Files.copy(root.resolve("butchercraft/simulation_state.json"),
                root.resolve("butchercraft/simulation_state.json.tmp"));
        return root;
    }

    private Map<String, JsonObject> fixtureDocuments() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        JsonObject world = world();
        files.put("simulation_state.json", object(
                "schema_version", 1,
                "simulation_tick", CLOCK_TICK,
                "calendar", object("day", 2),
                "pending_scheduled_events", array()
        ));
        files.put("simulation_scheduler.json", scheduler());
        files.put("execution_operations.json", executionOperations());
        files.put("execution_machine_runs.json", machineRuns());
        files.put("machine_operating_states.json", object(
                "schema_version", 1,
                "owner_revision", 375,
                "world_identity", world,
                "configuration_identity", "butchercraft:machine_operating_configuration/schema_1",
                "records", array(operatingRecord())
        ));
        files.put("workstation_instances.json", object(
                "schema_version", 1,
                "owner_revision", 21,
                "world_identity", world,
                "next_instance_generation", 7,
                "allocation_configuration_identity", "butchercraft:workstation_instance_configuration/v1/standard",
                "instances", array(instance())
        ));
        files.put("workstation_endpoint_journal.json", object(
                "schema_version", 2,
                "owner_revision", 20,
                "next_journal_sequence", 5,
                "world_identity", world,
                "endpoint_configuration_identity", "butchercraft:workstation_endpoint_configuration/v2/stack_aware",
                "immutable_legacy_schema_1_journal", GSON.toJson(object(
                        "schema_version", 1,
                        "owner_revision", 20,
                        "next_journal_sequence", 5,
                        "endpoint_effects", array()
                )) + "\n",
                "endpoint_effects", array()
        ));
        files.put("workstation_reservations.json", simple(1, "reservations", array()));
        files.put("material_handling.json", object(
                "schema_version", 2,
                "owner_revision", 22,
                "next_transfer_sequence", 3,
                "world_identity", world,
                "configuration_identity", "butchercraft:material_handling_configuration/v2/one_item_approved_routes",
                "immutable_legacy_schema_1_runtime", GSON.toJson(object(
                        "schema_version", 1,
                        "owner_revision", 22,
                        "next_transfer_sequence", 3,
                        "world_identity", world,
                        "configuration_identity", "butchercraft:material_handling_configuration/v1/standard",
                        "transfers", array()
                )) + "\n",
                "transfers", array()
        ));
        files.put("planning_cadence.json", object(
                "schema_version", 1,
                "configuration", object("periodic_interval_ticks", 1200),
                "configuration_identity", "butchercraft:planning_cadence_configuration/sanitized",
                "last_completed_cycle_tick", 38401,
                "next_periodic_eligibility_tick", 39601,
                "pending_triggers", array(),
                "cycle_evidence", array(),
                "active_cycle_id", JsonNull.INSTANCE,
                "revision", 66
        ));
        for (String name : List.of(
                "planning_observations.json", "planning_needs.json", "planning_opportunities.json",
                "planning_candidates.json", "planning_approved_plans.json", "planning_runtime.json"
        )) files.put(name, simple(1, "records", array()));
        files.put("production_processes.json", simple(1, "processes", array()));
        files.put("production_plans.json", simple(1, "plans", array()));
        files.put("production_runs.json", simple(1, "runs", array()));
        files.put("transactions.json", simple(1, "transactions", array()));
        files.put("inventory.json", object("schema_version", 1, "storage_nodes", array(),
                "inventory_containers", array(), "inventory_runtimes", array()));
        files.put("business_calendar_runtime.json", object("schema_version", 1,
                "configuration_identity", "butchercraft:business_runtime_config/v1/sanitized", "records", array()));
        files.put("business_runtime.json", simple(1, "business_runtime_states", array()));
        files.put("world_time.json", object("schema_version", 1,
                "configuration_identity", "butchercraft:world_time_config/v1/sanitized", "records", array()));
        files.put("departments.json", simple(1, "departments", array()));
        files.put("employee_records.json", object("schema_version", 1, "next_sequence", 1, "records", array()));
        files.put("employee_material_handling_assignments.json", object(
                "schema_version", 1, "owner_revision", 12, "assignments", array()));
        files.put("workforce_definitions.json", simple(1, "workforce_definitions", array()));
        files.put("goods.json", simple(1, "goods", array()));
        files.put("economic_actors.json", simple(1, "economic_actors", array()));
        files.put("orders.json", simple(1, "orders", array()));
        files.put("contracts.json", simple(1, "contracts", array()));
        files.put("player_identities.json", simple(1, "player_identities", array()));
        return files;
    }

    private JsonObject scheduler() {
        JsonObject definition = object(
                "schema_version", 2,
                "id", "butchercraft:economic_planning_cycle/continuation",
                "type_id", "butchercraft:economic_planning_cycle",
                "stage_id", "butchercraft:planning",
                "scheduled_tick", 1
        );
        JsonObject runtime = object(
                "schema_version", 2,
                "work_id", "butchercraft:economic_planning_cycle/continuation",
                "status", "deferred",
                "next_eligible_tick", 39601
        );
        return object(
                "schema_version", 2,
                "last_finalized_simulation_tick", SCHEDULER_TICK,
                "next_submission_sequence", 127,
                "stages", array(),
                "work", array(object("definition", definition, "runtime", runtime))
        );
    }

    private JsonObject executionOperations() {
        JsonArray operations = new JsonArray();
        for (int sequence = 1; sequence <= 9; sequence++) operations.add(terminalOperation(sequence));
        operations.add(currentOperation());
        return object(
                "schema_version", 1,
                "handler_registry_identity", "butchercraft:execution_handler_registry/v1/sanitized",
                "configuration_identity", "butchercraft:execution_runtime_configuration/v1/standard",
                "operations", operations
        );
    }

    private JsonObject terminalOperation(int sequence) {
        String operationId = operationId(sequence);
        String authorizationDigest = digest(100 + sequence);
        String invocation = "butchercraft:scheduler_invocation/v2/" + hex(200 + sequence);
        String effect = "butchercraft:scheduler_effect_identity/v2/" + hex(300 + sequence);
        String domainEffect = id("execution_domain_effect", 400 + sequence);
        String ownerIdentity = id("workstation_result", 600 + sequence);
        String resultIdentity = id("execution_result_evidence", 700 + sequence);
        long tick = 39_323L + (sequence * 61L);
        JsonObject authorization = authorization(operationId, authorizationDigest, sequence, tick);
        JsonObject ownerResult = object(
                "schema_version", 1,
                "owner_subsystem_id", "butchercraft:workstation",
                "owner_result_identity", ownerIdentity,
                "domain_effect_identity", domainEffect,
                "owner_result_digest", digest(800 + sequence),
                "content_digest", digest(900 + sequence)
        );
        JsonObject result = object(
                "schema_version", 1,
                "evidence_identity", resultIdentity,
                "operation_id", operationId,
                "terminal_status", "succeeded",
                "authorization_identity", string(authorization, "authorization_identity"),
                "authorization_content_digest", authorizationDigest,
                "domain_effect_identity", domainEffect,
                "scheduler_invocation_identity", invocation,
                "scheduler_effect_identity", effect,
                "owner_result_evidence", ownerResult,
                "failure", JsonNull.INSTANCE,
                "result_content_digest", digest(1000 + sequence)
        );
        JsonObject attempt = object(
                "schema_version", 1,
                "attempt_id", id("execution_attempt", 1100 + sequence),
                "operation_id", operationId,
                "attempt_sequence", 1,
                "simulation_tick", tick,
                "scheduler_invocation_identity", invocation,
                "scheduler_effect_identity", effect,
                "handler_id", "butchercraft:execution_handler/patty_former_player_operation",
                "owner_result_identity", ownerIdentity
        );
        return object(
                "schema_version", 1,
                "operation_id", operationId,
                "authorization_evidence", authorization,
                "domain_effect_identity", domainEffect,
                "status", "succeeded",
                "created_simulation_tick", tick - 60,
                "last_updated_simulation_tick", tick,
                "revision", 4,
                "attempt_sequence", 1,
                "scheduler_invocation_started", true,
                "failure", JsonNull.INSTANCE,
                "owner_result_evidence", ownerResult,
                "result_evidence", result,
                "attempts", array(attempt)
        );
    }

    private JsonObject currentOperation() {
        String operationId = operationId(10);
        return object(
                "schema_version", 1,
                "operation_id", operationId,
                "authorization_evidence", authorization(operationId, digest(110), 10, CLOCK_TICK),
                "domain_effect_identity", id("execution_domain_effect", 410),
                "status", "authorized",
                "created_simulation_tick", CLOCK_TICK,
                "last_updated_simulation_tick", CLOCK_TICK,
                "revision", 1,
                "attempt_sequence", 0,
                "scheduler_invocation_started", false,
                "failure", JsonNull.INSTANCE,
                "owner_result_evidence", JsonNull.INSTANCE,
                "result_evidence", JsonNull.INSTANCE,
                "attempts", array()
        );
    }

    private JsonObject authorization(String operationId, String contentDigest, int sequence, long tick) {
        return object(
                "schema_version", 1,
                "authorization_identity", "butchercraft:execution_authorization/v1/"
                        + contentDigest.substring("sha256:".length()),
                "authorization_source_owner", "butchercraft:workstation",
                "executable_work_reference_type", "butchercraft:workstation/patty_former",
                "executable_work_reference_id", "butchercraft:workstation/patty_former/minecraft/overworld/0/64/0",
                "operation_type", "butchercraft:workstation/patty_former_operation",
                "handler_id", "butchercraft:execution_handler/patty_former_player_operation",
                "frozen_input_identity", id("workstation_input", 1200 + sequence),
                "source_freshness_identity", id("workstation_freshness", 1300 + sequence),
                "configuration_identity", "butchercraft:execution_configuration/patty_former_player_operation_v1",
                "world_identity", WORLD_ID,
                "issued_simulation_tick", tick,
                "valid_until_simulation_tick", tick + 260,
                "explicit_input_identities", array(),
                "authorization_content_digest", contentDigest
        );
    }

    private JsonObject machineRuns() {
        JsonArray terminalChildren = new JsonArray();
        for (int sequence = 1; sequence <= 9; sequence++) {
            long tick = 39_323L + (sequence * 61L);
            terminalChildren.add(object(
                    "schema_version", 1,
                    "child_identity", childId(sequence),
                    "run_identity", object("value", RUN_ID),
                    "sequence", sequence,
                    "workstation_instance_identity", WORKSTATION_ID,
                    "operation_id", object("value", operationId(sequence)),
                    "authorization_content_digest", digest(100 + sequence),
                    "operation_type", "butchercraft:workstation/patty_former_operation",
                    "source_freshness_identity", id("workstation_freshness", 1300 + sequence),
                    "configuration_identity", "butchercraft:execution_configuration/patty_former_player_operation_v1",
                    "state", "COMPLETED",
                    "prepared_simulation_tick", tick - 60,
                    "last_updated_simulation_tick", tick,
                    "terminal_evidence_identity", id("execution_result_evidence", 700 + sequence),
                    "failure_code", JsonNull.INSTANCE,
                    "content_digest", digest(1400 + sequence)
            ));
        }
        JsonObject current = object(
                "schema_version", 1,
                "child_identity", childId(10),
                "run_identity", object("value", RUN_ID),
                "sequence", 10,
                "workstation_instance_identity", WORKSTATION_ID,
                "operation_id", object("value", operationId(10)),
                "authorization_content_digest", digest(110),
                "operation_type", "butchercraft:workstation/patty_former_operation",
                "source_freshness_identity", id("workstation_freshness", 1310),
                "configuration_identity", "butchercraft:execution_configuration/patty_former_player_operation_v1",
                "state", "ADMITTED",
                "prepared_simulation_tick", CLOCK_TICK,
                "last_updated_simulation_tick", CLOCK_TICK,
                "terminal_evidence_identity", JsonNull.INSTANCE,
                "failure_code", JsonNull.INSTANCE,
                "content_digest", digest(1410)
        );
        JsonObject run = object(
                "schema_version", 1,
                "run_identity", object("value", RUN_ID),
                "world_identity", WORLD_ID,
                "workstation_instance_identity", WORKSTATION_ID,
                "generation", 1,
                "operating_policy_identity", id("machine_operating_policy", 1500),
                "configuration_identity", "butchercraft:machine_run_configuration/schema_1",
                "lifecycle", "AUTHORIZED",
                "revision", 29,
                "created_simulation_tick", 39324,
                "last_updated_simulation_tick", CLOCK_TICK,
                "next_child_sequence", 11,
                "current_child", current,
                "terminal_children", terminalChildren,
                "content_digest", digest(1600)
        );
        return object(
                "schema_version", 1,
                "owner_revision", 370,
                "world_identity", WORLD_ID,
                "configuration_identity", "butchercraft:machine_run_configuration/schema_1",
                "generation_allocators", array(),
                "runs", array(run)
        );
    }

    private JsonObject operatingRecord() {
        return object(
                "schema_version", 1,
                "workstation", object(
                        "instance_id", object("value", WORKSTATION_ID),
                        "generation", 6,
                        "allocation_configuration_identity",
                        "butchercraft:workstation_instance_configuration/v1/standard"
                ),
                "policy", object(
                        "kind", "POWERED_CONTINUOUS_EXPLICIT_STOP",
                        "configuration_identity", "butchercraft:machine_operating_policy_configuration/schema_1"
                ),
                "state", "RUNNING",
                "revision", 30,
                "current_run_identity", object("value", RUN_ID),
                "last_observed_simulation_tick", CLOCK_TICK,
                "content_digest", digest(1700)
        );
    }

    private JsonObject instance() {
        return object(
                "schema_version", 1,
                "instance_identity", WORKSTATION_ID,
                "world_identity", world(),
                "endpoint_key", object(
                        "workstation_type_identity", "butchercraft:patty_former",
                        "dimension_identity", "minecraft:overworld",
                        "x", 0,
                        "y", 64,
                        "z", 0
                ),
                "generation", 6,
                "allocation_configuration_identity", "butchercraft:workstation_instance_configuration/v1/standard",
                "lifecycle", "ACTIVE",
                "creation_revision", 14,
                "last_update_revision", 15,
                "unresolved_journal_references", array()
        );
    }

    private JsonObject world() {
        return object("identity", WORLD_ID, "schema_version", 6, "root_digest", WORLD_DIGEST);
    }

    private RecoveryOperatorEvidence actualOperatorEvidence(SplitSnapshotRecoveryPlan plan) {
        String canonical = String.join("\n",
                "principal=butchercraft:operator/r2a_offline_validation",
                "authority=ADMINISTRATOR",
                "world=" + plan.worldIdentityRoot().identity(),
                "recovery=" + plan.recoveryIdentity().value(),
                "analysis=" + plan.analysisDigest()
        );
        String contentDigest = CheckpointSnapshotDigest.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        return new RecoveryOperatorEvidence(
                "butchercraft:operator/r2a_offline_validation",
                RecoveryOperatorAuthority.ADMINISTRATOR,
                "butchercraft:recovery_operator_evidence/r2a_offline_validation/"
                        + contentDigest.substring("sha256:".length()),
                contentDigest
        );
    }

    private List<LegacySplitRecoveryOwnerPreparer> preparers() {
        List<LegacySplitRecoveryOwnerPreparer> values = new ArrayList<>();
        values.add(new SimulationClockLegacyRecoveryOwnerPreparer());
        values.add(new SchedulerLegacyRecoveryOwnerPreparer());
        values.add(new ExecutionLegacyRecoveryOwnerPreparer());
        values.add(new WorkstationLegacyRecoveryOwnerPreparer());
        values.add(new PlanningLegacyRecoveryOwnerPreparer());
        values.add(new CheckpointRecoveryLegacyRecoveryOwnerPreparer());
        for (CheckpointOwnerId owner : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            if (values.stream().noneMatch(value -> value.ownerId().equals(owner))) {
                values.add(new ExactUnchangedLegacyRecoveryOwnerPreparer(owner));
            }
        }
        return List.copyOf(values);
    }

    private List<Path> protectedRoots() {
        String configured = System.getenv("BUTCHERCRAFT_R2A_PROTECTED_ROOTS");
        if (configured == null || configured.isBlank()) return List.of();
        return java.util.Arrays.stream(configured.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private Map<CheckpointOwnerId, Map<String, String>> ownerFields(CheckpointRecoveredGeneration generation) {
        Map<CheckpointOwnerId, Map<String, String>> result = new HashMap<>();
        generation.ownerSnapshots().forEach(snapshot -> {
            LegacyRecoveryOwnerSnapshotDocument document =
                    LegacyRecoveryOwnerSnapshotDocument.deserialize(snapshot.payloadBytes());
            Map<String, String> fields = new HashMap<>();
            document.fields().forEach(field -> fields.put(field.key(), field.value()));
            result.put(snapshot.descriptor().ownerId(), fields);
        });
        return result;
    }

    private Map<String, byte[]> nativeFiles(Path root) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> !candidate.startsWith(root.resolve("butchercraft/checkpoints")))
                    .filter(candidate -> !candidate.getFileName().toString().equals("session.lock"))
                    .sorted()
                    .toList()) {
                result.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return result;
    }

    private Path writeNativeCopy(Path root, Map<String, byte[]> files) throws IOException {
        List<Map.Entry<String, byte[]>> entries = new ArrayList<>(files.entrySet());
        java.util.Collections.reverse(entries);
        for (Map.Entry<String, byte[]> entry : entries) {
            Path target = root.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
        return root;
    }

    private String treeDigest(Path root) throws IOException {
        List<String> canonical = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> !candidate.getFileName().toString().equals("session.lock"))
                    .sorted()
                    .toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(path);
                canonical.add(name + "\t" + bytes.length + "\t" + CheckpointSnapshotDigest.sha256(bytes));
            }
        }
        return CheckpointSnapshotDigest.sha256(String.join("\n", canonical).getBytes(StandardCharsets.UTF_8));
    }

    private long regularFileCount(Path root) throws IOException {
        if (!Files.exists(root)) return 0L;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private JsonObject read(Path root, String name) throws IOException {
        return GSON.fromJson(Files.readString(root.resolve("butchercraft").resolve(name)), JsonObject.class);
    }

    private void write(Path root, String name, JsonObject value) throws IOException {
        Files.writeString(root.resolve("butchercraft").resolve(name), GSON.toJson(value) + "\n");
    }

    private static JsonObject simple(int schema, String field, JsonArray values) {
        return object("schema_version", schema, field, values);
    }

    private static JsonObject object(Object... fields) {
        JsonObject object = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) {
            String name = (String) fields[index];
            Object value = fields[index + 1];
            if (value == null || value == JsonNull.INSTANCE) object.add(name, JsonNull.INSTANCE);
            else if (value instanceof JsonObject json) object.add(name, json);
            else if (value instanceof JsonArray array) object.add(name, array);
            else if (value instanceof Boolean bool) object.addProperty(name, bool);
            else if (value instanceof Number number) object.addProperty(name, number);
            else object.addProperty(name, value.toString());
        }
        return object;
    }

    private static JsonArray array(JsonObject... values) {
        JsonArray array = new JsonArray();
        for (JsonObject value : values) array.add(value);
        return array;
    }

    private static String string(JsonObject object, String name) {
        return object.get(name).getAsString();
    }

    private static String operationId(int sequence) {
        return id("execution_operation", sequence);
    }

    private static String childId(int sequence) {
        return id("machine_run_child", sequence);
    }

    private static String id(String type, int value) {
        return "butchercraft:" + type + "/v1/" + hex(value);
    }

    private static String digest(int value) {
        return "sha256:" + hex(value);
    }

    private static String hex(int value) {
        return String.format(java.util.Locale.ROOT, "%064x", value);
    }
}
