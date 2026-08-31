package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationEndpointRuntimeService;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.workstation.projection.WorkstationProjectionReadCode;
import com.butchercraft.workstation.projection.WorkstationProjectionReconciliationCode;
import com.butchercraft.workstation.projection.WorkstationProjectionCodec;
import com.butchercraft.workstation.projection.WorkstationProjectionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.Arrays;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DurableWorkstationProjectionGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos MACHINE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos SOURCE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos DESTINATION_POS = new BlockPos(3, 1, 2);

    private DurableWorkstationProjectionGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void placedGrinderCreatesDurableProjection(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            DurableWorkstationProjection projection = projection(helper, grinder);
            helper.assertTrue(projection.slots().size() == grinder.inventory().totalSlotCount(),
                    "Placed Grinder publishes every ordered slot");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void placedPattyFormerCreatesDurableProjection(GameTestHelper helper) {
        PattyFormerBlockEntity machine = place(helper, MACHINE_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState(),
                PattyFormerBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(projection(helper, machine).endpointKey().workstationTypeIdentity()
                    .equals("butchercraft:patty_former"), "Patty Former projection binds exact type");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void placedCuttingTableCreatesDurableProjection(GameTestHelper helper) {
        CuttingTableBlockEntity machine = place(helper, MACHINE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState(),
                CuttingTableBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(projection(helper, machine).slots().size() == 3,
                    "Cutting Table projection preserves all three slots");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void loadedProjectionReconciliationIsStableWhenEqual(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            long revision = projection(helper, grinder).projectionRevision();
            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(helper.getLevel(), grinder);
            helper.assertTrue(result.code() == WorkstationProjectionReconciliationCode.EQUAL,
                    "Equal loaded and durable Workstation state reconciles without replacement");
            helper.assertTrue(projection(helper, grinder).projectionRevision() == revision,
                    "Equality reconciliation does not create revision drift");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void coherentLoadedLegacyWorkstationBootstrapsProjection(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            GrinderBlockEntity legacy = replaceAsLegacyFixture(helper, grinder);
            helper.runAtTickTime(4, () -> {
                DurableWorkstationProjection durable = projection(helper, legacy);
                helper.assertTrue(durable.instanceId().equals(legacy.checkpointInstanceIdentity().orElseThrow())
                                && durable.projectionRevision() > 0
                                && durable.projectionRevision() == legacy.durableProjectionRevision()
                                && durable.slots().size() == legacy.inventory().totalSlotCount(),
                        "Coherent loaded legacy Workstation bootstraps an exact instance-local durable projection");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void missingCurrentProjectionRequiresRecovery(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var frozen = DurableWorkstationProjectionService.INSTANCE.freezeForCheckpoint(
                    helper.getLevel().getServer(), grinder.checkpointInstanceIdentity().orElseThrow());
            deleteProjection(helper, grinder);
            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(helper.getLevel(), grinder);
            helper.assertTrue(result.code() == WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                    "A Workstation that references missing durable state is not reclassified as legacy");
            restoreFrozenProjection(helper, frozen.instanceId(), frozen.frozenBytes());
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unresolvedLegacyEndpointEffectBlocksBootstrap(GameTestHelper helper) {
        CuttingTableBlockEntity source = place(helper, SOURCE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState(),
                CuttingTableBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            source.inventory().setOutputInternal(1, count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 4));
            var endpoint = StackAwareWorkstationEndpointRuntimeService.INSTANCE;
            var observation = endpoint.observeWithdrawal(helper.getLevel(), helper.absolutePos(SOURCE_POS), 1)
                    .observation().orElseThrow();
            var preparation = endpoint.prepare(
                    helper.getLevel(), helper.absolutePos(SOURCE_POS),
                    "butchercraft:gametest/r3a_unresolved_legacy", observation).preparation().orElseThrow();
            var frozen = DurableWorkstationProjectionService.INSTANCE.freezeForCheckpoint(
                    helper.getLevel().getServer(), source.checkpointInstanceIdentity().orElseThrow());
            deleteProjection(helper, source);

            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(helper.getLevel(), source);
            helper.assertTrue(result.code() == WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                    "Unresolved endpoint evidence prevents legacy projection fabrication");
            restoreFrozenProjection(helper, frozen.instanceId(), frozen.frozenBytes());
            var cancellation = endpoint.cancelPrepared(
                    helper.getLevel(), helper.absolutePos(SOURCE_POS), preparation);
            helper.assertTrue(cancellation.succeeded(),
                    "Prepared endpoint fault fixture cancels before test teardown: " + cancellation.detail());
            helper.setBlock(SOURCE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void grinderInventoryMutationAdvancesExactProjection(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            long before = projection(helper, grinder).projectionRevision();
            grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 12));
            DurableWorkstationProjection after = projection(helper, grinder);
            helper.assertTrue(after.projectionRevision() > before
                            && after.slots().getFirst().exactStack().orElseThrow().count() == 12,
                    "Grinder inventory mutation advances exact durable state");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pattyFormerInventoryMutationAdvancesExactProjection(GameTestHelper helper) {
        PattyFormerBlockEntity machine = place(helper, MACHINE_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState(),
                PattyFormerBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            machine.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 21));
            helper.assertTrue(projection(helper, machine).slots().getFirst()
                            .exactStack().orElseThrow().count() == 21,
                    "Patty Former durable projection matches stack-aware input");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cuttingTableInventoryMutationAdvancesExactProjection(GameTestHelper helper) {
        CuttingTableBlockEntity machine = place(helper, MACHINE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState(),
                CuttingTableBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            machine.inventory().setOutputInternal(1, count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 32));
            helper.assertTrue(projection(helper, machine).slots().get(machine.trimOutputSlot())
                            .exactStack().orElseThrow().count() == 32,
                    "Cutting Table durable projection matches trim output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void projectionRemainsReadableWithoutLoadedBlockEntity(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var identity = grinder.checkpointInstanceIdentity().orElseThrow();
            helper.getLevel().removeBlockEntity(helper.absolutePos(MACHINE_POS));
            var read = DurableWorkstationProjectionService.INSTANCE.read(helper.getLevel().getServer(), identity);
            helper.assertTrue(read.code() == WorkstationProjectionReadCode.AVAILABLE,
                    "Projection lookup remains available without a loaded block entity");
            grinder.clearRemoved();
            helper.getLevel().setBlockEntity(grinder);
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void loadedMissingPhysicalBlockEntityIsIdentityConflict(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var identity = grinder.checkpointInstanceIdentity().orElseThrow();
            helper.getLevel().removeBlockEntity(helper.absolutePos(MACHINE_POS));
            var read = DurableWorkstationProjectionService.INSTANCE
                    .readWithLoadedValidation(helper.getLevel().getServer(), identity);
            helper.assertTrue(read.code() == WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                    "Active durable state never recreates a missing loaded physical Workstation");
            grinder.clearRemoved();
            helper.getLevel().setBlockEntity(grinder);
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void durableNewerProjectionReconcilesLoadedVanillaNbt(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            CompoundTag staleNbt = grinder.saveWithFullMetadata(level.registryAccess());
            grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 9));
            BlockState state = level.getBlockState(helper.absolutePos(MACHINE_POS));
            BlockEntity loaded = BlockEntity.loadStatic(
                    helper.absolutePos(MACHINE_POS), state, staleNbt, level.registryAccess());
            helper.assertTrue(loaded instanceof GrinderBlockEntity, "Stale fixture reloads as Grinder");
            GrinderBlockEntity stale = (GrinderBlockEntity) loaded;
            stale.setLevel(level);
            level.removeBlockEntity(helper.absolutePos(MACHINE_POS));
            level.setBlockEntity(stale);
            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(level, stale);
            helper.assertTrue(result.succeeded() && stale.inventory().input().getCount() == 9,
                    "Newer durable projection reconciles stale loaded block-entity NBT");
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void ambiguousNewerBlockEntityStateRequiresRecovery(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            DurableWorkstationProjection stale = projection(helper, grinder);
            grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 3));
            var coherent = DurableWorkstationProjectionService.INSTANCE.freezeForCheckpoint(
                    helper.getLevel().getServer(), grinder.checkpointInstanceIdentity().orElseThrow());
            overwriteProjectionForCrashFixture(helper, stale);

            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(helper.getLevel(), grinder);
            helper.assertTrue(result.code() == WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                    "Unproven newer block-entity state fails closed");
            helper.assertTrue(grinder.inventory().input().getCount() == 3,
                    "Ambiguous state is not overwritten before recovery authorization");
            restoreFrozenProjection(helper, coherent.instanceId(), coherent.frozenBytes());
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void endpointOwnerEvidenceRepairsInterruptedProjectionPublication(GameTestHelper helper) {
        CuttingTableBlockEntity source = place(helper, SOURCE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState(),
                CuttingTableBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            source.inventory().setOutputInternal(1, count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 5));
            DurableWorkstationProjection stale = projection(helper, source);
            var endpoint = StackAwareWorkstationEndpointRuntimeService.INSTANCE;
            var observation = endpoint.observeWithdrawal(level, helper.absolutePos(SOURCE_POS), 1)
                    .observation().orElseThrow();
            var preparation = endpoint.prepare(
                    level, helper.absolutePos(SOURCE_POS),
                    "butchercraft:gametest/r3a_projection_repair", observation).preparation().orElseThrow();
            helper.assertTrue(endpoint.commit(level, helper.absolutePos(SOURCE_POS), preparation).succeeded(),
                    "Schema-2 endpoint withdrawal commits");
            overwriteProjectionForCrashFixture(helper, stale);
            var result = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(level, source);
            helper.assertTrue(result.code()
                            == WorkstationProjectionReconciliationCode.OWNER_EVIDENCE_REPAIRED_DURABLE_PROJECTION,
                    "Immutable endpoint result repairs interrupted projection publication");
            helper.assertTrue(projection(helper, source).slots().get(source.trimOutputSlot())
                            .exactStack().orElseThrow().count() == 4,
                    "Projection repair observes exact post-withdrawal remainder");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void processingOwnerEvidenceRepairsInterruptedProjectionPublication(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            grinder.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            DurableWorkstationProjection beforeOperation = projection(helper, grinder);
            requestOperation(helper, MACHINE_POS);
            helper.runAtTickTime(110, () -> {
                helper.assertTrue(grinder.inventory().output().is(ModItems.GROUND_BEEF.get()),
                        "Processing owner effect completed before projection fault injection");
                overwriteProjectionForCrashFixture(helper, beforeOperation);
                var result = DurableWorkstationProjectionService.INSTANCE
                        .reconcileLoaded(helper.getLevel(), grinder);
                helper.assertTrue(result.code()
                                == WorkstationProjectionReconciliationCode.OWNER_EVIDENCE_REPAIRED_DURABLE_PROJECTION,
                        "Immutable processing owner result repairs interrupted projection publication");
                helper.assertTrue(projection(helper, grinder).slots().get(1).exactStack().isPresent(),
                        "Projection repair preserves the one already-produced output");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void destinationDepositAdvancesGrinderProjection(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, DESTINATION_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            ServerLevel level = helper.getLevel();
            var endpoint = StackAwareWorkstationEndpointRuntimeService.INSTANCE;
            ItemStack trim = ModItems.BEEF_TRIM.get().getDefaultInstance();
            var observation = endpoint.observeDeposit(level, helper.absolutePos(DESTINATION_POS), trim)
                    .observation().orElseThrow();
            var preparation = endpoint.prepare(
                    level, helper.absolutePos(DESTINATION_POS),
                    "butchercraft:gametest/r3a_projection_deposit", observation).preparation().orElseThrow();
            helper.assertTrue(endpoint.commit(level, helper.absolutePos(DESTINATION_POS), preparation).succeeded(),
                    "Schema-2 endpoint deposit commits");
            helper.assertTrue(projection(helper, grinder).slots().getFirst().exactStack().orElseThrow().count() == 1,
                    "Grinder durable projection records exact deposited Beef Trim");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void retirementPublishesTombstone(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var identity = grinder.checkpointInstanceIdentity().orElseThrow();
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            var read = DurableWorkstationProjectionService.INSTANCE.read(helper.getLevel().getServer(), identity);
            helper.assertTrue(read.code() == WorkstationProjectionReadCode.RETIRED,
                    "Removed Workstation retains durable retirement tombstone");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void replacementDoesNotInheritPriorProjection(GameTestHelper helper) {
        GrinderBlockEntity first = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var firstIdentity = first.checkpointInstanceIdentity().orElseThrow();
            helper.setBlock(MACHINE_POS, Blocks.AIR.defaultBlockState());
            GrinderBlockEntity replacement = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                    GrinderBlockEntity.class);
            helper.runAtTickTime(4, () -> {
                var replacementIdentity = replacement.checkpointInstanceIdentity().orElseThrow();
                helper.assertTrue(!replacementIdentity.equals(firstIdentity),
                        "Replacement Workstation receives a new Instance Identity");
                DurableWorkstationProjection replacementProjection = projection(helper, replacement);
                helper.assertTrue(replacementProjection.instanceId().equals(replacementIdentity)
                                && replacementProjection.projectionRevision() > 0,
                        "Replacement Workstation begins an independent projection revision history");
                helper.assertTrue(DurableWorkstationProjectionService.INSTANCE
                                .read(helper.getLevel().getServer(), firstIdentity).code()
                                == WorkstationProjectionReadCode.RETIRED,
                        "Prior Workstation projection remains distinguishable as a tombstone");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void grinderProcessingPublishesTerminalProjection(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            grinder.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
            requestOperation(helper, MACHINE_POS);
        });
        helper.runAtTickTime(110, () -> {
            DurableWorkstationProjection projection = projection(helper, grinder);
            helper.assertTrue(projection.slots().getFirst().exactStack().isEmpty()
                            && projection.slots().get(1).exactStack().orElseThrow().itemIdentity()
                            .equals("butchercraft:ground_beef_test"),
                    "Grinder terminal inventory is durable without rerunning recipe");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void pattyFormerProcessingPublishesTerminalProjection(GameTestHelper helper) {
        PattyFormerBlockEntity machine = place(helper, MACHINE_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState(),
                PattyFormerBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            machine.inventory().setInputInternal(ModItems.GROUND_BEEF.get().getDefaultInstance());
            requestOperation(helper, MACHINE_POS);
        });
        helper.runAtTickTime(120, () -> {
            DurableWorkstationProjection projection = projection(helper, machine);
            helper.assertTrue(projection.slots().getFirst().exactStack().isEmpty()
                            && projection.slots().get(1).exactStack().orElseThrow().itemIdentity()
                            .equals("butchercraft:beef_patties"),
                    "Patty Former terminal inventory is durable without duplicate product");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void frozenCheckpointCandidateCannotBeMutatedByCaller(GameTestHelper helper) {
        GrinderBlockEntity grinder = place(helper, MACHINE_POS, ModBlocks.GRINDER.get().defaultBlockState(),
                GrinderBlockEntity.class);
        helper.runAtTickTime(2, () -> {
            var identity = grinder.checkpointInstanceIdentity().orElseThrow();
            var frozen = DurableWorkstationProjectionService.INSTANCE
                    .freezeForCheckpoint(helper.getLevel().getServer(), identity);
            byte[] changed = frozen.frozenBytes();
            changed[0] ^= 0x11;
            helper.assertTrue(!Arrays.equals(changed, frozen.frozenBytes()),
                    "Checkpoint-read candidate returns defensive immutable bytes");
            helper.succeed();
        });
    }

    private static DurableWorkstationProjection projection(
            GameTestHelper helper,
            AbstractInventoryWorkstationBlockEntity workstation
    ) {
        var identity = workstation.checkpointInstanceIdentity().orElseThrow();
        var read = DurableWorkstationProjectionService.INSTANCE.read(helper.getLevel().getServer(), identity);
        helper.assertTrue(read.code() == WorkstationProjectionReadCode.AVAILABLE,
                "Expected available durable Workstation projection, got " + read.code() + ": " + read.detail());
        return read.projection().orElseThrow();
    }

    private static void requestOperation(GameTestHelper helper, BlockPos position) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setShiftKeyDown(true);
        helper.useBlock(position, player);
    }

    private static ItemStack count(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }

    private static void deleteProjection(
            GameTestHelper helper,
            AbstractInventoryWorkstationBlockEntity workstation
    ) {
        var storage = new WorkstationProjectionStorage(
                DurableWorkstationProjectionService.projectionRoot(helper.getLevel().getServer()));
        try {
            Files.delete(storage.pathFor(workstation.checkpointInstanceIdentity().orElseThrow()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to create loaded legacy projection fixture", exception);
        }
    }

    private static GrinderBlockEntity replaceAsLegacyFixture(
            GameTestHelper helper,
            GrinderBlockEntity workstation
    ) {
        ServerLevel level = helper.getLevel();
        CompoundTag legacyNbt = workstation.saveWithFullMetadata(level.registryAccess());
        legacyNbt.remove("DurableProjectionReference");
        deleteProjection(helper, workstation);
        BlockState state = level.getBlockState(helper.absolutePos(MACHINE_POS));
        BlockEntity loaded = BlockEntity.loadStatic(
                helper.absolutePos(MACHINE_POS), state, legacyNbt, level.registryAccess());
        helper.assertTrue(loaded instanceof GrinderBlockEntity, "Legacy fixture reloads as Grinder");
        GrinderBlockEntity legacy = (GrinderBlockEntity) loaded;
        legacy.setLevel(level);
        level.removeBlockEntity(helper.absolutePos(MACHINE_POS));
        level.setBlockEntity(legacy);
        return legacy;
    }

    private static void overwriteProjectionForCrashFixture(
            GameTestHelper helper,
            DurableWorkstationProjection projection
    ) {
        var storage = new WorkstationProjectionStorage(
                DurableWorkstationProjectionService.projectionRoot(helper.getLevel().getServer()));
        AtomicFilePublication.publishBytes(
                storage.pathFor(projection.instanceId()),
                new WorkstationProjectionCodec().freeze(projection),
                "R3A interrupted projection publication GameTest fixture"
        );
    }

    private static void restoreFrozenProjection(
            GameTestHelper helper,
            WorkstationInstanceId instanceId,
            byte[] frozenBytes
    ) {
        var storage = new WorkstationProjectionStorage(
                DurableWorkstationProjectionService.projectionRoot(helper.getLevel().getServer()));
        AtomicFilePublication.publishBytes(
                storage.pathFor(instanceId), frozenBytes, "R3A GameTest fault-fixture cleanup");
    }

    private static <T extends BlockEntity> T place(
            GameTestHelper helper,
            BlockPos position,
            BlockState state,
            Class<T> type
    ) {
        helper.setBlock(position, state);
        BlockEntity blockEntity = helper.getBlockEntity(position);
        helper.assertTrue(type.isInstance(blockEntity), "Expected " + type.getSimpleName());
        return type.cast(blockEntity);
    }
}
