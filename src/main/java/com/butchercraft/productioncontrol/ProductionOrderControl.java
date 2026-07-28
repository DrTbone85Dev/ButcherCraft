package com.butchercraft.productioncontrol;

import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.machine.grinder.production.GrinderProductionAdapter;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerWorkstationReference;
import com.butchercraft.machine.pattyformer.production.PattyFormerProductionAdapter;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.BusinessRuntimeCalendarService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.production.ProductionDeadline;
import com.butchercraft.world.production.ProductionOperationResult;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPlanMetadata;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionWorkstationChain;
import com.butchercraft.world.production.ProductionWorkstationChainStep;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProductionOrderControl {
    private static final int CANCEL_BUTTON_ID = 0;

    private ProductionOrderControl() {
    }

    public static int cancelButtonId() {
        return CANCEL_BUTTON_ID;
    }

    public static ProductionOrderData dataOrDefault(ItemStack stack) {
        ProductionOrderData data = Objects.requireNonNull(stack, "stack").get(ModDataComponents.PRODUCTION_ORDER.get());
        return data == null ? ProductionOrderData.beefPattiesOrder() : data;
    }

    public static void openOrCreate(Player player, ItemStack stack, net.minecraft.world.InteractionHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(hand, "hand");
        OrderRunResult result = ensureRun(player, stack);
        result.message().ifPresent(message -> player.displayClientMessage(message, false));
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new ProductionOrderMenu(containerId, inventory, serverPlayer, hand),
                    Component.translatable("container.butchercraft.production_order")
            ), buffer -> {});
        }
    }

    public static boolean assignClickedWorkstation(
            Player player,
            ItemStack stack,
            net.minecraft.world.InteractionHand hand,
            BlockPos pos
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(pos, "pos");
        OrderRunResult ensured = ensureRun(player, stack);
        if (!ensured.accepted()) {
            ensured.message().ifPresent(message -> player.displayClientMessage(message, false));
            return true;
        }

        MinecraftServer server = serverFor(player);
        ServerLevel level = serverLevel(player);
        ProductionRunSnapshot run = ensured.run().orElseThrow();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ProductionOperationResult<ProductionRunSnapshot> assigned;
        long tick = simulationTick(server);
        if (blockEntity instanceof GrinderBlockEntity) {
            ProductionWorkstationChainStep step = grinderStep(run);
            assigned = GrinderProductionAdapter.assignChainStep(
                    production(server),
                    run.id(),
                    step.stepIdentity(),
                    new WorkstationTickContext(level, pos),
                    BuiltInDefinitionIds.GRIND_BEEF,
                    tick
            );
        } else if (blockEntity instanceof PattyFormerBlockEntity) {
            ProductionWorkstationChainStep step = pattyFormerStep(run);
            assigned = PattyFormerProductionAdapter.assignChainStep(
                    production(server),
                    run.id(),
                    step.stepIdentity(),
                    new WorkstationTickContext(level, pos),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    tick
            );
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.butchercraft.production_order.unsupported_workstation"
            ), false);
            return true;
        }

        if (assigned.accepted()) {
            refreshStatus(player, dataOrDefault(stack), true);
            player.displayClientMessage(Component.translatable(
                    blockEntity instanceof GrinderBlockEntity
                            ? "message.butchercraft.production_order.grinder_assigned"
                            : "message.butchercraft.production_order.patty_former_assigned"
            ), false);
            return true;
        }
        player.displayClientMessage(Component.translatable(
                "message.butchercraft.production_order.assignment_rejected"
        ), false);
        return true;
    }

    public static boolean cancel(Player player, ItemStack stack) {
        ProductionOrderData data = dataOrDefault(stack);
        if (data.runId().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.butchercraft.production_order.cancel_no_run"
            ), false);
            return false;
        }
        ProductionRunId runId = ProductionRunId.of(data.runId().orElseThrow());
        MinecraftServer server = serverFor(player);
        SimulationSchedulerManager scheduler = SimulationSchedulerService.INSTANCE.managerFor(server);
        ProductionOperationResult<ProductionRunSnapshot> cancelled = production(server).cancel(
                runId,
                scheduler,
                simulationTick(server),
                "Player cancelled Production Order before workstation execution"
        );
        if (cancelled.accepted()) {
            player.displayClientMessage(Component.translatable(
                    "message.butchercraft.production_order.cancelled"
            ), false);
            return true;
        }
        player.displayClientMessage(Component.translatable(
                "message.butchercraft.production_order.cancel_rejected"
        ), false);
        return false;
    }

    public static ProductionOrderStatusSnapshot refreshStatus(
            Player player,
            ProductionOrderData data,
            boolean observe
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(data, "data");
        Optional<BusinessRuntimeObservationSnapshot> business = businessSnapshot(serverFor(player));
        if (data.runId().isEmpty()) {
            return ProductionOrderStatusSnapshot.empty(business);
        }
        ProductionRunId runId = ProductionRunId.of(data.runId().orElseThrow());
        MinecraftServer server = serverFor(player);
        Optional<ProductionRunSnapshot> found = production(server).findRun(runId);
        if (found.isEmpty()) {
            return ProductionOrderStatusSnapshot.stale(business);
        }
        ProductionRunSnapshot run = found.orElseThrow();
        if (observe) {
            run = observeAssignedSteps(server, run, business);
        }
        if (business.isPresent() && run.deadline().isPresent()) {
            ProductionOperationResult<ProductionRunSnapshot> evaluated =
                    production(server).evaluateDeadline(run.id(), business.orElseThrow().calendar());
            if (evaluated.accepted()) {
                run = evaluated.value().orElseThrow();
            }
        }
        return ProductionOrderStatusSnapshot.fromRun(
                run,
                observeGrinder(server, run),
                observePattyFormer(server, run),
                business
        );
    }

    static OrderRunResult ensureRun(Player player, ItemStack stack) {
        ProductionOrderData data = dataOrDefault(stack);
        if (!data.isBeefPattiesTemplate()) {
            return OrderRunResult.rejected(Component.translatable(
                    "message.butchercraft.production_order.unsupported_template"
            ));
        }
        if (data.runId().isPresent()) {
            ProductionRunId existingId = ProductionRunId.of(data.runId().orElseThrow());
            return production(serverFor(player)).findRun(existingId)
                    .map(run -> OrderRunResult.accepted(data, run, Optional.empty()))
                    .orElseGet(() -> OrderRunResult.rejected(Component.translatable(
                            "message.butchercraft.production_order.stale_reference"
                    )));
        }

        MinecraftServer server = serverFor(player);
        ProductionOperationResult<ProductionRunSnapshot> created = createRun(server, player);
        if (!created.accepted()) {
            return OrderRunResult.rejected(Component.translatable(
                    "message.butchercraft.production_order.create_rejected"
            ));
        }
        ProductionRunSnapshot run = created.value().orElseThrow();
        ProductionOrderData updated = data.withRun(run.id());
        stack.set(ModDataComponents.PRODUCTION_ORDER.get(), updated);
        return OrderRunResult.accepted(updated, run, Optional.of(Component.translatable(
                "message.butchercraft.production_order.created"
        )));
    }

    private static ProductionOperationResult<ProductionRunSnapshot> createRun(
            MinecraftServer server,
            Player player
    ) {
        ManualProductionChainBootstrap.ensureProductionProcess(production(server));
        long tick = simulationTick(server);
        ProductionPlanDefinition plan = ProductionPlanDefinition.builder()
                .id(nextPlanId(production(server), player, tick))
                .processId(ManualProductionChainBootstrap.PROCESS_ID)
                .producerActorId(ManualProductionChainBootstrap.PRODUCER_ACTOR)
                .batchCount(1L)
                .inventoryBinding(ManualProductionChainBootstrap.inputBinding())
                .inventoryBinding(ManualProductionChainBootstrap.outputBinding())
                .createdSimulationTick(tick)
                .earliestStartTick(tick)
                .metadata(new ProductionPlanMetadata(
                        Set.of("butchercraft:player_facing", "butchercraft:manual_chain"),
                        Optional.empty(),
                        Optional.of("Created by a player Production Order item")
                ))
                .build();
        ProductionOperationResult<ProductionRunSnapshot> registered = production(server).registerPlan(plan);
        if (!registered.accepted()) {
            return registered;
        }
        ProductionRunSnapshot run = registered.value().orElseThrow();
        ProductionOperationResult<ProductionRunSnapshot> assigned = production(server).assignWorkstationChain(
                run.id(),
                ProductionWorkstationChain.beefPattyChain(run.id()),
                tick
        );
        if (!assigned.accepted()) {
            return assigned;
        }
        return attachDefaultDeadline(server, assigned.value().orElseThrow(), tick);
    }

    private static ProductionOperationResult<ProductionRunSnapshot> attachDefaultDeadline(
            MinecraftServer server,
            ProductionRunSnapshot run,
            long tick
    ) {
        Optional<BusinessRuntimeObservationSnapshot> business = businessSnapshot(server);
        if (business.isEmpty()) {
            return ProductionOperationResult.accepted(run);
        }
        BusinessRuntimeObservationSnapshot snapshot = business.orElseThrow();
        BusinessRuntimeCalendarConfiguration configuration =
                BusinessRuntimeCalendarService.configurationFromConfig(snapshot.calendar().configurationIdentity());
        if (!configuration.productionOrderDeadlinesEnabled()) {
            return ProductionOperationResult.accepted(run);
        }
        ProductionDeadline deadline = ProductionDeadline.target(
                run.id(),
                snapshot.calendar(),
                configuration.identity(),
                configuration.productionOrderDefaultDeadlineMinutes(),
                "butchercraft:production_order/beef_patties"
        );
        return production(server).setDeadline(run.id(), deadline, tick);
    }

    private static ProductionPlanId nextPlanId(
            com.butchercraft.world.production.ProductionManager manager,
            Player player,
            long tick
    ) {
        String playerToken = "p" + player.getUUID().toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
        int sequence = manager.planRegistry().size() + 1;
        ProductionPlanId candidate;
        do {
            candidate = ProductionPlanId.of("butchercraft:manual_beef_patties/"
                    + playerToken + "/t" + tick + "/n" + sequence);
            sequence++;
        } while (manager.planRegistry().find(candidate).isPresent());
        return candidate;
    }

    private static ProductionRunSnapshot observeAssignedSteps(
            MinecraftServer server,
            ProductionRunSnapshot run,
            Optional<BusinessRuntimeObservationSnapshot> business
    ) {
        ProductionRunSnapshot current = run;
        if (current.workstationChain().isEmpty() || current.status().isTerminal()) {
            return current;
        }
        ProductionWorkstationChain chain = current.workstationChain().orElseThrow();
        ProductionWorkstationChainStep grinder = chain.steps().get(0);
        if (grinder.workstationIdentity().isPresent() && !grinder.status().terminal()) {
            Optional<ObservedGrinder> observed = grinderBlock(server, grinder.workstationIdentity().orElseThrow());
            if (observed.isPresent()) {
                ProductionRunSnapshot observedRun = current;
                ProductionOperationResult<ProductionRunSnapshot> result = business
                        .map(snapshot -> GrinderProductionAdapter.observeChainStep(
                                production(server),
                                execution(server),
                                observedRun.id(),
                                grinder.stepIdentity(),
                                observed.orElseThrow().blockEntity(),
                                observed.orElseThrow().context(),
                                BuiltInDefinitionIds.GRIND_BEEF,
                                simulationTick(server),
                                snapshot.calendar()
                        ))
                        .orElseGet(() -> GrinderProductionAdapter.observeChainStep(
                                production(server),
                                execution(server),
                                observedRun.id(),
                                grinder.stepIdentity(),
                                observed.orElseThrow().blockEntity(),
                                observed.orElseThrow().context(),
                                BuiltInDefinitionIds.GRIND_BEEF,
                                simulationTick(server)
                        ));
                if (result.accepted()) {
                    current = result.value().orElseThrow();
                }
            }
        }
        if (current.workstationChain().isEmpty() || current.status().isTerminal()) {
            return current;
        }
        ProductionWorkstationChainStep patty = current.workstationChain().orElseThrow().steps().get(1);
        if (patty.workstationIdentity().isPresent() && !patty.status().terminal()) {
            Optional<ObservedPattyFormer> observed = pattyFormerBlock(server, patty.workstationIdentity().orElseThrow());
            if (observed.isPresent()) {
                ProductionRunSnapshot observedRun = current;
                ProductionOperationResult<ProductionRunSnapshot> result = business
                        .map(snapshot -> PattyFormerProductionAdapter.observeChainStep(
                                production(server),
                                execution(server),
                                observedRun.id(),
                                patty.stepIdentity(),
                                observed.orElseThrow().blockEntity(),
                                observed.orElseThrow().context(),
                                BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                                simulationTick(server),
                                snapshot.calendar()
                        ))
                        .orElseGet(() -> PattyFormerProductionAdapter.observeChainStep(
                                production(server),
                                execution(server),
                                observedRun.id(),
                                patty.stepIdentity(),
                                observed.orElseThrow().blockEntity(),
                                observed.orElseThrow().context(),
                                BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                                simulationTick(server)
                        ));
                if (result.accepted()) {
                    current = result.value().orElseThrow();
                }
            }
        }
        return current;
    }

    private static ProductionOrderStatusSnapshot.WorkstationObservation observeGrinder(
            MinecraftServer server,
            ProductionRunSnapshot run
    ) {
        if (run.workstationChain().isEmpty()) {
            return ProductionOrderStatusSnapshot.WorkstationObservation.unassigned();
        }
        ProductionWorkstationChainStep step = run.workstationChain().orElseThrow().steps().get(0);
        if (step.workstationIdentity().isEmpty()) {
            return ProductionOrderStatusSnapshot.WorkstationObservation.unassigned();
        }
        return grinderBlock(server, step.workstationIdentity().orElseThrow())
                .map(observed -> workstationObservation(observed.blockEntity()))
                .orElseGet(ProductionOrderStatusSnapshot.WorkstationObservation::missingWorkstation);
    }

    private static ProductionOrderStatusSnapshot.WorkstationObservation observePattyFormer(
            MinecraftServer server,
            ProductionRunSnapshot run
    ) {
        if (run.workstationChain().isEmpty()) {
            return ProductionOrderStatusSnapshot.WorkstationObservation.unassigned();
        }
        ProductionWorkstationChainStep step = run.workstationChain().orElseThrow().steps().get(1);
        if (step.workstationIdentity().isEmpty()) {
            return ProductionOrderStatusSnapshot.WorkstationObservation.unassigned();
        }
        return pattyFormerBlock(server, step.workstationIdentity().orElseThrow())
                .map(observed -> workstationObservation(observed.blockEntity()))
                .orElseGet(ProductionOrderStatusSnapshot.WorkstationObservation::missingWorkstation);
    }

    private static ProductionOrderStatusSnapshot.WorkstationObservation workstationObservation(
            com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity blockEntity
    ) {
        int elapsed = blockEntity.menuData().get(1);
        int total = blockEntity.menuData().get(2);
        WorkstationState state = blockEntity.workstationState();
        int progress = total <= 0
                ? state == WorkstationState.COMPLETE ? 100 : 0
                : Math.min(100, elapsed * 100 / total);
        return new ProductionOrderStatusSnapshot.WorkstationObservation(
                false,
                progress,
                state,
                blockEntity.lastFailure().map(com.butchercraft.workstation.WorkstationFailure::code)
        );
    }

    private static ProductionWorkstationChainStep grinderStep(ProductionRunSnapshot run) {
        return run.workstationChain().orElseThrow().steps().get(0);
    }

    private static ProductionWorkstationChainStep pattyFormerStep(ProductionRunSnapshot run) {
        return run.workstationChain().orElseThrow().steps().get(1);
    }

    private static Optional<ObservedGrinder> grinderBlock(MinecraftServer server, String identity) {
        return GrinderWorkstationReference.parse(identity).flatMap(reference -> {
            ServerLevel level = server.getLevel(reference.dimensionKey());
            if (level == null) {
                return Optional.empty();
            }
            BlockEntity blockEntity = level.getBlockEntity(reference.blockPos());
            if (blockEntity instanceof GrinderBlockEntity grinder) {
                return Optional.of(new ObservedGrinder(grinder, new WorkstationTickContext(level, reference.blockPos())));
            }
            return Optional.empty();
        });
    }

    private static Optional<ObservedPattyFormer> pattyFormerBlock(MinecraftServer server, String identity) {
        return PattyFormerWorkstationReference.parse(identity).flatMap(reference -> {
            ServerLevel level = server.getLevel(reference.dimensionKey());
            if (level == null) {
                return Optional.empty();
            }
            BlockEntity blockEntity = level.getBlockEntity(reference.blockPos());
            if (blockEntity instanceof PattyFormerBlockEntity pattyFormer) {
                return Optional.of(new ObservedPattyFormer(
                        pattyFormer,
                        new WorkstationTickContext(level, reference.blockPos())
                ));
            }
            return Optional.empty();
        });
    }

    private static com.butchercraft.world.production.ProductionManager production(MinecraftServer server) {
        return ProductionService.INSTANCE.managerFor(server);
    }

    private static Optional<BusinessRuntimeObservationSnapshot> businessSnapshot(MinecraftServer server) {
        return BusinessRuntimeCalendarService.INSTANCE.currentSnapshot(server);
    }

    private static ExecutionManager execution(MinecraftServer server) {
        return ExecutionService.INSTANCE.managerFor(server);
    }

    private static long simulationTick(MinecraftServer server) {
        return SimulationClockService.INSTANCE.clock(server).simulationTick();
    }

    private static MinecraftServer serverFor(Player player) {
        MinecraftServer server = player.level().getServer();
        return Objects.requireNonNull(server, "server");
    }

    private static ServerLevel serverLevel(Player player) {
        if (player.level() instanceof ServerLevel level) {
            return level;
        }
        throw new IllegalStateException("Production Order control requires a server level");
    }

    private record ObservedGrinder(
            GrinderBlockEntity blockEntity,
            WorkstationTickContext context
    ) {
    }

    private record ObservedPattyFormer(
            PattyFormerBlockEntity blockEntity,
            WorkstationTickContext context
    ) {
    }

    record OrderRunResult(
            boolean accepted,
            ProductionOrderData data,
            Optional<ProductionRunSnapshot> run,
            Optional<Component> message
    ) {
        static OrderRunResult accepted(
                ProductionOrderData data,
                ProductionRunSnapshot run,
                Optional<Component> message
        ) {
            return new OrderRunResult(true, data, Optional.of(run), message);
        }

        static OrderRunResult rejected(Component message) {
            return new OrderRunResult(
                    false,
                    ProductionOrderData.beefPattiesOrder(),
                    Optional.empty(),
                    Optional.of(message)
            );
        }
    }
}
