package com.butchercraft.product.component;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStackDataTest {
    private static final ProductStackData BEEF_TRIM = ProductStackData.fromEngineValues(
            EngineId.of("butchercraft:beef_trim"),
            ProductCategory.BEEF,
            ProcessingState.RAW,
            1_000,
            QuantityUnit.GRAM,
            700
    );

    @Test
    void validConstructionStoresStableFields() {
        assertEquals("butchercraft:beef_trim", BEEF_TRIM.productTypeId());
        assertEquals("butchercraft:beef", BEEF_TRIM.sourceCategoryId());
        assertEquals("butchercraft:trim", BEEF_TRIM.processingStateId());
        assertEquals(1_000, BEEF_TRIM.quantityValue());
        assertEquals("gram", BEEF_TRIM.quantityUnitId());
        assertEquals(700, BEEF_TRIM.qualityScore());
    }

    @Test
    void invalidIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "ButcherCraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:fish",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:sliced",
                1_000,
                "gram",
                700
        ));
    }

    @Test
    void invalidQuantityUnitAndQualityAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                -1,
                "gram",
                700
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "kilogram",
                700
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                -1
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                1_001
        ));
    }

    @Test
    void equalityUsesAllStoredFields() {
        ProductStackData same = ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:beef_trim"),
                ProductCategory.BEEF,
                ProcessingState.RAW,
                1_000,
                QuantityUnit.GRAM,
                700
        );
        ProductStackData changedQuality = ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:beef_trim"),
                ProductCategory.BEEF,
                ProcessingState.RAW,
                1_000,
                QuantityUnit.GRAM,
                701
        );

        assertEquals(BEEF_TRIM, same);
        assertTrue(!BEEF_TRIM.equals(changedQuality));
    }

    @Test
    void persistentCodecRoundTripsValidData() {
        JsonElement encoded = ProductStackData.CODEC.encodeStart(JsonOps.INSTANCE, BEEF_TRIM).result().orElseThrow();
        ProductStackData decoded = ProductStackData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(BEEF_TRIM, decoded);
    }

    @Test
    void persistentCodecRejectsCorruptedData() {
        JsonElement invalid = JsonParser.parseString("""
                {
                  "product_type_id": "butchercraft:beef_trim",
                  "source_category_id": "butchercraft:beef",
                  "processing_state_id": "butchercraft:trim",
                  "quantity_value": -1,
                  "quantity_unit_id": "gram",
                  "quality_score": 700
                }
                """);

        assertTrue(ProductStackData.CODEC.parse(JsonOps.INSTANCE, invalid).result().isEmpty());
    }

    @Test
    void streamCodecRoundTripsValidData() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ProductStackData.STREAM_CODEC.encode(buffer, BEEF_TRIM);

            assertEquals(BEEF_TRIM, ProductStackData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
