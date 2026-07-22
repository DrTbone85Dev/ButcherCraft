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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryStressTest {
    @Test
    void oneHundredThousandContainersAndOneMillionEntriesValidateDeterministically() {
        List<GoodId> goodIds = IntStream.range(0, 10)
                .mapToObj(index -> GoodId.of("stress:good_" + index))
                .toList();
        GoodRegistry goods = GoodRegistry.of(
                goodIds.stream().map(InventoryStressTest::good).toList(),
                List.of(),
                BuiltInIndustryCatalog.all()
        );
        ActorId actorId = ActorId.of("stress:warehouse_actor");
        EconomicActorDefinition.Builder actorBuilder = EconomicActorDefinition.builder()
                .id(actorId)
                .displayName("Stress Warehouse Actor")
                .actorType(ActorType.STORAGE)
                .industryId(BuiltInIndustryCatalog.MANUFACTURING)
                .capability(ActorCapability.STORE);
        goodIds.forEach(goodId -> actorBuilder.relationship(ActorRelationship.of(goodId, GoodRole.STORED)));
        EconomicActorRegistry actors = EconomicActorRegistry.of(
                List.of(actorBuilder.build()),
                goods,
                BuiltInIndustryCatalog.all()
        );
        StorageNode storageNode = StorageNode.builder()
                .id("stress:warehouse")
                .displayName("Stress Warehouse")
                .storageRequirement(StorageRequirement.AMBIENT)
                .build();
        List<InventoryContainer> containers = IntStream.range(0, 100_000)
                .mapToObj(index -> InventoryContainer.builder()
                        .id("stress:inventory_" + index)
                        .displayName("Stress Inventory " + index)
                        .ownerActorId(actorId)
                        .storageNodeId(storageNode.id())
                        .inventoryType(InventoryType.WAREHOUSE)
                        .build())
                .toList();
        InventoryRegistry first = InventoryRegistry.of(containers, List.of(storageNode), goods, actors);
        InventoryRegistry second = InventoryRegistry.of(
                containers.reversed(),
                List.of(storageNode),
                goods,
                actors
        );
        List<InventoryEntry> entries = goodIds.stream()
                .map(goodId -> new InventoryEntry(goodId, 1L, UnitOfMeasure.EACH))
                .toList();
        List<InventoryRuntime> runtimes = containers.stream()
                .map(container -> new InventoryRuntime(
                        container.id(),
                        InventoryStatus.ACTIVE,
                        entries,
                        0L,
                        InventorySchema.CURRENT_VERSION
                ))
                .toList();

        InventoryManager manager = new InventoryManager(first, runtimes);

        assertEquals(100_000, manager.registry().size());
        assertEquals(1_000_000L, manager.entryCount());
        assertEquals(first.containers(), second.containers());
        assertEquals(100_000L, manager.quantityOwnedBy(actorId, goodIds.getFirst()));
        assertEquals(100_000L, manager.quantityAt(storageNode.id(), goodIds.getFirst(), true));
        assertEquals(100_000, manager.inventoriesOwnedBy(actorId).size());
        manager.validate();
    }

    private static CommodityDefinition good(GoodId goodId) {
        return new CommodityDefinition(
                goodId,
                "Stress Good " + goodId.value(),
                BuiltInIndustryCatalog.MANUFACTURING,
                UnitOfMeasure.EACH,
                Stackability.STACKABLE,
                Set.of(EconomicFlag.TRADEABLE),
                StorageRequirement.AMBIENT,
                TransportRequirement.STANDARD,
                Set.of(),
                com.butchercraft.world.goods.GoodSchema.CURRENT_VERSION,
                CommodityType.RAW_MATERIAL
        );
    }
}
