package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.condition.*;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.workstation.checkpoint.ConditionCheckpointClosure;
import com.butchercraft.world.simulation.SimulationClockService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineConditionGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos POS = new BlockPos(2, 1, 2);
    private static final DurableWorkstationProjectionService OWNER = DurableWorkstationProjectionService.INSTANCE;
    private MachineConditionGameTests() { }

    @GameTest(template = TEMPLATE)
    public static void grinderHasDurableHealthyInertCondition(GameTestHelper helper) {
        GrinderBlockEntity machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            var condition = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(condition.policy().inert() && condition.mechanicalLoss() == 0 && condition.revision() == 1,
                    "Grinder production policy initializes durable healthy condition without consequences");
            helper.assertTrue(condition.instanceId().equals(machine.checkpointInstanceIdentity().orElseThrow()), "Condition binds exact instance");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pattyFormerHasIndependentInertCondition(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        var machine = (PattyFormerBlockEntity) helper.getBlockEntity(POS);
        helper.runAtTickTime(2, () -> {
            var condition = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(condition.policy().inert() && condition.policy().machineType().equals("butchercraft:patty_former"),
                    "Patty Former has its own inert policy, not Grinder condition content");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cuttingTableConditionIsExplicitlyNotApplicable(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        var machine = (AbstractInventoryWorkstationBlockEntity) helper.getBlockEntity(POS);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(projection(helper, machine).condition().orElseThrow().equals(ConditionProjection.notApplicable()),
                    "Cutting Table has an explicit non-applicable marker and no wear state");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void inertGrinderChildKeepsProductAndConditionJoint(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            helper.assertTrue(machine.startRun().accepted(), "Grinder Run starts");
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(100, () -> {
            var durable = projection(helper, machine);
            var state = durable.condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(machine.inventory().output().is(ModItems.GROUND_BEEF.get()) && machine.inventory().output().getCount() == 1,
                    "Existing Grinder produces exactly one Ground Beef");
            helper.assertTrue(state.mechanicalLoss() == 0 && state.effectCount() == 1, "Inert child records one exact zero-consequence effect");
            helper.assertTrue(durable.processingOwnerResultIdentity().orElseThrow().startsWith("butchercraft:workstation_result/v2/"),
                    "Durable owner result uses the versioned joint contract");
            OWNER.reconcileLoaded(helper.getLevel(), machine);
            helper.assertTrue(projection(helper, machine).condition().orElseThrow().state().orElseThrow().equals(state),
                    "Repeated loaded observation never reapplies condition");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void nonzeroFixtureGrinderChildUsesRealExecutionAndJointPublication(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            helper.assertTrue(machine.startRun().accepted(), "Test-policy Grinder starts through real Execution");
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(100, () -> {
            var durable = projection(helper, machine);
            var state = durable.condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(machine.inventory().output().is(ModItems.GROUND_BEEF.get()) && machine.inventory().output().getCount() == 1,
                    "One real child commits one product");
            helper.assertTrue(state.mechanicalLoss() == 3 && state.serviceDebt() == 1,
                    "One successful child; STOP within grace adds no empty-run loss");
            var receipts = OWNER.conditionReceipts(helper.getLevel().getServer()).closure(durable.condition().orElseThrow());
            helper.assertTrue(receipts.size() == state.effectCount()
                    && receipts.stream().filter(receipt -> receipt.kind() == ConditionEffectKind.SUCCESSFUL_PROCESSING).count() == 1,
                    "One exact processing effect plus explicit lifecycle boundaries are durable");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void nonzeroFixturePattyChildUsesIndependentOwnerResult(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        var machine = (PattyFormerBlockEntity) helper.getBlockEntity(POS);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.PATTY);
            machine.inventory().setInputInternal(ModItems.GROUND_BEEF.get().getDefaultInstance());
            helper.assertTrue(machine.startRun().accepted(), "Patty Former real Run starts");
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(120, () -> {
            var state = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(machine.inventory().output().is(ModItems.BEEF_PATTIES.get()), "Patty Former owner produces Beef Patties");
            helper.assertTrue(state.mechanicalLoss() == 3 && state.serviceDebt() == 1, "Patty Former applies one fixture consequence");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150)
    public static void exposureUsesBoundedSettlementAndStopClosesIt(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.startRun();
        });
        helper.runAtTickTime(70, () -> {
            var before = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(before.activeExposure().isPresent() && before.mechanicalLoss() > 0, "Fixture dry exposure is durably settled");
            helper.assertTrue(before.effectCount() < 20, "Settlement is bounded, not one durable write per tick");
            machine.stopRun();
            var stopped = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(stopped.activeExposure().isEmpty(), "STOP closes exposure before OFF publication");
            helper.assertTrue(stopped.account(ConditionExposureType.DRY_RUNNING).graceProgress() > 0, "STOP does not reset grace");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150)
    public static void unloadedHookPreservesCutoffAndCreatesNoOfflineWear(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.startRun();
        });
        helper.runAtTickTime(40, () -> {
            machine.onChunkUnloaded();
            var suspended = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(suspended.activeExposure().isEmpty(), "Owner unload hook closes proven loaded interval");
            helper.runAtTickTime(80, () -> {
                var later = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
                helper.assertTrue(later.mechanicalLoss() == suspended.mechanicalLoss()
                        && later.lastAccountedTick() == suspended.lastAccountedTick(), "No fabricated unloaded tail");
                machine.stopRun();
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void conditionCheckpointClosureRestoresExactBytesWithoutReplay(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            machine.startRun();
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(100, () -> {
            var durable = projection(helper, machine);
            var receipts = OWNER.conditionReceipts(helper.getLevel().getServer());
            ConditionCheckpointClosure closure = new ConditionCheckpointClosure();
            closure.include(durable, receipts::frozen);
            var restored = ConditionCheckpointClosure.decode(closure.encode(), java.util.List.of(durable));
            helper.assertTrue(restored.encode().equals(closure.encode()), "Owner-native evidence closure restores byte-exact receipt content");
            helper.assertTrue(durable.equals(new com.butchercraft.workstation.projection.WorkstationProjectionCodec().decode(
                    OWNER.freezeForCheckpoint(helper.getLevel().getServer(), durable.instanceId()).frozenBytes())),
                    "Frozen projection restores exact product and condition without replay");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void replacementDoesNotInheritOldMachineCondition(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            machine.startRun();
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(100, () -> {
            var old = projection(helper, machine);
            helper.setBlock(POS, Blocks.AIR.defaultBlockState());
            var replacement = grinder(helper);
            helper.runAtTickTime(104, () -> {
                var fresh = projection(helper, replacement);
                helper.assertTrue(!fresh.instanceId().equals(old.instanceId()), "Replacement allocates a new exact Workstation identity");
                helper.assertTrue(fresh.condition().orElseThrow().state().orElseThrow().mechanicalLoss() == 0,
                        "Replacement uses canonical healthy condition and cannot inherit old wear");
                helper.succeed();
            });
        });
    }

    private static GrinderBlockEntity grinder(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.GRINDER.get().defaultBlockState());
        return (GrinderBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = TEMPLATE)
    public static void legacyGrinderMigratesHealthyAndPreservesHistoricalBytes(GameTestHelper helper) {
        legacyMigration(helper, grinder(helper));
    }

    @GameTest(template = TEMPLATE)
    public static void legacyPattyFormerMigratesHealthyAndPreservesHistoricalBytes(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        legacyMigration(helper, (PattyFormerBlockEntity) helper.getBlockEntity(POS));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void inertPattyChildAndReadOnlyDiagnosticsPreserveCondition(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        var machine = (PattyFormerBlockEntity) helper.getBlockEntity(POS);
        helper.runAtTickTime(2, () -> {
            machine.inventory().setInputInternal(ModItems.GROUND_BEEF.get().getDefaultInstance());
            machine.startRun();
            stopAfterOutput(helper, machine, machine::stopRun, 10);
        });
        helper.runAtTickTime(120, () -> {
            var before = projection(helper, machine);
            var condition = before.condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(machine.inventory().output().is(ModItems.BEEF_PATTIES.get())
                    && machine.inventory().output().getCount() == 1, "Inert Patty Former produces its unchanged canonical result");
            helper.assertTrue(condition.mechanicalLoss() == 0 && condition.effectCount() == 1,
                    "One zero-loss processing receipt, no powered idle wear");
            BlockPos position = helper.absolutePos(POS);
            String command = "butchercraft workstation status " + position.getX() + " " + position.getY() + " " + position.getZ();
            try {
                helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher().execute(command,
                        helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPermission(4)) == 1,
                        "Existing synchronized diagnostics command executes");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalStateException(exception);
            }
            helper.assertTrue(before.equals(projection(helper, machine)), "Read-only diagnostics publish no condition or inventory mutation");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void outputBlockedTestPolicyCannotEarnOrdinaryWear(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            var output = ModItems.GROUND_BEEF.get().getDefaultInstance();
            output.setCount(64);
            machine.inventory().setOutputInternal(output);
            machine.startRun();
        });
        helper.runAtTickTime(90, () -> {
            var durable = projection(helper, machine);
            var condition = durable.condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(condition.mechanicalLoss() == 0 && condition.activeExposure().isEmpty(),
                    "POWERED-IDLE output blockage creates no processing or dry exposure wear");
            helper.assertTrue(OWNER.conditionReceipts(helper.getLevel().getServer()).closure(durable.condition().orElseThrow())
                    .stream().noneMatch(receipt -> receipt.kind() == ConditionEffectKind.SUCCESSFUL_PROCESSING),
                    "Rejected child admission never earns ordinary wear");
            machine.stopRun();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void restartPolicyBSuspendsFixtureExposureWithoutOfflineWear(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.startRun();
        });
        helper.runAtTickTime(40, () -> {
            var run = machine.runStatus().runIdentity().orElseThrow();
            long tick = SimulationClockService.INSTANCE.clock(helper.getLevel().getServer()).simulationTick();
            com.butchercraft.world.ExecutionMachineRunService.INSTANCE.suspendForRestart(helper.getLevel().getServer(), run, tick);
            com.butchercraft.world.MachineOperatingStateService.INSTANCE.suspendForRestart(helper.getLevel().getServer(), run, tick);
            var suspended = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(suspended.activeExposure().isEmpty(), "Policy B closes the proven loaded exposure");
            helper.runAtTickTime(100, () -> {
                var later = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
                helper.assertTrue(later.equals(suspended), "RESTART_REQUIRED earns no exposure while awaiting explicit authority");
                machine.stopRun();
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void mechanicalFaultRemainsCoherentAndStopSurvivesReload(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.FAULT);
            machine.startRun();
        });
        helper.runAtTickTime(90, () -> {
            var faulted = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(faulted.fault().isPresent() && faulted.activeExposure().isEmpty(),
                    "Test-only threshold creates one controlling fault and ends exposure");
            helper.assertTrue(OWNER.reconcileLoaded(helper.getLevel(), machine).succeeded(),
                    "Mechanical fault is coherent, not recovery failure");
            helper.assertTrue(machine.stopRun().accepted(), "Canonical STOP remains available while mechanically faulted");
            var stopped = projection(helper, machine);
            var reloaded = reload(helper, machine);
            helper.assertTrue(OWNER.reconcileLoaded(helper.getLevel(), reloaded).succeeded(), "Faulted machine reloads coherently");
            helper.assertTrue(stopped.condition().equals(projection(helper, reloaded).condition()),
                    "STOP/reload neither clears the fault nor replays it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 320)
    public static void composedProcessingExposureSupplyBlockedStopReloadAndCheckpoint(GameTestHelper helper) {
        var machine = grinder(helper);
        helper.runAtTickTime(2, () -> {
            testPolicy(helper, machine, MachineConditionGameTestPolicies.GRINDER);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            machine.startRun();
        });
        helper.runAtTickTime(90, () -> {
            var empty = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(machine.inventory().output().getCount() == 1 && empty.mechanicalLoss() > 3,
                    "First child and subsequent bounded empty exposure both committed");
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
        });
        helper.runAtTickTime(170, () -> {
            helper.assertTrue(machine.inventory().output().getCount() == 2, "Supply admits exactly one further child");
            var full = ModItems.GROUND_BEEF.get().getDefaultInstance();
            full.setCount(64);
            machine.inventory().setOutputInternal(full);
            machine.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
        });
        helper.runAtTickTime(190, () -> {
            var blocked = projection(helper, machine).condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(blocked.activeExposure().isEmpty(), "Blocked powered idle closes dry exposure");
            helper.runAtTickTime(230, () -> {
                helper.assertTrue(blocked.equals(projection(helper, machine).condition().orElseThrow().state().orElseThrow()),
                        "Blocked idle earns no wear or repeated bookkeeping writes");
                machine.inventory().setInputInternal(net.minecraft.world.item.ItemStack.EMPTY);
                machine.inventory().setOutputInternal(net.minecraft.world.item.ItemStack.EMPTY);
                machine.stopRun();
                var stopped = projection(helper, machine);
                var reloaded = reload(helper, machine);
                helper.assertTrue(OWNER.reconcileLoaded(helper.getLevel(), reloaded).succeeded(), "Exact owner projection reloads");
                helper.assertTrue(stopped.condition().equals(projection(helper, reloaded).condition()), "No offline/reload exposure");
                var receipts = OWNER.conditionReceipts(helper.getLevel().getServer());
                var closure = new ConditionCheckpointClosure();
                closure.include(stopped, receipts::frozen);
                var restored = ConditionCheckpointClosure.decode(closure.encode(), java.util.List.of(stopped));
                helper.assertTrue(closure.encode().equals(restored.encode()), "Exact composed lifecycle checkpoint closure");
                helper.assertTrue(receipts.closure(stopped.condition().orElseThrow()).stream()
                        .filter(receipt -> receipt.kind() == ConditionEffectKind.SUCCESSFUL_PROCESSING).count() == 2,
                        "Exactly two processing effects, no duplicate child from reload or checkpoint");
                helper.succeed();
            });
        });
    }

    private static GrinderBlockEntity reload(GameTestHelper helper, GrinderBlockEntity machine) {
        var level = helper.getLevel();
        var position = helper.absolutePos(POS);
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        var restored = (GrinderBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                position, level.getBlockState(position), saved, level.registryAccess());
        helper.assertTrue(restored != null, "Saved Grinder projection recreates its block entity");
        restored.setLevel(level);
        level.removeBlockEntity(position);
        level.setBlockEntity(restored);
        return restored;
    }

    private static void legacyMigration(GameTestHelper helper, AbstractInventoryWorkstationBlockEntity machine) {
        helper.runAtTickTime(2, () -> {
            var current = projection(helper, machine);
            var legacy = DurableWorkstationProjection.active(current.worldIdentity(), current.instanceId(), current.endpointKey(),
                    current.instanceGeneration(), current.instanceAllocationConfigurationIdentity(), current.blockEntityTypeIdentity(),
                    current.instanceRegistryRevision(), current.projectionRevision(), current.inventoryRevision(), current.endpointEffectRevision(),
                    current.lastAppliedJournalSequence(), current.slotCapacityConfigurationIdentity(), current.slots(), current.exactBlockEntityProjection(),
                    current.preparedEndpointEffectIdentity(), current.lastEndpointEffectIdentity(), current.lastEndpointOwnerResultIdentity(),
                    current.processingOperationIdentity(), current.processingOwnerResultIdentity(), current.operatingStateReference());
            var codec = new com.butchercraft.workstation.projection.WorkstationProjectionCodec();
            byte[] historical = codec.freeze(legacy);
            var storage = new com.butchercraft.workstation.projection.WorkstationProjectionStorage(OWNER.projectionRoot(helper.getLevel().getServer()));
            com.butchercraft.persistence.AtomicFilePublication.publishBytes(storage.pathFor(legacy.instanceId()), historical, "legacy condition GameTest fixture");
            machine.acceptConditionProjection(java.util.Optional.empty());
            machine.acceptDurableProjectionReference(legacy.projectionRevision(), legacy.stateDigest());
            helper.assertTrue(OWNER.reconcileLoaded(helper.getLevel(), machine).succeeded(), "Exact historical instance migrates at its owner boundary");
            var successor = projection(helper, machine);
            var state = successor.condition().orElseThrow().state().orElseThrow();
            helper.assertTrue(state.mechanicalLoss() == 0 && state.effectCount() == 0 && state.revision() == 1,
                    "Historical operation is not inferred as wear");
            helper.assertTrue(state.initializationEvidence().legacyProjectionDigest().orElseThrow().equals(legacy.stateDigest()),
                    "Migration binds exact historical projection evidence");
            String instanceHash = com.butchercraft.world.checkpoint.CheckpointSnapshotDigest.sha256(
                    legacy.instanceId().value().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(7);
            String bytesHash = com.butchercraft.world.checkpoint.CheckpointSnapshotDigest.sha256(historical).substring(7);
            try {
                helper.assertTrue(java.util.Arrays.equals(historical, java.nio.file.Files.readAllBytes(storage.rootDirectory()
                        .resolve("legacy_sources").resolve(instanceHash).resolve(bytesHash + ".json"))), "Historical bytes remain immutable");
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            OWNER.reconcileLoaded(helper.getLevel(), machine);
            helper.assertTrue(successor.equals(projection(helper, machine)), "Repeated migration is observational");
            helper.succeed();
        });
    }

    private static void stopAfterOutput(GameTestHelper helper, AbstractInventoryWorkstationBlockEntity machine,
            Runnable stop, long tick) {
        helper.runAtTickTime(tick, () -> {
            if (!machine.inventory().output().isEmpty()) stop.run();
            else {
                helper.assertTrue(tick < 120, "The admitted child must complete within its bounded fixture window");
                stopAfterOutput(helper, machine, stop, tick + 1);
            }
        });
    }

    private static DurableWorkstationProjection projection(GameTestHelper helper, AbstractInventoryWorkstationBlockEntity machine) {
        return OWNER.read(helper.getLevel().getServer(), machine.checkpointInstanceIdentity().orElseThrow()).projection().orElseThrow();
    }

    private static void testPolicy(GameTestHelper helper, AbstractInventoryWorkstationBlockEntity machine, MachineConditionPolicy policy) {
        OWNER.changeConditionPolicy(helper.getLevel().getServer(), machine.checkpointInstanceIdentity().orElseThrow(), policy.identity(),
                SimulationClockService.INSTANCE.clock(helper.getLevel().getServer()).simulationTick());
    }
}
