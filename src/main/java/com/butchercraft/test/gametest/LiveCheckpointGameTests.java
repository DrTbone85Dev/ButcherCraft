package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointPublicationRequest;
import com.butchercraft.world.checkpoint.CheckpointPublicationReport;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.LiveCheckpointParticipantRegistry;
import com.butchercraft.world.checkpoint.LiveCheckpointRequestOutcome;
import com.butchercraft.world.checkpoint.LiveCheckpointService;
import com.butchercraft.world.checkpoint.LiveCheckpointStatus;
import com.butchercraft.world.checkpoint.LivePlatformDeterminismManifest;
import com.butchercraft.world.checkpoint.RestorationStorage;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.world.checkpoint.StartupRecoveryService;
import com.butchercraft.world.checkpoint.StartupRecoverySource;
import com.butchercraft.world.checkpoint.StartupRecoveryState;
import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecordV2;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import com.butchercraft.integration.checkpoint.NativeOwnerRestorationAdapters;
import com.butchercraft.integration.checkpoint.LiveOwnerCoherenceStatus;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveCheckpointGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos CUTTING_TABLE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos GRINDER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 3);
    private static final int MAX_FAILURE_SUMMARY_LENGTH = 900;

    private LiveCheckpointGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_01_healthy")
    public static void createsCheckpointInHealthyWorld(GameTestHelper helper) {
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(status.participantCount() == 17, "Live checkpoint includes all 17 owners");
            helper.assertTrue(status.checkpointSizeBytes() > 0L, "Live checkpoint publishes durable bytes");
            helper.assertTrue(status.freezeDurationNanos() > 0L, "Live checkpoint measures snapshot freeze");
            helper.assertTrue(status.publicationDurationNanos() > 0L, "Live checkpoint measures publication");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_02_manifest")
    public static void verifiesCompleteLiveParticipantManifest(GameTestHelper helper) {
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            var generation = selectedGeneration(helper);
            helper.assertTrue(generation.manifest().ownerSnapshots().size() == 17,
                    "Committed generation manifest contains 17 participants");
            helper.assertTrue(generation.manifest().ownerSnapshots().stream()
                            .map(snapshot -> snapshot.ownerId()).toList()
                            .equals(LiveCheckpointParticipantRegistry.requiredOwners()),
                    "Committed generation contains the exact canonical participant set");
            helper.assertTrue(generation.manifest().triggerCauses().contains(
                            "butchercraft:checkpoint_trigger/manual"),
                    "Committed generation persists its manual trigger cause");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_03_grinder_running")
    public static void checkpointsContinuouslyRunningGrinder(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 4));
        helper.assertTrue(grinder.startRun().accepted(), "Grinder continuous Run starts");
        var runIdentity = grinder.runStatus().runIdentity().orElseThrow();
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(grinder.runStatus().runIdentity().filter(runIdentity::equals).isPresent(),
                    "Checkpoint does not replace the active Grinder Run");
            helper.assertTrue(grinder.inventory().input().getCount() + grinder.inventory().output().getCount() == 4,
                    "Checkpoint preserves the exact Grinder item total");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_04_patty_running")
    public static void checkpointsContinuouslyRunningPattyFormer(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 4));
        helper.assertTrue(pattyFormer.startRun().accepted(), "Patty Former continuous Run starts");
        var runIdentity = pattyFormer.runStatus().runIdentity().orElseThrow();
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(pattyFormer.runStatus().runIdentity().filter(runIdentity::equals).isPresent(),
                    "Checkpoint does not replace the active Patty Former Run");
            helper.assertTrue(pattyFormer.inventory().input().getCount()
                            + pattyFormer.inventory().output().getCount() == 4,
                    "Checkpoint preserves the exact Patty Former item total");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_05_running_empty")
    public static void checkpointsGrinderRunningEmpty(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        helper.assertTrue(grinder.startRun().accepted(), "Empty Grinder Run starts");
        helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.RUNNING_EMPTY,
                "Grinder is RUNNING_EMPTY before checkpoint");
        var runIdentity = grinder.runStatus().runIdentity().orElseThrow();
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.RUNNING_EMPTY,
                    "Checkpoint preserves RUNNING_EMPTY");
            helper.assertTrue(grinder.runStatus().runIdentity().filter(runIdentity::equals).isPresent(),
                    "RUNNING_EMPTY checkpoint preserves Run identity");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_06_output_blocked")
    public static void checkpointsOutputBlockedGrinder(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2));
        grinder.inventory().setOutputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 64));
        helper.assertTrue(grinder.startRun().accepted(), "Blocked Grinder Run starts");
        helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.OUTPUT_BLOCKED,
                "Grinder is OUTPUT_BLOCKED before checkpoint");
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.OUTPUT_BLOCKED,
                    "Checkpoint preserves OUTPUT_BLOCKED");
            helper.assertTrue(grinder.inventory().input().getCount() == 2
                            && grinder.inventory().output().getCount() == 64,
                    "Blocked checkpoint preserves exact inventory");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_07_custody")
    public static void checkpointsMaterialHandlingCustody(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        ItemStack source = count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2);
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(source)
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Cutting Table source is prepared");
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 64));
        var transfer = (MaterialTransferRecordV2) MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS),
                helper.absolutePos(GRINDER_POS)
        ).transfer().orElseThrow();
        helper.assertTrue(transfer.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED,
                "Blocked destination leaves proven custody in RECOVERY_REQUIRED");
        helper.assertTrue(transfer.exactInTransitCustody().isPresent(), "Material Handling owns exact custody");
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            String materialHandling = ownerFile(helper,
                    LegacySplitRecoveryParticipants.MATERIAL_HANDLING, "material_handling.json");
            helper.assertTrue(materialHandling.contains(transfer.transferReference().value()),
                    "Checkpoint contains the exact Material Handling transfer identity");
            helper.assertTrue(materialHandling.contains("RECOVERY_REQUIRED"),
                    "Checkpoint preserves the custody recovery state");
            var cancellation = MaterialHandlingService.INSTANCE.cancel(
                    helper.getLevel(), transfer.transferReference(), "Checkpoint GameTest fixture teardown");
            helper.assertTrue(cancellation.succeeded(),
                    "Checkpoint custody fixture returns proven custody before teardown");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_08_inventory_projection")
    public static void checkpointsExactLoadedWorkstationInventories(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 10));
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 7));
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            String projections = ownerFile(helper,
                    LegacySplitRecoveryParticipants.WORKSTATION, "workstation_projections.json");
            helper.assertTrue(projections.contains("\"schema_version\": 2")
                            && projections.contains("\"payload_base64\"")
                            && projections.contains("\"workstation_restorable_status\": \"complete_restorable\""),
                    "Workstation participant embeds exact self-verifying durable projection payloads");
            helper.assertTrue(grinder.inventory().input().getCount() == 10
                            && pattyFormer.inventory().input().getCount() == 7,
                    "Checkpoint packaging does not mutate workstation inventories");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_09_run_continuity")
    public static void checkpointDoesNotRestartOrStopMachineRun(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 3));
        helper.assertTrue(grinder.startRun().accepted(), "Grinder Run starts");
        var identity = grinder.runStatus().runIdentity().orElseThrow();
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            helper.assertTrue(grinder.runStatus().runIdentity().filter(identity::equals).isPresent(),
                    "Checkpoint preserves Machine Run Identity");
            helper.assertTrue(grinder.runStatus().runIdentity().isPresent(),
                    "Checkpoint does not stop the active Machine Run");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40000, batch = "checkpoint_10_generation_advance")
    public static void laterCheckpointAdvancesGeneration(GameTestHelper helper) {
        long before = requestCheckpoint(helper);
        long[] firstSequence = {-1L};
        helper.succeedWhen(() -> {
            LiveCheckpointStatus current = LiveCheckpointService.INSTANCE.status();
            helper.assertTrue(current.state() != LiveCheckpointStatus.State.FAILED,
                    "Live checkpoint did not fail: " + failureSummary(current));
            long sequence = committedSequence();
            if (firstSequence[0] < 0L && sequence > before) {
                firstSequence[0] = sequence;
                var second = LiveCheckpointService.INSTANCE.requestManual(helper.getLevel().getServer());
                helper.assertTrue(second.outcome() == LiveCheckpointRequestOutcome.ACCEPTED,
                        "Second checkpoint request is accepted");
            }
            helper.assertTrue(firstSequence[0] >= 0L && sequence == firstSequence[0] + 1L,
                    "Later checkpoint has not advanced exactly one generation yet");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_11_incomplete_failure")
    public static void failedIncompleteCandidateLeavesCommittedGenerationValid(GameTestHelper helper) {
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, status -> {
            MinecraftServer server = helper.getLevel().getServer();
            var selected = selectedGeneration(helper).manifest();
            CheckpointGenerationId rejectedId = CheckpointGenerationId.of(
                    selected.generationId().committedSequence() + 1L,
                    selected.authoritativeSimulationTick()
            );
            CheckpointPublicationReport rejected = checkpointStore(helper).publish(new CheckpointPublicationRequest(
                    rejectedId,
                    Optional.of(selected.generationId()),
                    Optional.of(selected.manifestDigest()),
                    rejectedId.authoritativeSimulationTick(),
                    List.of(),
                    LiveCheckpointParticipantRegistry.requiredOwners(),
                    List.of("butchercraft:checkpoint_trigger/gametest_failure"),
                    LivePlatformDeterminismManifest.currentReference(server),
                    worldRoot(server)
            ));

            helper.assertFalse(rejected.successful(), "Incomplete candidate publication is rejected");
            helper.assertTrue(selectedGeneration(helper).manifest().generationId().equals(selected.generationId()),
                    "Prior committed generation remains selected after incomplete publication");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_12_continue_after_failure")
    public static void gameplayContinuesAfterRejectedIncompleteCheckpoint(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        grinder.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
        helper.assertTrue(grinder.startRun().accepted(), "Grinder starts after prior incomplete candidate rejection");
        helper.succeedWhen(() -> {
            helper.assertTrue(grinder.inventory().output().getCount() == 1,
                    "Normal machine processing continues after rejected checkpoint publication");
            clearCheckpointFixtureWorkstations(helper);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100000, batch = "checkpoint_13_long_run")
    public static void twentyLiveCheckpointsRemainMonotonicDuringContinuousProcessing(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 20));
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 20));
        helper.assertTrue(grinder.startRun().accepted(), "Long-run Grinder starts");
        helper.assertTrue(pattyFormer.startRun().accepted(), "Long-run Patty Former starts");
        long initial = committedSequence();
        long[] observed = {initial};
        int[] completed = {0};
        helper.assertTrue(LiveCheckpointService.INSTANCE.requestManual(helper.getLevel().getServer()).outcome()
                        == LiveCheckpointRequestOutcome.ACCEPTED,
                "First long-run checkpoint is accepted");

        helper.succeedWhen(() -> {
            LiveCheckpointStatus status = LiveCheckpointService.INSTANCE.status();
            helper.assertTrue(status.state() != LiveCheckpointStatus.State.FAILED,
                    "Long-run checkpoint publication remains healthy: " + failureSummary(status));
            long current = committedSequence();
            if (current > observed[0]) {
                helper.assertTrue(current == observed[0] + 1L,
                        "Long-run checkpoint generation advances monotonically");
                observed[0] = current;
                completed[0]++;
                helper.assertTrue(status.checkpointTick() >= 0L, "Checkpoint tick remains valid");
                if (completed[0] < 20) {
                    helper.assertTrue(LiveCheckpointService.INSTANCE.requestManual(helper.getLevel().getServer()).outcome()
                                    == LiveCheckpointRequestOutcome.ACCEPTED,
                            "Next long-run checkpoint is accepted");
                }
            }
            helper.assertTrue(completed[0] == 20, "Twenty live checkpoints have not committed yet");
            helper.assertTrue(grinder.runStatus().runIdentity().isPresent(),
                    "Grinder retains one continuous Run through checkpoint stress");
            helper.assertTrue(pattyFormer.runStatus().runIdentity().isPresent(),
                    "Patty Former retains one continuous Run through checkpoint stress");
            clearCheckpointFixtureWorkstations(helper);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "checkpoint_14_startup_live")
    public static void healthyStartupSelectsCoherentLiveState(GameTestHelper helper) {
        var status = StartupRecoveryService.INSTANCE.status();
        helper.assertTrue(status.state() == StartupRecoveryState.LIVE_SELECTED,
                "Healthy startup reaches LIVE_SELECTED: " + status.state());
        helper.assertTrue(status.selectedSource() == StartupRecoverySource.LIVE,
                "Healthy startup selects live owner state");
        helper.assertTrue(status.liveCoherence() == LiveOwnerCoherenceStatus.COHERENT
                        || status.liveCoherence() == LiveOwnerCoherenceStatus.COHERENT_EMPTY,
                "Healthy startup proves live coherence");
        helper.assertTrue(status.restorationIdentity().isEmpty(),
                "Healthy startup does not create a Restoration Identity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "checkpoint_15_startup_registry")
    public static void startupInstallsExactOwnerRegistryAndOpenGate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var adapters = NativeOwnerRestorationAdapters.forServer(server);
        helper.assertTrue(adapters.stream().map(adapter -> adapter.ownerId()).toList()
                        .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS),
                "Startup restoration registry contains the exact 17 owners");
        var gate = StartupMutationGateService.INSTANCE.currentGate(server);
        helper.assertTrue(gate.isPresent(), "Startup installs the recovery mutation gate");
        helper.assertTrue(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                        .allMatch(owner -> gate.orElseThrow().permitsConsequentialMutation(owner)),
                "Healthy startup leaves all owner mutations permitted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_16_live_wins")
    public static void committedCheckpointDoesNotDisplaceAlreadyCoherentLiveStartup(GameTestHelper helper) {
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, ignored -> {
            var status = StartupRecoveryService.INSTANCE.status();
            helper.assertTrue(status.selectedSource() == StartupRecoverySource.LIVE,
                    "Coherent live startup remains authoritative when a checkpoint exists");
            helper.assertTrue(new RestorationStorage(
                            LiveCheckpointParticipantRegistry.checkpointRoot(helper.getLevel().getServer()))
                            .incompleteIntents().isEmpty(),
                    "Coherent live startup leaves no incomplete restoration intent");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20000, batch = "checkpoint_17_workstation_restorable")
    public static void committedCheckpointPassesReadOnlyWorkstationRestorabilityGate(GameTestHelper helper) {
        placeGrinder(helper);
        placePattyFormer(helper);
        long before = requestCheckpoint(helper);
        succeedAfterCommit(helper, before, ignored -> {
            var report = WorkstationCheckpointRestorabilityVerifier.verify(selectedGeneration(helper));
            helper.assertTrue(report.restorable(),
                    "Committed R3B generation is Workstation-restorable: " + report.blockers());
            helper.assertTrue(report.requiredProjectionCount() == report.availableProjectionCount(),
                    "Every required Workstation has one exact embedded projection");
        });
    }

    private static long requestCheckpoint(GameTestHelper helper) {
        long before = committedSequence();
        var result = LiveCheckpointService.INSTANCE.requestManual(helper.getLevel().getServer());
        helper.assertTrue(result.outcome() == LiveCheckpointRequestOutcome.ACCEPTED,
                "Manual live checkpoint request is accepted: " + result.detail());
        return before;
    }

    private static void succeedAfterCommit(
            GameTestHelper helper,
            long priorSequence,
            java.util.function.Consumer<LiveCheckpointStatus> assertions
    ) {
        helper.succeedWhen(() -> {
            LiveCheckpointStatus status = LiveCheckpointService.INSTANCE.status();
            helper.assertTrue(status.state() != LiveCheckpointStatus.State.FAILED,
                    "Live checkpoint did not fail: " + failureSummary(status));
            helper.assertTrue(committedSequence() > priorSequence, "Live checkpoint has not committed yet");
            assertions.accept(status);
            clearCheckpointFixtureWorkstations(helper);
        });
    }

    private static void clearCheckpointFixtureWorkstations(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, Blocks.AIR.defaultBlockState());
        helper.setBlock(GRINDER_POS, Blocks.AIR.defaultBlockState());
        helper.setBlock(PATTY_FORMER_POS, Blocks.AIR.defaultBlockState());
    }

    private static String failureSummary(LiveCheckpointStatus status) {
        String summary = status.failures().toString();
        if (summary.length() <= MAX_FAILURE_SUMMARY_LENGTH) return summary;
        return summary.substring(0, MAX_FAILURE_SUMMARY_LENGTH)
                + "... (" + status.failures().size() + " failures total)";
    }

    private static long committedSequence() {
        return LiveCheckpointService.INSTANCE.status().committedGeneration()
                .map(CheckpointGenerationId::committedSequence)
                .orElse(0L);
    }

    private static CheckpointFilesystemStore checkpointStore(GameTestHelper helper) {
        return new CheckpointFilesystemStore(
                LiveCheckpointParticipantRegistry.checkpointRoot(helper.getLevel().getServer())
        );
    }

    private static com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration selectedGeneration(
            GameTestHelper helper
    ) {
        MinecraftServer server = helper.getLevel().getServer();
        var report = checkpointStore(helper).loadSelectedGeneration(new CheckpointFilesystemRecoveryRequest(
                LiveCheckpointParticipantRegistry.requiredOwners(),
                worldRoot(server),
                LivePlatformDeterminismManifest.currentReference(server)
        ));
        helper.assertTrue(report.successful(), "Committed live checkpoint reloads and verifies: " + report.failures());
        return report.recoveredGeneration().orElseThrow();
    }

    private static String ownerFile(GameTestHelper helper, com.butchercraft.world.checkpoint.CheckpointOwnerId owner,
                                    String logicalName) {
        var payload = selectedGeneration(helper).ownerSnapshots().stream()
                .filter(snapshot -> snapshot.descriptor().ownerId().equals(owner))
                .findFirst()
                .orElseThrow();
        CheckpointOwnerFileSnapshot bundle = CheckpointOwnerFileBundleCodec.decode(payload.payloadBytes());
        return bundle.files().stream()
                .filter(file -> file.logicalName().equals(logicalName))
                .map(file -> new String(file.bytes(), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }

    private static WorldIdentityRootReference worldRoot(MinecraftServer server) {
        var identity = WorldIdentityRootIdentities.from(WorldIdentityService.INSTANCE.getOrCreate(server));
        return new WorldIdentityRootReference(identity.identity(), identity.schemaVersion(), identity.rootDigest());
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        return (GrinderBlockEntity) helper.getBlockEntity(GRINDER_POS);
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper) {
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        return (PattyFormerBlockEntity) helper.getBlockEntity(PATTY_FORMER_POS);
    }

    private static CuttingTableBlockEntity placeCuttingTable(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        return (CuttingTableBlockEntity) helper.getBlockEntity(CUTTING_TABLE_POS);
    }

    private static ItemStack count(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }
}
