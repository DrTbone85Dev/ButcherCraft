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
        assertTrue(BEEF_TRIM.packaging().isEmpty());
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
                "ButcherCraft:fish",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "ButcherCraft:sliced",
                1_000,
                "gram",
                700
        ));
    }

    @Test
    void dataDrivenSourceCategoriesAreAccepted() {
        ProductStackData bisonTrim = ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:bison_trim"),
                ProductCategory.fromId(EngineId.of("butchercraft:bison")),
                ProcessingState.RAW,
                1_000,
                QuantityUnit.GRAM,
                700
        );

        assertEquals("butchercraft:bison", bisonTrim.sourceCategoryId());
    }

    @Test
    void dataDrivenProcessingStatesAreAccepted() {
        ProductStackData forequarter = ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:beef_forequarter"),
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of("butchercraft:forequarter")),
                100_000,
                QuantityUnit.GRAM,
                700
        );

        assertEquals("butchercraft:forequarter", forequarter.processingStateId());
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
    void persistentCodecAcceptsLegacyDataWithoutPackaging() {
        JsonElement legacy = JsonParser.parseString("""
                {
                  "product_type_id": "butchercraft:beef_trim",
                  "source_category_id": "butchercraft:beef",
                  "processing_state_id": "butchercraft:trim",
                  "quantity_value": 1000,
                  "quantity_unit_id": "gram",
                  "quality_score": 700
                }
                """);

        ProductStackData decoded = ProductStackData.CODEC.parse(JsonOps.INSTANCE, legacy).result().orElseThrow();

        assertTrue(decoded.packaging().isEmpty());
    }

    @Test
    void packagingMetadataRoundTripsThroughPersistentAndStreamCodecs() {
        ProductStackData packaged = ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:retail_ground_beef"),
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of("butchercraft:retail_packaged")),
                900,
                QuantityUnit.GRAM,
                700
        ).withPackaging(new ProductStackPackagingData(
                "butchercraft:retail_package",
                "tray_wrap",
                "butchercraft:ground_beef"
        ));

        JsonElement encoded = ProductStackData.CODEC.encodeStart(JsonOps.INSTANCE, packaged).result().orElseThrow();
        ProductStackData decoded = ProductStackData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(packaged, decoded);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ProductStackData.STREAM_CODEC.encode(buffer, packaged);

            assertEquals(packaged, ProductStackData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidPackagingMetadataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProductStackPackagingData(
                "butchercraft:retail_package",
                "unsupported_format",
                "butchercraft:ground_beef"
        ));
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
