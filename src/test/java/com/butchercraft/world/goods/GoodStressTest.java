package com.butchercraft.world.goods;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoodStressTest {
    @Test
    void oneHundredThousandDefinitionsLoadDeterministicallyWithoutDuplicates() {
        List<CommodityDefinition> definitions = IntStream.range(0, 100_000)
                .mapToObj(index -> new CommodityDefinition(
                        GoodId.of("stress:good_" + index),
                        "Stress Good " + index,
                        BuiltInIndustryCatalog.MANUFACTURING,
                        UnitOfMeasure.EACH,
                        Stackability.STACKABLE,
                        Set.of(EconomicFlag.TRADEABLE),
                        StorageRequirement.AMBIENT,
                        TransportRequirement.STANDARD,
                        Set.of(),
                        GoodSchema.CURRENT_VERSION,
                        CommodityType.RAW_MATERIAL
                ))
                .toList();
        List<GoodTransformation> transformations = IntStream.range(0, 99)
                .mapToObj(index -> new GoodTransformation(
                        GoodId.of("stress:good_" + (index * 1_000)),
                        GoodId.of("stress:good_" + ((index + 1) * 1_000)),
                        GoodYieldRatio.identity(),
                        BuiltInIndustryCatalog.MANUFACTURING
                ))
                .toList();

        GoodRegistry first = GoodRegistry.of(definitions, transformations, BuiltInIndustryCatalog.all());
        GoodRegistry second = GoodRegistry.of(
                definitions.reversed(),
                transformations.reversed(),
                BuiltInIndustryCatalog.all()
        );

        assertEquals(100_000, first.size());
        assertEquals(first.definitions(), second.definitions());
        assertEquals(99, first.transformationCount());
        assertEquals(first.transformations(), second.transformations());
        Set<GoodId> ids = new HashSet<>(first.stream().map(GoodDefinition::id).toList());
        assertEquals(100_000, ids.size());
        assertEquals(100_000, first.findByIndustry(BuiltInIndustryCatalog.MANUFACTURING).size());
    }
}
