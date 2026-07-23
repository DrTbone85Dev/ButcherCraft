package com.butchercraft.world.goods;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.butchercraft.world.goods.GoodTestFixtures.commodity;
import static com.butchercraft.world.goods.GoodTestFixtures.product;
import static com.butchercraft.world.goods.GoodTestFixtures.registry;
import static com.butchercraft.world.goods.GoodTestFixtures.transformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodRegistryTest {
    @Test
    void registrySupportsDeterministicLookupAndQueries() {
        GoodRegistry registry = registry();

        assertEquals(3, registry.size());
        assertEquals(1, registry.transformationCount());
        assertTrue(registry.contains(GoodId.of("test:ground_beef")));
        assertEquals("Ground Beef", registry.find(GoodId.of("test:ground_beef")).orElseThrow().displayName());
        assertEquals(1, registry.findByCategory(GoodCategory.COMMODITY).size());
        assertEquals(2, registry.findByCategory(GoodCategory.PRODUCT).size());
        assertEquals(2, registry.findByIndustry(BuiltInIndustryCatalog.MEAT_PROCESSING).size());
        assertEquals(1, registry.transformationsFrom(GoodId.of("test:beef_carcass")).size());
        assertEquals(1, registry.transformationsTo(GoodId.of("test:ground_beef")).size());
        assertEquals(1, registry.transformationsForIndustry(BuiltInIndustryCatalog.MEAT_PROCESSING).size());
        assertEquals(List.of("test:beef_carcass", "test:ground_beef", "test:water"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void inputOrderDoesNotChangeRegistryOrder() {
        GoodRegistry expected = registry();
        List<GoodDefinition> reversedDefinitions = new ArrayList<>(expected.definitions());
        List<GoodTransformation> reversedTransformations = new ArrayList<>(expected.transformations());
        GoodRegistry actual = GoodRegistry.of(
                reversedDefinitions.reversed(),
                reversedTransformations.reversed(),
                BuiltInIndustryCatalog.all()
        );

        assertEquals(expected.definitions(), actual.definitions());
        assertEquals(expected.transformations(), actual.transformations());
    }

    @Test
    void registryRejectsDuplicateGoodIdsAndTransformations() {
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(commodity("duplicate"), product("duplicate")),
                List.of(),
                BuiltInIndustryCatalog.all()
        ));

        GoodTransformation first = transformation("input", "output");
        GoodTransformation second = new GoodTransformation(
                first.inputGoodId(),
                first.outputGoodId(),
                GoodYieldRatio.identity(),
                first.owningIndustryId()
        );
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("input"), product("output")),
                List.of(first, second),
                BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryRejectsUnknownDefinitionSourceAndTransformationIndustries() {
        IndustryId unknown = IndustryId.of("example:unknown");
        CommodityDefinition unknownOwner = CommodityDefinition.builder()
                .id("test:unknown_owner")
                .displayName("Unknown Owner")
                .industryId(unknown)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .commodityType(CommodityType.OTHER)
                .build();
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(unknownOwner),
                List.of(),
                BuiltInIndustryCatalog.all()
        ));

        ProductDefinition unknownSource = ProductDefinition.builder()
                .id("test:unknown_source")
                .displayName("Unknown Source")
                .industryId(BuiltInIndustryCatalog.MANUFACTURING)
                .sourceIndustryId(unknown)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .transformationStage(ProductStage.FINISHED)
                .build();
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(unknownSource),
                List.of(),
                BuiltInIndustryCatalog.all()
        ));

        GoodTransformation unknownIndustry = new GoodTransformation(
                GoodId.of("test:input"),
                GoodId.of("test:output"),
                GoodYieldRatio.identity(),
                unknown
        );
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("input"), product("output")),
                List.of(unknownIndustry),
                BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryRejectsUnknownTransformationInputsAndOutputs() {
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("output")),
                List.of(transformation("missing", "output")),
                BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("input")),
                List.of(transformation("input", "missing")),
                BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryRejectsSelfAndMultiNodeTransformationCycles() {
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("self")),
                List.of(transformation("self", "self")),
                BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> GoodRegistry.of(
                List.of(product("alpha"), product("beta"), product("gamma")),
                List.of(
                        transformation("alpha", "beta"),
                        transformation("beta", "gamma"),
                        transformation("gamma", "alpha")
                ),
                BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryViewsAreImmutable() {
        GoodRegistry registry = registry();

        assertThrows(UnsupportedOperationException.class, () -> registry.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.transformations().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.knownIndustries().clear());
    }
}
