package com.butchercraft.engine;

import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {
    @Test
    void validConstructionStoresRequiredFields() {
        Product product = EngineTestFixtures.rawProduct();

        assertEquals(EngineTestFixtures.RAW_BEEF, product.typeId());
        assertEquals(ProductCategory.BEEF, product.sourceCategory());
        assertEquals(ProcessingState.RAW, product.processingState());
        assertEquals(ProductQuantity.grams(1_000), product.quantity());
        assertEquals(ProductQuality.ofScore(650), product.quality());
    }

    @Test
    void invalidIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EngineId.of(""));
        assertThrows(IllegalArgumentException.class, () -> EngineId.of("ButcherCraft"));
        assertThrows(IllegalArgumentException.class, () -> EngineId.of("butchercraft:RawBeef"));
        assertThrows(IllegalArgumentException.class, () -> EngineId.of("raw-beef"));
    }

    @Test
    void equalityUsesAllProductFields() {
        Product first = EngineTestFixtures.rawProduct();
        Product second = EngineTestFixtures.rawProduct();
        Product changed = first.withQuality(ProductQuality.ofScore(651));

        assertEquals(first, second);
        assertNotSame(first, second);
        assertNotEquals(first, changed);
        assertNotSame(first, changed);
    }

    @Test
    void withMethodsReturnNewImmutableSnapshots() {
        Product original = EngineTestFixtures.rawProduct();
        Product changedQuantity = original.withQuantity(ProductQuantity.grams(500));
        Product changedQuality = original.withQuality(ProductQuality.ofScore(700));

        assertEquals(ProductQuantity.grams(1_000), original.quantity());
        assertEquals(ProductQuality.ofScore(650), original.quality());
        assertEquals(ProductQuantity.grams(500), changedQuantity.quantity());
        assertEquals(ProductQuality.ofScore(700), changedQuality.quality());
    }

    @Test
    void nullSourceOrStateIsRejected() {
        Product product = EngineTestFixtures.rawProduct();

        assertThrows(NullPointerException.class, () -> new Product(
                product.typeId(),
                null,
                product.processingState(),
                product.quantity(),
                product.quality()
        ));
        assertThrows(NullPointerException.class, () -> new Product(
                product.typeId(),
                product.sourceCategory(),
                null,
                product.quantity(),
                product.quality()
        ));
    }
}
