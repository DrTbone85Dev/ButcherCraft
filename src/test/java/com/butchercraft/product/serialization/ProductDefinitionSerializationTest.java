package com.butchercraft.product.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;
import com.butchercraft.product.definition.ProductPackagingMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDefinitionSerializationTest {
    @Test
    void canonicalProductDefinitionRoundTripsEveryField() {
        ProductDefinition definition = ProductDefinition.builder()
                .id("butchercraft:beef_trim")
                .displayName("Beef Trim")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(EngineId.of("butchercraft:beef")))
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag("butchercraft:trait/trim")
                .metadata("butchercraft:schema/source", "test")
                .build();

        SerializedProductDefinition serialized = new CanonicalProductDefinitionSerializer().serialize(definition);
        ProductDefinition deserialized = new CanonicalProductDefinitionDeserializer().deserialize(serialized);

        assertNotSame(definition, deserialized);
        assertEquals(definition, deserialized);
        assertEquals(ProductSchemaVersion.CURRENT, serialized.schemaVersion());
        assertEquals("butchercraft:beef_trim", serialized.id());
        assertEquals("Beef Trim", serialized.displayName());
        assertEquals("butchercraft:beef", serialized.category());
        assertEquals("gram", serialized.defaultQuantityUnit());
        assertEquals(List.of("butchercraft:trait/trim"), serialized.tags());
        assertEquals(java.util.Optional.empty(), serialized.packaging());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
    }

    @Test
    void canonicalProductDefinitionRoundTripsPackagingMetadata() {
        ProductDefinition definition = ProductDefinition.builder()
                .id("butchercraft:retail_ground_beef")
                .displayName("Retail Ground Beef")
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(EngineId.of("butchercraft:beef")))
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag("butchercraft:trait/retail_packaged")
                .packagingMetadata(new ProductPackagingMetadata(
                        EngineId.of("butchercraft:retail_package"),
                        EngineId.of("butchercraft:ground_beef")
                ))
                .metadata("butchercraft:schema/source", "test")
                .build();

        SerializedProductDefinition serialized = new CanonicalProductDefinitionSerializer().serialize(definition);
        ProductDefinition deserialized = new CanonicalProductDefinitionDeserializer().deserialize(serialized);

        assertEquals(definition, deserialized);
        assertEquals("butchercraft:retail_package", serialized.packaging().orElseThrow().definition());
        assertEquals("butchercraft:ground_beef", serialized.packaging().orElseThrow().sourceProduct());
    }

    @Test
    void serializedProductDefinitionIsImmutableAndValidated() {
        SerializedProductDefinition serialized = new SerializedProductDefinition(
                ProductSchemaVersion.CURRENT,
                " butchercraft:beef_trim ",
                " Beef Trim ",
                " butchercraft:beef ",
                " gram ",
                List.of(" butchercraft:trait/trim "),
                Map.of(" butchercraft:schema/source ", " test ")
        );

        assertEquals("butchercraft:beef_trim", serialized.id());
        assertEquals("Beef Trim", serialized.displayName());
        assertEquals("butchercraft:beef", serialized.category());
        assertEquals("gram", serialized.defaultQuantityUnit());
        assertEquals(List.of("butchercraft:trait/trim"), serialized.tags());
        assertEquals(java.util.Optional.empty(), serialized.packaging());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
        assertThrows(UnsupportedOperationException.class, () -> serialized.tags().clear());
        assertThrows(UnsupportedOperationException.class, () -> serialized.metadata().clear());
        assertThrows(IllegalArgumentException.class, () -> new ProductSchemaVersion(0));
        assertThrows(IllegalArgumentException.class, () -> new SerializedProductDefinition(
                ProductSchemaVersion.CURRENT,
                " ",
                "Beef Trim",
                "butchercraft:beef",
                "gram",
                List.of(),
                Map.of()
        ));
    }
}
