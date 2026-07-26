package com.butchercraft.productioncontrol;

import com.butchercraft.world.EconomicActorService;
import com.butchercraft.world.GoodService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.ActorRuntimeStatus;
import com.butchercraft.world.economy.actor.ActorType;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.EconomicFlag;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.ProductDefinition;
import com.butchercraft.world.goods.ProductStage;
import com.butchercraft.world.goods.Stackability;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.TransportRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
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
import com.butchercraft.world.production.ProductionMetadata;
import com.butchercraft.world.production.ProductionOutputDefinition;
import com.butchercraft.world.production.ProductionOutputRole;
import com.butchercraft.world.production.ProductionProcessDefinition;
import com.butchercraft.world.production.ProductionProcessId;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Optional;
import java.util.Set;

public final class ManualProductionChainBootstrap {
    public static final ManualProductionChainBootstrap INSTANCE = new ManualProductionChainBootstrap();

    public static final GoodId BEEF_TRIM = GoodId.of("butchercraft:beef_trim");
    public static final GoodId GROUND_BEEF = GoodId.of("butchercraft:ground_beef");
    public static final GoodId BEEF_PATTIES = GoodId.of("butchercraft:beef_patties");
    public static final ActorId PRODUCER_ACTOR = ActorId.of("butchercraft:manual_production_player");
    public static final StorageNodeId STORAGE_NODE =
            StorageNodeId.of("butchercraft:manual_production_node");
    public static final InventoryId INPUT_INVENTORY =
            InventoryId.of("butchercraft:manual_production_input");
    public static final InventoryId OUTPUT_INVENTORY =
            InventoryId.of("butchercraft:manual_production_output");
    public static final ProductionProcessId PROCESS_ID =
            ProductionProcessId.of("butchercraft:manual_beef_patties_chain");
    public static final ProductionLineId INPUT_LINE = ProductionLineId.of("beef_trim");
    public static final ProductionLineId OUTPUT_LINE = ProductionLineId.of("beef_patties");

    private ManualProductionChainBootstrap() {
    }

    public void ensureGoods(ServerStartedEvent event) {
        GoodManager manager = GoodService.INSTANCE.managerFor(event.getServer());
        ensureGood(manager, BEEF_TRIM, "Beef Trim", ProductStage.RAW);
        ensureGood(manager, GROUND_BEEF, "Ground Beef", ProductStage.INTERMEDIATE);
        ensureGood(manager, BEEF_PATTIES, "Beef Patties", ProductStage.FINISHED);
    }

    public void ensureActor(ServerStartedEvent event) {
        EconomicActorManager manager = EconomicActorService.INSTANCE.managerFor(event.getServer());
        if (manager.find(PRODUCER_ACTOR).isEmpty()) {
            manager.register(EconomicActorDefinition.builder()
                    .id(PRODUCER_ACTOR)
                    .displayName("Manual Production Player")
                    .actorType(ActorType.PROCESSOR)
                    .industryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                    .capability(ActorCapability.TRANSFORM)
                    .capability(ActorCapability.PRODUCE)
                    .capability(ActorCapability.STORE)
                    .build());
        }
        manager.requireRuntime(PRODUCER_ACTOR).transitionTo(ActorRuntimeStatus.OPERATIONAL, 0L);
    }

