package com.butchercraft.world.planning;

import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.ActorRuntimeStatus;
import com.butchercraft.world.economy.actor.ActorType;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.EconomicOrderDefinition;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.economy.order.OrderLineDefinition;
import com.butchercraft.world.economy.order.OrderLineId;
import com.butchercraft.world.economy.order.OrderLineRole;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.economy.order.OrderPriority;
import com.butchercraft.world.economy.order.OrderType;
import com.butchercraft.world.economy.order.SubstitutionPolicy;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.CommodityDefinition;
import com.butchercraft.world.goods.CommodityType;
import com.butchercraft.world.goods.EconomicFlag;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.GoodTransformation;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.IndustryId;
import com.butchercraft.world.goods.Stackability;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.TransportRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryRegistry;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.inventory.InventorySchema;
import com.butchercraft.world.inventory.InventoryStatus;
import com.butchercraft.world.inventory.InventoryType;
import com.butchercraft.world.inventory.StorageCapacity;
import com.butchercraft.world.inventory.StorageNode;
import com.butchercraft.world.inventory.StorageNodeId;
import com.butchercraft.world.production.ConsumptionPolicy;
import com.butchercraft.world.production.ProductionBatchPolicy;
import com.butchercraft.world.production.ProductionBindingDirection;
import com.butchercraft.world.production.ProductionDuration;
import com.butchercraft.world.production.ProductionInputDefinition;
import com.butchercraft.world.production.ProductionInputRole;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionInventoryConstraint;
import com.butchercraft.world.production.ProductionLineId;
import com.butchercraft.world.production.ProductionLineMetadata;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionOutputDefinition;
import com.butchercraft.world.production.ProductionOutputRole;
import com.butchercraft.world.production.ProductionProcessDefinition;
import com.butchercraft.world.production.ProductionProcessId;
import com.butchercraft.world.production.ProductionTransformationReference;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.workforce.WorkforceManager;
import com.butchercraft.world.workforce.WorkforceRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class PlanningTestFixtures {
    static final IndustryId INDUSTRY = BuiltInIndustryCatalog.MANUFACTURING;
    static final GoodId INPUT = GoodId.of("test:planning_input");
    static final GoodId OUTPUT = GoodId.of("test:planning_output");
    static final ActorId PRODUCER = ActorId.of("test:planning_producer");
    static final ActorId BUYER = ActorId.of("test:planning_buyer");
    static final InventoryId INPUT_INVENTORY = InventoryId.of("test:planning_input_inventory");
    static final InventoryId OUTPUT_INVENTORY = InventoryId.of("test:planning_output_inventory");
    static final ProductionProcessId PROCESS = ProductionProcessId.of("test:planning_process");
    static final ProductionLineId INPUT_LINE = ProductionLineId.of("input");
    static final ProductionLineId OUTPUT_LINE = ProductionLineId.of("output");
    static final OrderId ORDER = OrderId.of("test:planning_order");
    static final OrderLineId ORDER_LINE = OrderLineId.of("test:planning_order_line");

    private PlanningTestFixtures() {
    }

    static Context context() {
        return context(Long.MAX_VALUE);
    }

    static Context context(long outputCapacity) {
        GoodRegistry goods = GoodRegistry.of(
                List.of(good(INPUT, "Planning Input"), good(OUTPUT, "Planning Output")),
                List.of(new GoodTransformation(INPUT, OUTPUT, GoodYieldRatio.identity(), INDUSTRY)),
                BuiltInIndustryCatalog.all()
        );
        EconomicActorDefinition producer = EconomicActorDefinition.builder()
                .id(PRODUCER).displayName("Planning Producer").actorType(ActorType.PROCESSOR)
                .industryId(INDUSTRY).capability(ActorCapability.TRANSFORM)
                .capability(ActorCapability.PRODUCE).capability(ActorCapability.STORE)
                .capability(ActorCapability.SELL).build();
        EconomicActorDefinition buyer = EconomicActorDefinition.builder()
                .id(BUYER).displayName("Planning Buyer").actorType(ActorType.CONSUMER)
                .industryId(INDUSTRY).capability(ActorCapability.BUY)
                .capability(ActorCapability.STORE).build();
        EconomicActorRegistry actors = EconomicActorRegistry.of(
                List.of(producer, buyer), goods, BuiltInIndustryCatalog.all()
        );
        EconomicActorManager actorManager = new EconomicActorManager(actors);
        actorManager.requireRuntime(PRODUCER).transitionTo(ActorRuntimeStatus.OPERATIONAL, 0L);
        actorManager.requireRuntime(BUYER).transitionTo(ActorRuntimeStatus.OPERATIONAL, 0L);

        StorageNodeId nodeId = StorageNodeId.of("test:planning_node");
        StorageNode node = StorageNode.builder().id(nodeId).displayName("Planning Node")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.unlimited()).build();
        InventoryContainer input = container(
                INPUT_INVENTORY, "Planning Input Inventory", nodeId, InventoryType.WAREHOUSE
        );
        InventoryContainer output = container(
                OUTPUT_INVENTORY, "Planning Output Inventory", nodeId, InventoryType.PROCESSING,
                outputCapacity == Long.MAX_VALUE
                        ? StorageCapacity.unlimited()
                        : StorageCapacity.builder().maximumUnits(outputCapacity).build()
        );
        InventoryRegistry inventories = InventoryRegistry.of(
                List.of(input, output), List.of(node), goods, actors
        );
        InventoryManager inventoryManager = new InventoryManager(inventories, List.of(
                runtime(INPUT_INVENTORY, List.of(new InventoryEntry(INPUT, 20L, UnitOfMeasure.EACH))),
                runtime(OUTPUT_INVENTORY, List.of())
        ));
        TransactionManager transactionManager = new TransactionManager(inventoryManager);
        ContractManager contractManager = new ContractManager(actors);
        OrderManager orderManager = new OrderManager(
                actors, inventories, transactionManager, contractManager
        );
        BusinessRuntimeManager businessManager = new BusinessRuntimeManager(
                BusinessRuntimeRegistry.empty(), SimulationConfiguration.standard()
        );
        WorkforceManager workforceManager = new WorkforceManager(WorkforceRegistry.empty());
        com.butchercraft.world.production.ProductionDependencies productionDependencies =
                new com.butchercraft.world.production.ProductionDependencies(
                        new GoodManager(goods), actorManager, businessManager, workforceManager,
                        inventoryManager, transactionManager, orderManager, contractManager
                );
        ProductionManager productionManager = new ProductionManager(productionDependencies);
        if (!productionManager.registerProcess(process()).accepted()) {
            throw new AssertionError("Planning fixture process was rejected");
        }
        SimulationSchedulerManager schedulerManager = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(
                        new ProductionSimulationWorkHandler(productionManager)
                )),
                0L
        );
        PlanningDependencies planningDependencies = new PlanningDependencies(
                new GoodManager(goods), actorManager, businessManager, workforceManager,
                inventoryManager, transactionManager, orderManager, contractManager,
                productionManager, schedulerManager
        );
        return new Context(planningDependencies);
    }

    static EconomicOrderDefinition order(long quantity) {
        return order(GoodQuantity.of(quantity));
    }

    static EconomicOrderDefinition order(GoodQuantity quantity) {
        return EconomicOrderDefinition.builder()
                .id(ORDER).displayName("Planning Order").type(OrderType.PURCHASE)
                .requesterActorId(BUYER).counterpartyActorId(PRODUCER)
                .lines(List.of(OrderLineDefinition.builder()
                        .id(ORDER_LINE).goodId(OUTPUT).requestedQuantity(quantity)
                        .unitOfMeasure(UnitOfMeasure.EACH).role(OrderLineRole.REQUESTED)
                        .substitutionPolicy(SubstitutionPolicy.EXACT_ONLY).build()))
                .createdSimulationTick(10L).requestedFulfillmentTick(50L)
                .latestAcceptableFulfillmentTick(60L).priority(OrderPriority.HIGH).build();
    }

    static void submitAcceptedOrder(Context context, long quantity) {
        submitAcceptedOrder(context, GoodQuantity.of(quantity));
    }

    static void submitAcceptedOrder(Context context, GoodQuantity quantity) {
        if (!context.dependencies().orderManager().submit(order(quantity)).success()) {
            throw new AssertionError("Planning fixture Order was rejected");
        }
        if (!context.dependencies().orderManager().accept(ORDER, 11L).success()) {
            throw new AssertionError("Planning fixture Order was not accepted");
        }
    }

    static ProductionProcessDefinition process() {
        ProductionTransformationReference transformation =
                new ProductionTransformationReference(INPUT, OUTPUT, INDUSTRY);
        return ProductionProcessDefinition.builder()
                .id(PROCESS).displayName("Planning Process").owningIndustryId(INDUSTRY)
                .requiredActorCapability(ActorCapability.TRANSFORM)
                .input(new ProductionInputDefinition(
                        INPUT_LINE, INPUT, GoodQuantity.of(2L), UnitOfMeasure.EACH,
                        ProductionInputRole.PRIMARY, ConsumptionPolicy.CONSUME_FULL,
                        Optional.of(transformation),
                        new ProductionInventoryConstraint(Set.of(InventoryType.WAREHOUSE)),
                        ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        OUTPUT_LINE, OUTPUT, GoodQuantity.of(1L), UnitOfMeasure.EACH,
                        ProductionOutputRole.PRIMARY, GoodYieldRatio.identity(),
                        Optional.of(transformation),
                        new ProductionInventoryConstraint(Set.of(InventoryType.PROCESSING)),
                        ProductionLineMetadata.empty()
                ))
                .transformationReference(transformation)
                .duration(new ProductionDuration(2L, 1L))
                .batchPolicy(ProductionBatchPolicy.wholeBatches(1L, 100L, 1L))
                .build();
    }

    static PlanningManager manager(Context context) {
        return new PlanningManager(
                context.dependencies(), PlanningSelectionPolicy.standard(), PlanningExecutionBudget.standard()
        );
    }

    private static CommodityDefinition good(GoodId id, String displayName) {
        return CommodityDefinition.builder().id(id).displayName(displayName).industryId(INDUSTRY)
                .unitOfMeasure(UnitOfMeasure.EACH).stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE).storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .commodityType(CommodityType.RAW_MATERIAL).build();
    }

    private static InventoryContainer container(
            InventoryId id,
            String displayName,
            StorageNodeId nodeId,
            InventoryType type
    ) {
        return container(id, displayName, nodeId, type, StorageCapacity.unlimited());
    }

    private static InventoryContainer container(
            InventoryId id,
            String displayName,
            StorageNodeId nodeId,
            InventoryType type,
            StorageCapacity capacity
    ) {
        return InventoryContainer.builder().id(id).displayName(displayName).ownerActorId(PRODUCER)
                .storageNodeId(nodeId).inventoryType(type).capacity(capacity).build();
    }

    private static InventoryRuntime runtime(InventoryId id, List<InventoryEntry> entries) {
        return new InventoryRuntime(id, InventoryStatus.ACTIVE, entries, 0L, InventorySchema.CURRENT_VERSION);
    }

    record Context(PlanningDependencies dependencies) {
        ProductionManager productionManager() {
            return dependencies.productionManager();
        }

        SimulationSchedulerManager schedulerManager() {
            return dependencies.schedulerManager();
        }

        InventoryManager inventoryManager() {
            return dependencies.inventoryManager();
        }

        OrderManager orderManager() {
            return dependencies.orderManager();
        }

        TransactionManager transactionManager() {
            return dependencies.transactionManager();
        }
    }
}
