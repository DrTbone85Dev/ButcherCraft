package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingRegistryTest {
    @Test
    void builderCreatesImmutableRegistryThatPreservesInsertionOrder() {
        PackagingRegistryBuilder builder = PackagingRegistry.builder()
                .register(packaging("butchercraft:first", "First"))
                .register(packaging("butchercraft:second", "Second"));

        PackagingRegistry registry = builder.build();
        builder.register(packaging("butchercraft:third", "Third"));

        assertEquals(2, registry.size());
        assertTrue(registry.contains(EngineId.of("butchercraft:first")));
        assertFalse(registry.contains(EngineId.of("butchercraft:third")));
        assertEquals("First", registry.find(EngineId.of("butchercraft:first")).orElseThrow().displayName());
        assertEquals(List.of("butchercraft:first", "butchercraft:second"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void builderRejectsDuplicateIdsAndNullDefinitions() {
        PackagingDefinition definition = packaging("butchercraft:duplicate", "Duplicate");
        PackagingRegistryBuilder builder = PackagingRegistry.builder().register(definition);

        assertThrows(IllegalArgumentException.class, () -> builder.register(definition));
        assertThrows(NullPointerException.class, () -> PackagingRegistry.builder().register(null));
    }

    @Test
    void lookupMethodsRejectNullInputs() {
        PackagingRegistry registry = PackagingRegistry.builder().build();

        assertThrows(NullPointerException.class, () -> registry.contains(null));
        assertThrows(NullPointerException.class, () -> registry.find(null));
        assertThrows(NullPointerException.class, () -> registry.findByFormat(null));
        assertThrows(NullPointerException.class, () -> registry.findCompatible(null));
    }

    @Test
    void formatAndCompatibilityQueriesPreserveRegistrationOrder() {
        PackagingRegistry registry = PackagingRegistry.builder()
                .register(packaging("butchercraft:first", "First"))
                .register(packaging("butchercraft:second", "Second"))
                .build();

        ProductDefinition groundBeef = ProductDefinition.builder()
                .id("butchercraft:ground_beef")
                .displayName("Ground Beef")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(EngineId.of("butchercraft:beef")))
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag("butchercraft:trait/ground")
                .metadata("butchercraft:schema/source", "test")
                .build();

        assertEquals(List.of("butchercraft:first", "butchercraft:second"),
                registry.findByFormat(PackagingFormat.RETAIL)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of("butchercraft:first", "butchercraft:second"),
                registry.findCompatible(groundBeef)
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void builtInRegistryContainsRetailPackage() {
        PackagingRegistry registry = BuiltInPackagingRegistry.builtInRegistry();

        assertEquals(1, registry.size());
        assertTrue(registry.contains(BuiltInPackagingRegistry.RETAIL_PACKAGE));
        assertEquals(List.of("butchercraft:retail_package"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    private static PackagingDefinition packaging(String id, String displayName) {
        return PackagingDefinition.builder()
                .id(id)
                .displayName(displayName)
                .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                .format(PackagingFormat.RETAIL)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .compatibleCategory("butchercraft:beef")
                .compatibleTag("butchercraft:trait/ground")
                .metadata("butchercraft:schema/source", "test")
                .build();
    }
}
