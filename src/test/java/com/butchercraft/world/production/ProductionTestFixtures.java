package com.butchercraft.world.production;

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
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderManager;
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
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.workforce.WorkforceManager;
import com.butchercraft.world.workforce.WorkforceRegistry;

import java.util.List;
import java.util.Optional;

final class ProductionTestFixtures {
    static final IndustryId INDUSTRY = BuiltInIndustryCatalog.MANUFACTURING;
    static final GoodId INPUT = GoodId.of("test:input_a");
    static final GoodId OUTPUT = GoodId.of("test:output_b");
    static final GoodId BYPRODUCT = GoodId.of("test:byproduct_c");
    static final ActorId ACTOR = ActorId.of("test:producer");
    static final InventoryId INPUT_INVENTORY = InventoryId.of("test:input_inventory");
    static final InventoryId OUTPUT_INVENTORY = InventoryId.of("test:output_inventory");
    static final InventoryId BYPRODUCT_INVENTORY = InventoryId.of("test:byproduct_inventory");
    static final ProductionProcessId PROCESS_ID = ProductionProcessId.of("test:generic_process");
    static final ProductionPlanId PLAN_ID = ProductionPlanId.of("test:plan");
    static final ProductionLineId INPUT_LINE = ProductionLineId.of("input");
    static final ProductionLineId OUTPUT_LINE = ProductionLineId.of("output");
    static final ProductionLineId BYPRODUCT_LINE = ProductionLineId.of("byproduct");

    private ProductionTestFixtures() {
    }

    static TestContext context() {
        return context(100L, 0L);
    }

