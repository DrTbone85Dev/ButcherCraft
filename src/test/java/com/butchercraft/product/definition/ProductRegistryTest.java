package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRegistryTest {
    private static final EngineId BEEF = EngineId.of("butchercraft:beef");
    private static final EngineId PORK = EngineId.of("butchercraft:pork");
    private static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    private static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    private static final List<String> EXPECTED_BUILT_IN_PRODUCT_IDS = List.of(
            "butchercraft:beef_trim",
            "butchercraft:ground_beef",
            "butchercraft:retail_ground_beef",
            "butchercraft:pork_trim",
            "butchercraft:ground_pork",
            "butchercraft:bison_trim",
            "butchercraft:ground_bison",
            "butchercraft:beef_forequarter",
            "butchercraft:beef_chuck",
            "butchercraft:beef_rib",
            "butchercraft:beef_packer_brisket",
            "butchercraft:beef_plate",
            "butchercraft:beef_shank",
            "butchercraft:beef_fat",
            "butchercraft:beef_bone",
            "butchercraft:beef_hindquarter",
            "butchercraft:beef_round",
            "butchercraft:beef_sirloin",
            "butchercraft:beef_short_loin",
            "butchercraft:beef_flank",
            "butchercraft:t_bone_steak",
            "butchercraft:porterhouse_steak",
            "butchercraft:beef_strip_loin",
            "butchercraft:beef_tenderloin",
            "butchercraft:top_round",
            "butchercraft:bottom_round",
            "butchercraft:eye_of_round",
            "butchercraft:sirloin_tip",
            "butchercraft:top_sirloin",
            "butchercraft:sirloin_steak",
            "butchercraft:tri_tip"
    );

    @Test
    void builderCreatesImmutableRegistryThatPreservesInsertionOrder() {
        ProductRegistryBuilder builder = ProductRegistry.builder()
                .register(product("butchercraft:first", "First", BEEF, TAG_TRIM))
                .register(product("butchercraft:second", "Second", PORK, TAG_GROUND));

        ProductRegistry registry = builder.build();
        builder.register(product("butchercraft:third", "Third", BEEF, TAG_TRIM));

        assertEquals(2, registry.size());
        assertTrue(registry.contains(EngineId.of("butchercraft:first")));
        assertFalse(registry.contains(EngineId.of("butchercraft:third")));
        assertEquals("First", registry.find(EngineId.of("butchercraft:first")).orElseThrow().displayName());
        assertTrue(registry.find(EngineId.of("butchercraft:missing")).isEmpty());
        assertEquals(List.of("butchercraft:first", "butchercraft:second"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void builderRejectsDuplicateIdsAndNullDefinitions() {
        ProductDefinition definition = product("butchercraft:duplicate", "Duplicate", BEEF, TAG_TRIM);
        ProductRegistryBuilder builder = ProductRegistry.builder().register(definition);

        assertThrows(IllegalArgumentException.class, () -> builder.register(definition));
        assertThrows(NullPointerException.class, () -> ProductRegistry.builder().register(null));
    }

    @Test
    void lookupMethodsRejectNullInputs() {
        ProductRegistry registry = ProductRegistry.builder().build();

        assertThrows(NullPointerException.class, () -> registry.contains(null));
        assertThrows(NullPointerException.class, () -> registry.find(null));
        assertThrows(NullPointerException.class, () -> registry.findByCategory(null));
        assertThrows(NullPointerException.class, () -> registry.findByTag(null));
    }

    @Test
    void categoryAndTagQueriesPreserveRegistrationOrder() {
        ProductRegistry registry = ProductRegistry.builder()
                .register(product("butchercraft:first", "First", BEEF, TAG_TRIM))
                .register(product("butchercraft:second", "Second", PORK, TAG_GROUND))
                .register(product("butchercraft:third", "Third", BEEF, TAG_TRIM))
                .build();

        assertEquals(List.of("butchercraft:first", "butchercraft:third"), registry.findByCategory(ProductCategory.fromId(BEEF))
                .map(definition -> definition.id().value())
                .toList());
        assertEquals(List.of("butchercraft:first", "butchercraft:third"), registry.findByTag(TAG_TRIM)
                .map(definition -> definition.id().value())
                .toList());
        assertTrue(registry.findByTag(EngineId.of("butchercraft:trait/missing")).findAny().isEmpty());
    }

    @Test
    void builtInRegistryContainsCurrentGrinderAndBandsawProducts() {
        ProductRegistry registry = BuiltInProductRegistry.builtInRegistry();

        assertEquals(31, registry.size());
        assertEquals(EXPECTED_BUILT_IN_PRODUCT_IDS, registry.stream()
                .map(definition -> definition.id().value())
                .toList());
        assertEquals(List.of("butchercraft:beef_trim", "butchercraft:pork_trim", "butchercraft:bison_trim"),
                registry.findByTag(BuiltInProductRegistry.TAG_TRIM)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of("butchercraft:ground_beef", "butchercraft:ground_pork", "butchercraft:ground_bison"),
                registry.findByTag(BuiltInProductRegistry.TAG_GROUND)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of("butchercraft:retail_ground_beef"),
                registry.findByTag(BuiltInProductRegistry.TAG_RETAIL_PACKAGED)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of(
                        "butchercraft:beef_chuck",
                        "butchercraft:beef_rib",
                        "butchercraft:beef_packer_brisket",
                        "butchercraft:beef_plate",
                        "butchercraft:beef_shank",
                        "butchercraft:beef_round",
                        "butchercraft:beef_sirloin",
                        "butchercraft:beef_short_loin",
                        "butchercraft:beef_flank"
                ),
                registry.findByTag(BuiltInProductRegistry.TAG_PRIMAL)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of("butchercraft:beef_hindquarter"),
                registry.findByTag(BuiltInProductRegistry.TAG_HINDQUARTER)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of(
                        "butchercraft:beef_strip_loin",
                        "butchercraft:beef_tenderloin",
                        "butchercraft:top_round",
                        "butchercraft:bottom_round",
                        "butchercraft:eye_of_round",
                        "butchercraft:sirloin_tip",
                        "butchercraft:top_sirloin",
                        "butchercraft:tri_tip"
                ),
                registry.findByTag(BuiltInProductRegistry.TAG_SUBPRIMAL)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of(
                        "butchercraft:t_bone_steak",
                        "butchercraft:porterhouse_steak",
                        "butchercraft:sirloin_steak"
                ),
                registry.findByTag(BuiltInProductRegistry.TAG_STEAK)
                        .map(definition -> definition.id().value())
                        .toList());
    }

    private static ProductDefinition product(String id, String displayName, EngineId category, EngineId tag) {
        return ProductDefinition.builder()
                .id(id)
                .displayName(displayName)
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(category))
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag(tag)
                .metadata(BuiltInProductRegistry.METADATA_SOURCE, "test")
                .build();
    }
}
