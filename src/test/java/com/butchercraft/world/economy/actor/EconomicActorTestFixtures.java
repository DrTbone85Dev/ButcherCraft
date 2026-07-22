package com.butchercraft.world.economy.actor;

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

final class EconomicActorTestFixtures {
    static final GoodId GRAIN = GoodId.of("test:grain");
    static final GoodId BREAD = GoodId.of("test:bread");

    private EconomicActorTestFixtures() {
    }

    static GoodRegistry goods() {
        return GoodRegistry.of(
                List.of(commodity(GRAIN, "Grain"), commodity(BREAD, "Bread")),
                List.of(),
                BuiltInIndustryCatalog.all()
        );
    }

    static EconomicActorDefinition producer(String id) {
        return EconomicActorDefinition.builder()
                .id("test:" + id)
                .displayName(displayName(id))
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .capability(ActorCapability.SELL)
                .relationship(ActorRelationship.of(GRAIN, GoodRole.OUTPUT))
                .build();
    }

    static EconomicActorDefinition consumer(String id, ActorId dependency) {
        return EconomicActorDefinition.builder()
                .id("test:" + id)
                .displayName(displayName(id))
                .actorType(ActorType.CONSUMER)
                .industryId(BuiltInIndustryCatalog.RESTAURANTS)
                .capability(ActorCapability.CONSUME)
                .capability(ActorCapability.BUY)
                .relationship(ActorRelationship.dependingOn(GRAIN, GoodRole.CONSUMED, dependency))
                .build();
    }

    static EconomicActorRegistry registry() {
        EconomicActorDefinition farm = producer("farm");
        EconomicActorDefinition bakery = EconomicActorDefinition.builder()
                .id("test:bakery")
                .displayName("Bakery")
                .actorType(ActorType.PROCESSOR)
                .industryId(BuiltInIndustryCatalog.RESTAURANTS)
                .capability(ActorCapability.TRANSFORM)
                .capability(ActorCapability.BUY)
                .capability(ActorCapability.SELL)
                .relationship(ActorRelationship.dependingOn(GRAIN, GoodRole.INPUT, farm.id()))
                .relationship(ActorRelationship.of(BREAD, GoodRole.OUTPUT))
                .build();
        EconomicActorDefinition warehouse = EconomicActorDefinition.builder()
                .id("test:warehouse")
                .displayName("Warehouse")
                .actorType(ActorType.STORAGE)
                .industryId(BuiltInIndustryCatalog.TRANSPORTATION)
                .capability(ActorCapability.STORE)
                .relationship(ActorRelationship.supportingIndustries(
                        BREAD,
                        GoodRole.STORED,
                        List.of(BuiltInIndustryCatalog.RETAIL, BuiltInIndustryCatalog.RESTAURANTS)
                ))
                .build();
        return EconomicActorRegistry.of(
                List.of(warehouse, bakery, farm),
                goods(),
                BuiltInIndustryCatalog.all()
        );
    }

    private static CommodityDefinition commodity(GoodId id, String displayName) {
        return CommodityDefinition.builder()
                .id(id)
                .displayName(displayName)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.BULK)
                .commodityType(CommodityType.AGRICULTURAL)
                .build();
    }

    private static String displayName(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
    }
}