    static TestContext context(long outputCapacity, long initialOutput) {
        GoodRegistry goods = goods();
        EconomicActorDefinition actor = EconomicActorDefinition.builder()
                .id(ACTOR)
                .displayName("Generic Producer")
                .actorType(ActorType.PROCESSOR)
                .industryId(INDUSTRY)
                .capability(ActorCapability.TRANSFORM)
                .capability(ActorCapability.PRODUCE)
                .capability(ActorCapability.STORE)
                .build();
        EconomicActorRegistry actors = EconomicActorRegistry.of(
                List.of(actor), goods, BuiltInIndustryCatalog.all()
        );
        EconomicActorManager actorManager = new EconomicActorManager(actors);
        actorManager.requireRuntime(ACTOR).transitionTo(ActorRuntimeStatus.OPERATIONAL, 0L);

        StorageNodeId nodeId = StorageNodeId.of("test:production_node");
        StorageNode node = StorageNode.builder()
                .id(nodeId)
                .displayName("Production Node")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.unlimited())
                .build();
        InventoryContainer input = container(
                INPUT_INVENTORY, "Input Inventory", nodeId, StorageCapacity.unlimited()
        );
        InventoryContainer output = container(
                OUTPUT_INVENTORY,
                "Output Inventory",
                nodeId,
                StorageCapacity.builder().maximumUnits(outputCapacity).maximumDistinctGoods(10).build()
        );
        InventoryContainer byproduct = container(
                BYPRODUCT_INVENTORY, "Byproduct Inventory", nodeId, StorageCapacity.unlimited()
        );
        InventoryRegistry inventories = InventoryRegistry.of(
                List.of(input, output, byproduct), List.of(node), goods, actors
        );
        InventoryManager inventoryManager = new InventoryManager(inventories, List.of(
                runtime(INPUT_INVENTORY, List.of(new InventoryEntry(INPUT, 20L, UnitOfMeasure.EACH))),
                runtime(OUTPUT_INVENTORY, initialOutput == 0L ? List.of()
                        : List.of(new InventoryEntry(OUTPUT, initialOutput, UnitOfMeasure.EACH))),
                runtime(BYPRODUCT_INVENTORY, List.of())
        ));
        TransactionManager transactionManager = new TransactionManager(inventoryManager);
        ContractManager contractManager = new ContractManager(actors);
        OrderManager orderManager = new OrderManager(
                actors, inventories, transactionManager, contractManager
        );
        ProductionDependencies dependencies = new ProductionDependencies(
                new GoodManager(goods),
                actorManager,
                new BusinessRuntimeManager(BusinessRuntimeRegistry.empty(), SimulationConfiguration.standard()),
                new WorkforceManager(WorkforceRegistry.empty()),
                inventoryManager,
                transactionManager,
                orderManager,
                contractManager
        );
        return new TestContext(dependencies, new ProductionManager(dependencies));
    }

    static ProductionProcessDefinition process() {
        ProductionTransformationReference primary = new ProductionTransformationReference(INPUT, OUTPUT, INDUSTRY);
        ProductionTransformationReference secondary =
                new ProductionTransformationReference(INPUT, BYPRODUCT, INDUSTRY);
        return ProductionProcessDefinition.builder()
                .id(PROCESS_ID)
                .displayName("Generic A to B")
                .owningIndustryId(INDUSTRY)
                .requiredActorCapability(ActorCapability.TRANSFORM)
                .input(new ProductionInputDefinition(
                        INPUT_LINE, INPUT, GoodQuantity.of(2L), UnitOfMeasure.EACH,
                        ProductionInputRole.PRIMARY, ConsumptionPolicy.CONSUME_FULL,
                        Optional.of(primary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        OUTPUT_LINE, OUTPUT, GoodQuantity.of(1L), UnitOfMeasure.EACH,
                        ProductionOutputRole.PRIMARY, GoodYieldRatio.identity(),
                        Optional.of(primary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        BYPRODUCT_LINE, BYPRODUCT, GoodQuantity.of(1L), UnitOfMeasure.EACH,
                        ProductionOutputRole.BYPRODUCT, GoodYieldRatio.identity(),
                        Optional.of(secondary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .transformationReference(primary)
                .transformationReference(secondary)
                .duration(new ProductionDuration(2L, 2L))
                .batchPolicy(ProductionBatchPolicy.wholeBatches(1L, 100L, 1L))
                .build();
    }

    static ProductionPlanDefinition plan() {
        return ProductionPlanDefinition.builder()
                .id(PLAN_ID)
                .processId(PROCESS_ID)
                .producerActorId(ACTOR)
                .batchCount(1L)
                .inventoryBinding(binding(
                        INPUT_LINE, ProductionBindingDirection.INPUT, INPUT_INVENTORY, INPUT
                ))
                .inventoryBinding(binding(
                        OUTPUT_LINE, ProductionBindingDirection.OUTPUT, OUTPUT_INVENTORY, OUTPUT
                ))
                .inventoryBinding(binding(
                        BYPRODUCT_LINE, ProductionBindingDirection.OUTPUT, BYPRODUCT_INVENTORY, BYPRODUCT
                ))
                .createdSimulationTick(0L)
                .earliestStartTick(1L)
                .priority(ProductionPriority.NORMAL)
                .build();
    }

    static ProductionManager populatedManager(TestContext context) {
        ProductionManager manager = context.manager();
        if (!manager.registerProcess(process()).accepted()) throw new AssertionError("Fixture process rejected");
        if (!manager.registerPlan(plan()).accepted()) throw new AssertionError("Fixture plan rejected");
        return manager;
    }

    private static GoodRegistry goods() {
        return GoodRegistry.of(
                List.of(good(INPUT, "Input A"), good(OUTPUT, "Output B"), good(BYPRODUCT, "Byproduct C")),
                List.of(
                        new GoodTransformation(INPUT, OUTPUT, GoodYieldRatio.identity(), INDUSTRY),
                        new GoodTransformation(INPUT, BYPRODUCT, GoodYieldRatio.identity(), INDUSTRY)
                ),
                BuiltInIndustryCatalog.all()
        );
    }

    private static CommodityDefinition good(GoodId id, String name) {
        return CommodityDefinition.builder()
                .id(id)
                .displayName(name)
                .industryId(INDUSTRY)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .commodityType(CommodityType.RAW_MATERIAL)
                .build();
    }

    private static InventoryContainer container(
            InventoryId id,
            String name,
            StorageNodeId nodeId,
            StorageCapacity capacity
    ) {
        return InventoryContainer.builder()
                .id(id)
                .displayName(name)
                .ownerActorId(ACTOR)
                .storageNodeId(nodeId)
                .inventoryType(InventoryType.PROCESSING)
                .capacity(capacity)
                .build();
    }

    private static InventoryRuntime runtime(InventoryId id, List<InventoryEntry> entries) {
        return new InventoryRuntime(
                id, InventoryStatus.ACTIVE, entries, 0L, InventorySchema.CURRENT_VERSION
        );
    }

    private static ProductionInventoryBinding binding(
            ProductionLineId lineId,
            ProductionBindingDirection direction,
            InventoryId inventoryId,
            GoodId goodId
    ) {
        return new ProductionInventoryBinding(
                lineId, direction, inventoryId, goodId, UnitOfMeasure.EACH
        );
    }

    record TestContext(ProductionDependencies dependencies, ProductionManager manager) {
    }
}
