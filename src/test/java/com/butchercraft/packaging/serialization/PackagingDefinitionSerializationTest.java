package com.butchercraft.packaging.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.packaging.definition.PackagingFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackagingDefinitionSerializationTest {
    @Test
    void canonicalPackagingDefinitionRoundTripsEveryField() {
        PackagingDefinition definition = PackagingDefinition.builder()
                .id("butchercraft:retail_package")
                .displayName("Retail Package")
                .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                .format(PackagingFormat.RETAIL)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .compatibleCategory(ProductCategory.fromId(EngineId.of("butchercraft:beef")))
                .compatibleTag("butchercraft:trait/ground")
                .metadata("butchercraft:schema/source", "test")
                .build();

        SerializedPackagingDefinition serialized = new CanonicalPackagingDefinitionSerializer().serialize(definition);
        PackagingDefinition deserialized = new CanonicalPackagingDefinitionDeserializer().deserialize(serialized);

        assertNotSame(definition, deserialized);
        assertEquals(definition, deserialized);
        assertEquals(PackagingSchemaVersion.CURRENT, serialized.schemaVersion());
        assertEquals("butchercraft:retail_package", serialized.id());
        assertEquals("Retail Package", serialized.displayName());
        assertEquals("retail", serialized.format());
        assertEquals("gram", serialized.defaultQuantityUnit());
        assertEquals(List.of("butchercraft:beef"), serialized.compatibleCategories());
        assertEquals(List.of("butchercraft:trait/ground"), serialized.compatibleTags());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
    }

    @Test
    void serializedPackagingDefinitionIsImmutableAndValidated() {
        SerializedPackagingDefinition serialized = new SerializedPackagingDefinition(
                PackagingSchemaVersion.CURRENT,
                " butchercraft:retail_package ",
                " Retail Package ",
                " retail ",
                " gram ",
                List.of(" butchercraft:beef "),
                List.of(" butchercraft:trait/ground "),
                Map.of(" butchercraft:schema/source ", " test ")
        );

        assertEquals("butchercraft:retail_package", serialized.id());
        assertEquals("Retail Package", serialized.displayName());
        assertEquals("retail", serialized.format());
        assertEquals("gram", serialized.defaultQuantityUnit());
        assertEquals(List.of("butchercraft:beef"), serialized.compatibleCategories());
        assertEquals(List.of("butchercraft:trait/ground"), serialized.compatibleTags());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
        assertThrows(UnsupportedOperationException.class, () -> serialized.compatibleCategories().clear());
        assertThrows(UnsupportedOperationException.class, () -> serialized.compatibleTags().clear());
        assertThrows(UnsupportedOperationException.class, () -> serialized.metadata().clear());
        assertThrows(IllegalArgumentException.class, () -> new PackagingSchemaVersion(0));
        assertThrows(IllegalArgumentException.class, () -> new SerializedPackagingDefinition(
                PackagingSchemaVersion.CURRENT,
                " ",
                "Retail Package",
                "retail",
                "gram",
                List.of("butchercraft:beef"),
                List.of("butchercraft:trait/ground"),
                Map.of()
        ));
    }

    @Test
    void unsupportedSchemaVersionFailsDeserialization() {
        SerializedPackagingDefinition serialized = new SerializedPackagingDefinition(
                new PackagingSchemaVersion(99),
                "butchercraft:retail_package",
                "Retail Package",
                "retail",
                "gram",
                List.of("butchercraft:beef"),
                List.of("butchercraft:trait/ground"),
                Map.of()
        );

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalPackagingDefinitionDeserializer().deserialize(serialized));
    }
}
