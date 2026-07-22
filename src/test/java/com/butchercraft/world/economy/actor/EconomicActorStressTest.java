package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.GRAIN;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.goods;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomicActorStressTest {
    @Test
    void oneHundredThousandActorsAndTenThousandRelationshipsLoadDeterministically() {
        List<EconomicActorDefinition> definitions = IntStream.range(0, 100_000)
                .mapToObj(index -> new EconomicActorDefinition(
                        ActorId.of("stress:actor_" + index),
                        "Stress Actor " + index,
                        ActorType.PRODUCER,
                        BuiltInIndustryCatalog.MANUFACTURING,
                        Set.of(ActorCapability.PRODUCE),
                        index < 10_000
                                ? List.of(ActorRelationship.of(GRAIN, GoodRole.OUTPUT))
                                : List.of(),
                        EconomicActorSchema.CURRENT_VERSION
                ))
                .toList();

        EconomicActorRegistry first = EconomicActorRegistry.of(
                definitions,
                goods(),
                BuiltInIndustryCatalog.all()
        );
        EconomicActorRegistry second = EconomicActorRegistry.of(
                definitions.reversed(),
                goods(),
                BuiltInIndustryCatalog.all()
        );

        assertEquals(100_000, first.size());
        assertEquals(10_000, first.relationshipCount());
        assertEquals(first.definitions(), second.definitions());
        assertEquals(first.findByGood(GRAIN), second.findByGood(GRAIN));
        assertEquals(10_000, first.findByGood(GRAIN).size());
        assertEquals(100_000, first.findByCapability(ActorCapability.PRODUCE).size());
    }
}
