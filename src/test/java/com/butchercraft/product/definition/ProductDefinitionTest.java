package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDefinitionTest {
    private static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    private static final ProductCategory BEEF = ProductCategory.fromId(EngineId.of("butchercraft:beef"));
    private static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    private static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    @Test
    void builderCreatesCompleteImmutableProductDefinition() {
        ProductDefinition definition = validBuilder().build();

        assertEquals(BEEF_TRIM, definition.id());
        assertEquals("Beef Trim", definition.displayName());
        assertEquals(ProductDefinition.CURRENT_SCHEMA_VERSION, definition.schemaVersion());
        assertEquals(BEEF, definition.category());
        assertEquals(QuantityUnit.GRAM, definition.defaultQuantityUnit());
        assertEquals(List.of(TAG_TRIM), definition.tags().stream().toList());
        assertTrue(definition.hasTag(TAG_TRIM));
        assertEquals("built_in", definition.metadata().get(METADATA_SOURCE));
    }

    @Test
    void builderRejectsIncompleteDefinitions() {
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder()
                .displayName("Beef Trim")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(BEEF)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .build());
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder()
                .id(BEEF_TRIM)
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(BEEF)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .build());
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder()
                .id(BEEF_TRIM)
                .displayName("Beef Trim")
                .category(BEEF)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .build());
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder()
                .id(BEEF_TRIM)
                .displayName("Beef Trim")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .build());
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder()
                .id(BEEF_TRIM)
                .displayName("Beef Trim")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(BEEF)
                .build());
    }

    @Test
    void definitionConstructionValidatesFields() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder().displayName(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().schemaVersion(0).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().metadata(METADATA_SOURCE, " ").build());
        assertThrows(NullPointerException.class, () -> validBuilder().tag((EngineId) null));
        assertThrows(NullPointerException.class, () -> validBuilder().metadata((EngineId) null, "value"));
    }

    @Test
    void definitionDefensivelyCopiesTagsAndMetadata() {
        Set<EngineId> tags = new LinkedHashSet<>();
        tags.add(TAG_TRIM);
        Map<EngineId, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_SOURCE, " built_in ");

        ProductDefinition definition = new ProductDefinition(
                BEEF_TRIM,
                " Beef Trim ",
                ProductDefinition.CURRENT_SCHEMA_VERSION,
                BEEF,
                QuantityUnit.GRAM,
                tags,
                metadata
        );
        tags.clear();
        metadata.put(EngineId.of("butchercraft:schema/changed"), "changed");

        assertEquals("Beef Trim", definition.displayName());
        assertEquals(List.of(TAG_TRIM), definition.tags().stream().toList());
        assertEquals(Map.of(METADATA_SOURCE, "built_in"), definition.metadata());
        assertThrows(UnsupportedOperationException.class, () -> definition.tags().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.metadata().put(METADATA_SOURCE, "changed"));
    }

    @Test
    void equalityIncludesCanonicalFields() {
        ProductDefinition base = validBuilder().build();

        assertEquals(base, validBuilder().build());
        assertNotEquals(base, validBuilder().displayName("Different").build());
        assertNotEquals(base, validBuilder().tag("butchercraft:trait/extra").build());
        assertNotEquals(base, validBuilder().metadata("butchercraft:schema/extra", "value").build());
        assertNotEquals(base, validBuilder().defaultQuantityUnit(QuantityUnit.PIECE).build());
    }

    private static ProductDefinition.Builder validBuilder() {
        return ProductDefinition.builder()
                .id(BEEF_TRIM)
                .displayName("Beef Trim")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(BEEF)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag(TAG_TRIM)
                .metadata(METADATA_SOURCE, "built_in");
    }
}