    public void ensureInventory(ServerStartedEvent event) {
        InventoryManager manager = InventoryService.INSTANCE.managerFor(event.getServer());
        if (manager.findStorageNode(STORAGE_NODE).isEmpty()) {
            manager.registerStorageNode(StorageNode.builder()
                    .id(STORAGE_NODE)
                    .displayName("Manual Production Reference Storage")
                    .storageRequirement(StorageRequirement.REFRIGERATED)
                    .capacity(StorageCapacity.unlimited())
                    .build());
        }
        if (manager.find(INPUT_INVENTORY).isEmpty()) {
            manager.registerContainer(InventoryContainer.builder()
                    .id(INPUT_INVENTORY)
                    .displayName("Manual Production Input Reference")
                    .ownerActorId(PRODUCER_ACTOR)
                    .storageNodeId(STORAGE_NODE)
                    .inventoryType(InventoryType.PROCESSING)
                    .capacity(StorageCapacity.unlimited())
                    .build());
        }
        if (manager.find(OUTPUT_INVENTORY).isEmpty()) {
            manager.registerContainer(InventoryContainer.builder()
                    .id(OUTPUT_INVENTORY)
                    .displayName("Manual Production Output Reference")
                    .ownerActorId(PRODUCER_ACTOR)
                    .storageNodeId(STORAGE_NODE)
                    .inventoryType(InventoryType.PROCESSING)
                    .capacity(StorageCapacity.unlimited())
                    .build());
        }
    }

    public void ensureProduction(ServerStartedEvent event) {
        ensureProductionProcess(ProductionService.INSTANCE.managerFor(event.getServer()));
    }

    public static void ensureProductionProcess(ProductionManager manager) {
        if (manager.processRegistry().find(PROCESS_ID).isPresent()) {
            return;
        }
        var result = manager.registerProcess(beefPattiesProcess());
        if (!result.accepted()) {
            throw new IllegalStateException("Manual Beef Patties Production process was rejected: "
                    + result.failures().getFirst().message());
        }
    }

    public static ProductionInventoryBinding inputBinding() {
        return new ProductionInventoryBinding(
                INPUT_LINE,
                ProductionBindingDirection.INPUT,
                INPUT_INVENTORY,
                BEEF_TRIM,
                UnitOfMeasure.EACH
        );
    }

    public static ProductionInventoryBinding outputBinding() {
        return new ProductionInventoryBinding(
                OUTPUT_LINE,
                ProductionBindingDirection.OUTPUT,
                OUTPUT_INVENTORY,
                BEEF_PATTIES,
                UnitOfMeasure.EACH
        );
    }

    private static ProductionProcessDefinition beefPattiesProcess() {
        return ProductionProcessDefinition.builder()
                .id(PROCESS_ID)
                .displayName("Manual Beef Patties Chain")
                .owningIndustryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                .requiredActorCapability(ActorCapability.TRANSFORM)
                .input(new ProductionInputDefinition(
                        INPUT_LINE,
                        BEEF_TRIM,
                        GoodQuantity.of(1L),
                        UnitOfMeasure.EACH,
                        ProductionInputRole.PRIMARY,
                        ConsumptionPolicy.CONSUME_FULL,
                        Optional.empty(),
                        ProductionInventoryConstraint.any(),
                        ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        OUTPUT_LINE,
                        BEEF_PATTIES,
                        GoodQuantity.of(1L),
                        UnitOfMeasure.EACH,
                        ProductionOutputRole.PRIMARY,
                        GoodYieldRatio.identity(),
                        Optional.empty(),
                        ProductionInventoryConstraint.any(),
                        ProductionLineMetadata.empty()
                ))
                .duration(ProductionDuration.ofTicks(120L))
                .batchPolicy(ProductionBatchPolicy.wholeBatches(1L, 1L, 1L))
                .metadata(new ProductionMetadata(
                        Set.of("butchercraft:manual_chain"),
                        Optional.of("Fixed player-facing Beef Patties workstation chain")
                ))
                .build();
    }

    private static void ensureGood(GoodManager manager, GoodId id, String displayName, ProductStage stage) {
        if (manager.contains(id)) {
            return;
        }
        manager.register(ProductDefinition.builder()
                .id(id)
                .displayName(displayName)
                .industryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                .sourceIndustryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .economicFlag(EconomicFlag.PERISHABLE)
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .transportRequirement(TransportRequirement.REFRIGERATED)
                .transformationStage(stage)
                .build());
    }
}
