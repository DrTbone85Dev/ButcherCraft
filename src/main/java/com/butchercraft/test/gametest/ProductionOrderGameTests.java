package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerWorkstationReference;
import com.butchercraft.productioncontrol.ProductionOrderControl;
import com.butchercraft.productioncontrol.ProductionOrderData;
import com.butchercraft.productioncontrol.ProductionOrderNextAction;
import com.butchercraft.productioncontrol.ProductionOrderStatusSnapshot;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.BusinessRuntimeService;
import com.butchercraft.world.EconomicActorService;
import com.butchercraft.world.GoodService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.OrderContractService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.TransactionService;
import com.butchercraft.world.WorkforceService;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionDependencies;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionWorkstationChainStep;
import com.butchercraft.world.production.ProductionWorkstationChainStatus;
import com.butchercraft.world.production.persistence.ProductionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProductionOrderGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos GRINDER_POS = new BlockPos(1, 1, 2);
    private static final BlockPos OTHER_GRINDER_POS = new BlockPos(1, 1, 3);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 2);
    private static final BlockPos EMPTY_POS = new BlockPos(2, 1, 2);

    private ProductionOrderGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void defaultOrderItemCarriesTemplateWithoutRun(GameTestHelper helper) {
        ItemStack stack = orderStack();
        ProductionOrderData data = ProductionOrderControl.dataOrDefault(stack);

        helper.assertTrue(data.isBeefPattiesTemplate(), "Order uses the Beef Patties template");
        helper.assertTrue(data.runId().isEmpty(), "New order has no run reference");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void openingOrderCreatesPlayerFacingRun(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());

        useOrder(helper, player);

        ProductionRunSnapshot run = linkedRun(helper, player);
        helper.assertTrue(run.status() == ProductionRunStatus.READY, "Order creates a ready Production run");
        helper.assertTrue(run.workstationChain().isPresent(), "Order creates the fixed workstation chain");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void repeatedOpeningReusesLinkedRun(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());

        useOrder(helper, player);
        ProductionRunId firstRunId = linkedRun(helper, player).id();
        useOrder(helper, player);

        helper.assertTrue(linkedRun(helper, player).id().equals(firstRunId),
                "Reopening the same order observes the existing run");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void statusViewingWithoutRunDoesNotCreateRun(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());

        ProductionOrderStatusSnapshot snapshot = ProductionOrderControl.refreshStatus(
                player,
                ProductionOrderControl.dataOrDefault(player.getItemInHand(InteractionHand.MAIN_HAND)),
                true
        );

        helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.CREATE_RUN,
                "Viewing an unlinked order does not create a run");
        helper.assertTrue(ProductionOrderControl.dataOrDefault(player.getItemInHand(InteractionHand.MAIN_HAND))
                        .runId().isEmpty(),
                "Status refresh does not write a run reference");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void staleRunReferenceIsVisible(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderWithRun("butchercraft:missing_manual_run/run"));

        ProductionOrderStatusSnapshot snapshot = status(helper, player, true);

        helper.assertTrue(snapshot.staleReference(), "Missing run reference is reported as stale");
        helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.STALE_REFERENCE,
                "Stale reference exposes recovery guidance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void clickingGrinderCreatesAndAssignsRun(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);

        clickGrinder(helper, player);

        ProductionWorkstationChainStep grinder = grinderStep(linkedRun(helper, player));
        helper.assertTrue(grinder.workstationIdentity().isPresent(), "Grinder step is assigned");
        helper.assertTrue(grinder.status() == ProductionChainStepStatus.ASSIGNED,
                "Grinder step is assigned but not started");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void duplicateGrinderAssignmentIsIdempotent(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);

        clickGrinder(helper, player);
        ProductionRunSnapshot first = linkedRun(helper, player);
        clickGrinder(helper, player);

        helper.assertTrue(linkedRun(helper, player).equals(first),
                "Duplicate Grinder assignment keeps the authoritative run unchanged");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void conflictingGrinderAssignmentDoesNotOverwriteExistingAssignment(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);
        helper.setBlock(OTHER_GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());

        clickGrinder(helper, player);
        String firstIdentity = grinderStep(linkedRun(helper, player)).workstationIdentity().orElseThrow();
        ModItems.PRODUCTION_ORDER.get().useOnWorkstation(
                player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(),
                helper.absolutePos(OTHER_GRINDER_POS),
                player,
                InteractionHand.MAIN_HAND
        );

        helper.assertTrue(grinderStep(linkedRun(helper, player)).workstationIdentity().orElseThrow().equals(firstIdentity),
                "Conflicting Grinder assignment does not overwrite the original");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void conflictingGrinderReassignmentAfterOperationBeginsIsRejected(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        helper.setBlock(OTHER_GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        clickGrinder(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            ProductionOrderStatusSnapshot processing = status(helper, player, true);
            helper.assertTrue(processing.nextAction() == ProductionOrderNextAction.WAIT_FOR_GRINDER,
                    "Grinder operation has begun");
            String firstIdentity = grinderStep(linkedRun(helper, player)).workstationIdentity().orElseThrow();
            clickOtherGrinder(helper, player);

            helper.assertTrue(grinderStep(linkedRun(helper, player)).workstationIdentity().orElseThrow()
                            .equals(firstIdentity),
                    "Reassignment after processing begins is rejected");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void clickingPattyFormerCanPreassignButStillGuidesGrinderFirst(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placePattyFormer(helper);

        clickPattyFormer(helper, player);

        helper.assertTrue(pattyStep(linkedRun(helper, player)).workstationIdentity().isPresent(),
                "Patty Former step is assigned");
        helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.ASSIGN_GRINDER,
                "Guidance keeps the Grinder as the active first step");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void assigningBothWorkstationsStillGuidesBeefTrimLoading(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);
        placePattyFormer(helper);

        clickPattyFormer(helper, player);
        clickGrinder(helper, player);

        helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.LOAD_BEEF_TRIM,
                "Assigned workstations still guide the player to load Beef Trim first");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void unsupportedBlockClickCreatesNoWorkstationAssignment(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        helper.setBlock(EMPTY_POS, Blocks.STONE.defaultBlockState());

        ModItems.PRODUCTION_ORDER.get().useOnWorkstation(
                player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(),
                helper.absolutePos(EMPTY_POS),
                player,
                InteractionHand.MAIN_HAND
        );

        ProductionRunSnapshot run = linkedRun(helper, player);
        helper.assertTrue(grinderStep(run).workstationIdentity().isEmpty(), "Stone is not assigned as a Grinder");
        helper.assertTrue(pattyStep(run).workstationIdentity().isEmpty(), "Stone is not assigned as a Patty Former");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void cancelBeforeProcessingMarksRunCancelled(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);
        clickGrinder(helper, player);

        boolean cancelled = ProductionOrderControl.cancel(player, player.getItemInHand(InteractionHand.MAIN_HAND));

        helper.assertTrue(cancelled, "Unstarted order cancellation is accepted");
        helper.assertTrue(linkedRun(helper, player).status() == ProductionRunStatus.CANCELLED,
                "Production run is cancelled");
        helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.CANCELLED,
                "Cancelled order displays terminal guidance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void cancelledRunRejectsLaterWorkstationAssignment(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);
        clickGrinder(helper, player);
        ProductionOrderControl.cancel(player, player.getItemInHand(InteractionHand.MAIN_HAND));

        clickGrinder(helper, player);

        helper.assertTrue(linkedRun(helper, player).status() == ProductionRunStatus.CANCELLED,
                "Cancelled order remains cancelled after later clicks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void missingAssignedGrinderIsVisible(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placeGrinder(helper);
        clickGrinder(helper, player);
        helper.getLevel().destroyBlock(helper.absolutePos(GRINDER_POS), false);

        ProductionOrderStatusSnapshot snapshot = status(helper, player, true);

        helper.assertTrue(snapshot.grinderMissing(), "Missing assigned Grinder is visible to the order");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void missingAssignedPattyFormerIsVisible(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        placePattyFormer(helper);
        clickPattyFormer(helper, player);
        helper.getLevel().destroyBlock(helper.absolutePos(PATTY_FORMER_POS), false);

        ProductionOrderStatusSnapshot snapshot = status(helper, player, true);

        helper.assertTrue(snapshot.pattyFormerMissing(), "Missing assigned Patty Former is visible to the order");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void grinderProcessingProgressIsObserved(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        clickGrinder(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.WAIT_FOR_GRINDER,
                    "Order observes active Grinder processing");
            helper.assertTrue(snapshot.grinderProgressPercent() > 0,
                    "Order reports Grinder progress");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void grinderCompletionRequestsPattyFormerAssignment(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        clickGrinder(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.ASSIGN_PATTY_FORMER,
                    "Completed Grinder asks for Patty Former assignment");
            helper.assertTrue(grinderStep(linkedRun(helper, player)).status() == ProductionChainStepStatus.COMPLETE,
                    "Production observes Grinder step completion");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void grinderCompletionWithPattyFormerAssignedRequestsManualTransfer(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.MOVE_GROUND_BEEF,
                    "Completed Grinder asks for manual Ground Beef transfer");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void blockedGrinderOutputPresentsClearOutputGuidance(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        clickGrinder(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> grinder.inventory().setOutputInternal(groundBeef()));
        helper.runAtTickTime(115, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(grinder.lastFailure().orElseThrow().code() == WorkstationFailureCode.OUTPUT_OCCUPIED,
                    "Grinder reports occupied output");
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.CLEAR_GRINDER_OUTPUT,
                    "Order asks the player to clear the Grinder output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void manualTransferGuidanceDoesNotMoveItemsAutomatically(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            status(helper, player, true);
            helper.assertTrue(!grinder.inventory().output().isEmpty(),
                    "Ground Beef remains in Grinder output for player transfer");
            helper.assertTrue(pattyFormer.inventory().input().isEmpty(),
                    "Patty Former input remains empty until player transfer");
            helper.assertTrue(pattyFormer.workstationState() == WorkstationState.IDLE,
                    "Patty Former does not start automatically");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void manualTransferStatusRoundTripsThroughProductionPersistence(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            ProductionRunSnapshot run = linkedRun(helper, player);
            helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.MOVE_GROUND_BEEF,
                    "Order is at the manual transfer step before save");

            ProductionManager loaded = saveAndLoadProduction(helper);
            ProductionRunSnapshot restored = loaded.findRun(run.id()).orElseThrow();
            helper.assertTrue(restored.workstationChain().orElseThrow().status()
                            == ProductionWorkstationChainStatus.AWAITING_MANUAL_TRANSFER,
                    "Manual transfer chain status round-trips through Production persistence");
            helper.assertTrue(grinderStep(restored).status() == ProductionChainStepStatus.COMPLETE,
                    "Completed Grinder step round-trips through Production persistence");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void pattyFormerProcessingProgressIsObserved(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.MOVE_GROUND_BEEF,
                    "Order asks for manual Ground Beef transfer before the player moves items");
            transferGroundBeef(helper, grinder, pattyFormer);
        });
        helper.runAtTickTime(125, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.WAIT_FOR_PATTY_FORMER,
                    "Order observes active Patty Former processing");
            helper.assertTrue(snapshot.pattyFormerProgressPercent() > 0,
                    "Order reports Patty Former progress");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void blockedPattyFormerOutputPresentsClearOutputGuidance(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            transferGroundBeef(helper, grinder, pattyFormer);
            pattyFormer.inventory().setOutputInternal(beefPatties());
        });
        helper.runAtTickTime(230, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            helper.assertTrue(pattyFormer.lastFailure().orElseThrow().code()
                            == WorkstationFailureCode.OUTPUT_OCCUPIED,
                    "Patty Former reports occupied output");
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.CLEAR_PATTY_FORMER_OUTPUT,
                    "Order asks the player to clear the Patty Former output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void fullManualChainGuidesCollectionAndCompletesRun(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.MOVE_GROUND_BEEF,
                    "Order asks for manual Ground Beef transfer before the player moves items");
            transferGroundBeef(helper, grinder, pattyFormer);
        });
        helper.runAtTickTime(230, () -> {
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);
            ProductionRunSnapshot firstObservedRun = linkedRun(helper, player);
            ProductionOrderStatusSnapshot repeatedSnapshot = status(helper, player, true);
            ProductionRunSnapshot secondObservedRun = linkedRun(helper, player);
            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.COLLECT_BEEF_PATTIES,
                    "Completed chain asks player to collect Beef Patties");
            helper.assertTrue(firstObservedRun.status() == ProductionRunStatus.COMPLETED,
                    "Production run completes after both workstation owner results");
            helper.assertTrue(secondObservedRun.equals(firstObservedRun),
                    "Repeated UI observation does not duplicate Production evidence");
            helper.assertTrue(repeatedSnapshot.nextAction() == ProductionOrderNextAction.COLLECT_BEEF_PATTIES,
                    "Repeated UI observation remains read-only");
            helper.assertTrue(!pattyFormer.inventory().output().isEmpty(),
                    "Beef Patties remain in Patty Former output for collection");
            helper.assertTrue(pattyFormer.inventory().output().getCount() == 1,
                    "Repeated UI observation does not duplicate output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void completedRunRemainsCompleteAfterProductionPersistenceReload(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        clickGrinder(helper, player);
        clickPattyFormer(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(115, () -> {
            helper.assertTrue(status(helper, player, true).nextAction() == ProductionOrderNextAction.MOVE_GROUND_BEEF,
                    "Order asks for manual Ground Beef transfer before the player moves items");
            transferGroundBeef(helper, grinder, pattyFormer);
        });
        helper.runAtTickTime(240, () -> {
            status(helper, player, true);
            ProductionRunSnapshot run = linkedRun(helper, player);
            helper.assertTrue(run.status() == ProductionRunStatus.COMPLETED,
                    "Run is complete before save");

            ProductionManager loaded = saveAndLoadProduction(helper);
            ProductionRunSnapshot restored = loaded.findRun(run.id()).orElseThrow();
            helper.assertTrue(restored.status() == ProductionRunStatus.COMPLETED,
                    "Completed run remains complete after Production persistence reload");
            helper.assertTrue(restored.workstationChain().orElseThrow().status()
                            == ProductionWorkstationChainStatus.COMPLETE,
                    "Completed chain remains complete after Production persistence reload");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unknownOutcomePresentsRecoveryRequiredGuidance(GameTestHelper helper) {
        Player player = playerWithOrder(helper, orderStack());
        GrinderBlockEntity grinder = placeGrinder(helper);
        clickGrinder(helper, player);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            ProductionOrderStatusSnapshot running = status(helper, player, true);
            helper.assertTrue(running.nextAction() == ProductionOrderNextAction.WAIT_FOR_GRINDER,
                    "Grinder execution is running before Unknown Outcome publication");
            ProductionRunSnapshot run = linkedRun(helper, player);
            var unknown = ProductionService.INSTANCE.managerFor(helper.getLevel().getServer())
                    .recordWorkstationChainUnknownOutcome(
                            run.id(),
                            grinderStep(run).stepIdentity(),
                            "IM-019 GameTest unknown outcome",
                            run.lastUpdatedSimulationTick()
                    );
            helper.assertTrue(unknown.accepted(), "Production accepts the Unknown Outcome publication");
            ProductionOrderStatusSnapshot snapshot = status(helper, player, true);

            helper.assertTrue(snapshot.nextAction() == ProductionOrderNextAction.UNKNOWN_OUTCOME,
                    "Unknown Outcome asks the player to reopen after recovery");
            helper.assertTrue(linkedRun(helper, player).failureCode()
                            .filter(ProductionFailureCode.WORKSTATION_UNKNOWN_OUTCOME::equals)
                            .isPresent(),
                    "Production owns the Unknown Outcome terminal state");
            helper.succeed();
        });
    }

    private static Player playerWithOrder(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static ItemStack orderStack() {
        return ModItems.PRODUCTION_ORDER.get().getDefaultInstance();
    }

    private static ItemStack orderWithRun(String runId) {
        ItemStack stack = orderStack();
        stack.set(
                ModDataComponents.PRODUCTION_ORDER.get(),
                ProductionOrderData.beefPattiesOrder().withRun(ProductionRunId.of(runId))
        );
        return stack;
    }

    private static void useOrder(GameTestHelper helper, Player player) {
        ModItems.PRODUCTION_ORDER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static void clickGrinder(GameTestHelper helper, Player player) {
        ModItems.PRODUCTION_ORDER.get().useOnWorkstation(
                player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(),
                helper.absolutePos(GRINDER_POS),
                player,
                InteractionHand.MAIN_HAND
        );
    }

    private static void clickOtherGrinder(GameTestHelper helper, Player player) {
        ModItems.PRODUCTION_ORDER.get().useOnWorkstation(
                player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(),
                helper.absolutePos(OTHER_GRINDER_POS),
                player,
                InteractionHand.MAIN_HAND
        );
    }

    private static void clickPattyFormer(GameTestHelper helper, Player player) {
        ModItems.PRODUCTION_ORDER.get().useOnWorkstation(
                player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(),
                helper.absolutePos(PATTY_FORMER_POS),
                player,
                InteractionHand.MAIN_HAND
        );
    }

    private static ProductionOrderStatusSnapshot status(
            GameTestHelper helper,
            Player player,
            boolean observe
    ) {
        return ProductionOrderControl.refreshStatus(
                player,
                ProductionOrderControl.dataOrDefault(player.getItemInHand(InteractionHand.MAIN_HAND)),
                observe
        );
    }

    private static ProductionRunSnapshot linkedRun(GameTestHelper helper, Player player) {
        ProductionOrderData data = ProductionOrderControl.dataOrDefault(player.getItemInHand(InteractionHand.MAIN_HAND));
        ProductionRunId runId = ProductionRunId.of(data.runId().orElseThrow());
        return ProductionService.INSTANCE.managerFor(helper.getLevel().getServer())
                .findRun(runId)
                .orElseThrow();
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        return grinder(helper);
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper) {
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        return pattyFormer(helper);
    }

    private static GrinderBlockEntity grinder(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(GRINDER_POS));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity, "Expected Grinder block entity");
        return (GrinderBlockEntity) blockEntity;
    }

    private static PattyFormerBlockEntity pattyFormer(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(PATTY_FORMER_POS));
        helper.assertTrue(blockEntity instanceof PattyFormerBlockEntity, "Expected Patty Former block entity");
        return (PattyFormerBlockEntity) blockEntity;
    }

    private static void insertBeefTrim(GameTestHelper helper, GrinderBlockEntity grinder) {
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                ModItems.BEEF_TRIM.get().getDefaultInstance(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Beef Trim inserts into Grinder");
    }

    private static void transferGroundBeef(
            GameTestHelper helper,
            GrinderBlockEntity grinder,
            PattyFormerBlockEntity pattyFormer
    ) {
        ItemStack extracted = grinder.inventory().extractItem(WorkstationInventory.OUTPUT_SLOT, 1, false);
        helper.assertTrue(!extracted.isEmpty(), "Player extracts Ground Beef from Grinder output");
        ItemStack remainder = pattyFormer.inventory().insertItem(WorkstationInventory.INPUT_SLOT, extracted, false);
        helper.assertTrue(remainder.isEmpty(), "Player inserts Ground Beef into Patty Former input");
        helper.assertTrue(Objects.equals("butchercraft:ground_beef",
                        extracted.get(ModDataComponents.PRODUCT_DATA.get()).productTypeId()),
                "Transferred item is Ground Beef");
    }

    private static ItemStack groundBeef() {
        return ModItems.GROUND_BEEF.get().getDefaultInstance();
    }

    private static ItemStack beefPatties() {
        return ModItems.BEEF_PATTIES.get().getDefaultInstance();
    }

    private static ProductionManager saveAndLoadProduction(GameTestHelper helper) {
        ProductionStorage storage = productionStorage(helper);
        storage.save(ProductionService.INSTANCE.managerFor(helper.getLevel().getServer()));
        return storage.load();
    }

    private static ProductionStorage productionStorage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ProductionDependencies dependencies = new ProductionDependencies(
                GoodService.INSTANCE.managerFor(server),
                EconomicActorService.INSTANCE.managerFor(server),
                BusinessRuntimeService.INSTANCE.managerFor(server),
                WorkforceService.INSTANCE.managerFor(server),
                InventoryService.INSTANCE.managerFor(server),
                TransactionService.INSTANCE.managerFor(server),
                OrderContractService.INSTANCE.orderManagerFor(server),
                OrderContractService.INSTANCE.contractManagerFor(server)
        );
        return new ProductionStorage(
                ProductionService.processFile(server),
                ProductionService.planFile(server),
                ProductionService.runFile(server),
                dependencies
        );
    }

    private static ProductionWorkstationChainStep grinderStep(ProductionRunSnapshot run) {
        return run.workstationChain().orElseThrow().steps().getFirst();
    }

    private static ProductionWorkstationChainStep pattyStep(ProductionRunSnapshot run) {
        return run.workstationChain().orElseThrow().steps().get(1);
    }

    @SuppressWarnings("unused")
    private static String grinderIdentity(GameTestHelper helper) {
        return GrinderWorkstationReference.of(helper.getLevel(), helper.absolutePos(GRINDER_POS)).identity();
    }

    @SuppressWarnings("unused")
    private static String pattyFormerIdentity(GameTestHelper helper) {
        return PattyFormerWorkstationReference.of(helper.getLevel(), helper.absolutePos(PATTY_FORMER_POS)).identity();
    }
}
