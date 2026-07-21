package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingDefinitionTest {
    private static final EngineId RETAIL_PACKAGE = EngineId.of("butchercraft:retail_package");
    private static final ProductCategory BEEF = ProductCategory.fromId(EngineId.of("butchercraft:beef"));
    private static final ProductCategory PORK = ProductCategory.fromId(EngineId.of("butchercraft:pork"));
    private static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    private static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    private static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    @Test
    void builderCreatesCompleteImmutablePackagingDefinition() {
        PackagingDefinition definition = validBuilder().build();

        assertEquals(RETAIL_PACKAGE, definition.id());
        assertEquals("Retail Package", definition.displayName());
        assertEquals(PackagingDefinition.CURRENT_SCHEMA_VERSION, definition.schemaVersion());
        assertEquals(PackagingFormat.RETAIL, definition.format());
        assertEquals(QuantityUnit.GRAM, definition.defaultQuantityUnit());
        assertEquals(List.of(BEEF), definition.compatibleCategories().stream().toList());
        assertEquals(List.of(TAG_GROUND), definition.compatibleTags().stream().toList());
        assertEquals("built_in", definition.metadata().get(METADATA_SOURCE));
        assertTrue(definition.isCompatibleWith(product("butchercraft:ground_beef", BEEF, QuantityUnit.GRAM, TAG_GROUND)));
        assertFalse(definition.isCompatibleWith(product("butchercraft:beef_trim", BEEF, QuantityUnit.GRAM, TAG_TRIM)));
        assertFalse(definition.isCompatibleWith(product("butchercraft:ground_pork", PORK, QuantityUnit.GRAM, TAG_GROUND)));
    }

    @Test
    void builderRejectsIncompleteAndInvalidDefinitions() {
        assertThrows(IllegalStateException.class, () -> PackagingDefinition.builder()
                .displayName("Retail Package")
                .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                .format(PackagingFormat.RETAIL)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .compatibleCategory(BEEF)
                .build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().displayName(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().schemaVersion(0).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().metadata(METADATA_SOURCE, " ").build());
        assertThrows(IllegalArgumentException.class, () -> PackagingDefinition.builder()
                .id(RETAIL_PACKAGE)
                .displayName("Retail Package")
                .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                .format(PackagingFormat.RETAIL)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .build());
        assertThrows(NullPointerException.class, () -> validBuilder().compatibleTag((EngineId) null));
    }

    @Test
    void definitionDefensivelyCopiesCollections() {
        Set<ProductCategory> categories = new LinkedHashSet<>();
        categories.add(BEEF);
        Set<EngineId> tags = new LinkedHashSet<>();
        tags.add(TAG_GROUND);
        Map<EngineId, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_SOURCE, " built_in ");

        PackagingDefinition definition = new PackagingDefinition(
                RETAIL_PACKAGE,
                " Retail Package ",
                PackagingDefinition.CURRENT_SCHEMA_VERSION,
                PackagingFormat.RETAIL,
                QuantityUnit.GRAM,
                categories,
                tags,
                metadata
        );
        categories.clear();
        tags.clear();
        metadata.put(EngineId.of("butchercraft:schema/changed"), "changed");

        assertEquals("Retail Package", definition.displayName());
        assertEquals(List.of(BEEF), definition.compatibleCategories().stream().toList());
        assertEquals(List.of(TAG_GROUND), definition.compatibleTags().stream().toList());
        assertEquals(Map.of(METADATA_SOURCE, "built_in"), definition.metadata());
        assertThrows(UnsupportedOperationException.class, () -> definition.compatibleCategories().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.compatibleTags().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.metadata().clear());
    }

    @Test
    void equalityIncludesCanonicalFields() {
        PackagingDefinition base = validBuilder().build();

        assertEquals(base, validBuilder().build());
        assertNotEquals(base, validBuilder().displayName("Different").build());
        assertNotEquals(base, validBuilder().compatibleTag("butchercraft:trait/extra").build());
        assertNotEquals(base, validBuilder().metadata("butchercraft:schema/extra", "value").build());
        assertNotEquals(base, validBuilder().defaultQuantityUnit(QuantityUnit.PIECE).build());
    }

    static PackagingDefinition.Builder validBuilder() {
        return PackagingDefinition.builder()
                .id(RETAIL_PACKAGE)
                .displayName("Retail Package")
                .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                .format(PackagingFormat.RETAIL)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .compatibleCategory(BEEF)
                .compatibleTag(TAG_GROUND)
                .metadata(METADATA_SOURCE, "built_in");
    }

    private static ProductDefinition product(
            String id,
            ProductCategory category,
            QuantityUnit unit,
            EngineId tag
    ) {
        return ProductDefinition.builder()
                .id(id)
                .displayName(id)
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(category)
                .defaultQuantityUnit(unit)
                .tag(tag)
                .metadata(METADATA_SOURCE, "test")
                .build();
    }
}
