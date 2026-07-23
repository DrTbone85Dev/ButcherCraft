package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.ActorRelationship;
import com.butchercraft.world.economy.actor.ActorType;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.actor.GoodRole;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.CommodityDefinition;
import com.butchercraft.world.goods.CommodityType;
import com.butchercraft.world.goods.EconomicFlag;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.Stackability;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.TransportRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public final class InventoryTestFixtures {
    public static final GoodId GRAIN = GoodId.of("test:grain");
    public static final GoodId BEEF = GoodId.of("test:beef");
    public static final GoodId BOX = GoodId.of("test:box");
    public static final ActorId WAREHOUSE_ACTOR = ActorId.of("test:warehouse_actor");
    public static final ActorId FARM_ACTOR = ActorId.of("test:farm_actor");
    public static final StorageNodeId DISTRIBUTION_CENTER = StorageNodeId.of("test:distribution_center");
    public static final StorageNodeId WAREHOUSE = StorageNodeId.of("test:warehouse");
    public static final StorageNodeId COOLER = StorageNodeId.of("test:cooler");
    public static final InventoryId GRAIN_INVENTORY = InventoryId.of("test:grain_inventory");
    public static final InventoryId BEEF_INVENTORY = InventoryId.of("test:beef_inventory");

    private InventoryTestFixtures() {
    }

    public static GoodRegistry goods() {
        return GoodRegistry.of(
                List.of(
                        commodity(GRAIN, "Grain", UnitOfMeasure.BUSHEL, StorageRequirement.AMBIENT),
                        commodity(BEEF, "Beef", UnitOfMeasure.POUND, StorageRequirement.REFRIGERATED),
                        commodity(BOX, "Box", UnitOfMeasure.EACH, StorageRequirement.AMBIENT)
                ),
                List.of(),
                BuiltInIndustryCatalog.all()
        );
    }

    public static EconomicActorRegistry actors() {
        GoodRegistry goods = goods();
        EconomicActorDefinition warehouse = EconomicActorDefinition.builder()
                .id(WAREHOUSE_ACTOR)
                .displayName("Warehouse Operator")
                .actorType(ActorType.STORAGE)
                .industryId(BuiltInIndustryCatalog.TRANSPORTATION)
                .capability(ActorCapability.STORE)
                .relationship(ActorRelationship.of(GRAIN, GoodRole.STORED))
                .relationship(ActorRelationship.of(BEEF, GoodRole.STORED))
                .relationship(ActorRelationship.of(BOX, GoodRole.STORED))
                .build();
        EconomicActorDefinition farm = EconomicActorDefinition.builder()
                .id(FARM_ACTOR)
                .displayName("Farm")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .relationship(ActorRelationship.of(GRAIN, GoodRole.OUTPUT))
                .build();
        return EconomicActorRegistry.of(List.of(warehouse, farm), goods, BuiltInIndustryCatalog.all());
    }

    public static List<StorageNode> storageNodes() {
        StorageNode distributionCenter = StorageNode.builder()
                .id(DISTRIBUTION_CENTER)
                .displayName("Distribution Center")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.builder()
                        .maximumVolume(100, UnitOfMeasure.BUSHEL)
                        .maximumWeight(1_000, UnitOfMeasure.POUND)
                        .maximumUnits(1_000)
                        .maximumDistinctGoods(10)
                        .build())
                .build();
        StorageNode warehouse = StorageNode.builder()
                .id(WAREHOUSE)
                .displayName("Warehouse")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.builder()
                        .maximumVolume(80, UnitOfMeasure.BUSHEL)
                        .maximumUnits(500)
                        .maximumDistinctGoods(5)
                        .build())
                .parentNodeId(DISTRIBUTION_CENTER)
                .build();
        StorageNode cooler = StorageNode.builder()
                .id(COOLER)
                .displayName("Retail Cooler")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .capacity(StorageCapacity.builder()
                        .maximumWeight(500, UnitOfMeasure.POUND)
                        .maximumDistinctGoods(5)
                        .build())
                .parentNodeId(DISTRIBUTION_CENTER)
                .build();
        return List.of(cooler, warehouse, distributionCenter);
    }

    public static List<InventoryContainer> containers() {
        InventoryContainer grain = InventoryContainer.builder()
                .id(GRAIN_INVENTORY)
                .displayName("Grain Inventory")
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(WAREHOUSE)
                .inventoryType(InventoryType.WAREHOUSE)
                .capacity(StorageCapacity.builder()
                        .maximumVolume(50, UnitOfMeasure.BUSHEL)
                        .maximumDistinctGoods(2)
                        .build())
                .build();
        InventoryContainer beef = InventoryContainer.builder()
                .id(BEEF_INVENTORY)
                .displayName("Beef Inventory")
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(COOLER)
                .inventoryType(InventoryType.RETAIL)
                .capacity(StorageCapacity.builder()
                        .maximumWeight(100, UnitOfMeasure.POUND)
                        .maximumDistinctGoods(2)
                        .build())
                .build();
        return List.of(grain, beef);
    }

    public static InventoryRegistry registry() {
        EconomicActorRegistry actors = actors();
        return InventoryRegistry.of(containers(), storageNodes(), actors.goodRegistry(), actors);
    }

    public static InventoryManager manager() {
        InventoryEntryMetadata grainMetadata = new InventoryEntryMetadata(
                Optional.of("LOT-001"),
                OptionalLong.of(500L),
                OptionalInt.of(9_500),
                Optional.of(FARM_ACTOR)
        );
        InventoryRuntime grain = new InventoryRuntime(
                GRAIN_INVENTORY,
                InventoryStatus.ACTIVE,
                List.of(new InventoryEntry(GRAIN, 10, UnitOfMeasure.BUSHEL, grainMetadata)),
                25L,
                InventorySchema.CURRENT_VERSION
        );
        InventoryRuntime beef = new InventoryRuntime(
                BEEF_INVENTORY,
                InventoryStatus.ACTIVE,
                List.of(new InventoryEntry(BEEF, 20, UnitOfMeasure.POUND)),
                25L,
                InventorySchema.CURRENT_VERSION
        );
        return new InventoryManager(registry(), List.of(beef, grain));
    }

    private static CommodityDefinition commodity(
            GoodId id,
            String displayName,
            UnitOfMeasure unit,
            StorageRequirement storageRequirement
    ) {
        return CommodityDefinition.builder()
                .id(id)
                .displayName(displayName)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .unitOfMeasure(unit)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(storageRequirement)
                .transportRequirement(TransportRequirement.STANDARD)
                .commodityType(CommodityType.AGRICULTURAL)
                .build();
    }
}
