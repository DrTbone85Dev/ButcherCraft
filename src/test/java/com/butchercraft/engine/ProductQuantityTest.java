package com.butchercraft.engine;

import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductQuantityTest {
    @Test
    void exactStorageUsesLongAmountAndUnit() {
        ProductQuantity quantity = ProductQuantity.grams(123_456_789L);

        assertEquals(123_456_789L, quantity.amount());
        assertEquals(QuantityUnit.GRAM, quantity.unit());
    }

    @Test
    void additionAndSubtractionAreExact() {
        assertEquals(ProductQuantity.grams(1_500), ProductQuantity.grams(1_000).add(ProductQuantity.grams(500)));
        assertEquals(ProductQuantity.grams(250), ProductQuantity.grams(1_000).subtract(ProductQuantity.grams(750)));
    }

    @Test
    void incompatibleUnitsAreRejectedWithoutConversion() {
        ProductQuantity grams = ProductQuantity.grams(1);
        ProductQuantity pieces = new ProductQuantity(1, QuantityUnit.PIECE);

        assertThrows(IllegalArgumentException.class, () -> grams.add(pieces));
        assertThrows(IllegalArgumentException.class, () -> grams.subtract(pieces));
    }

    @Test
    void negativeValuesAreRejectedAndZeroIsDeliberate() {
        ProductQuantity zero = ProductQuantity.grams(0);

        assertTrue(zero.isZero());
        assertFalse(ProductQuantity.grams(1).isZero());
        assertThrows(IllegalArgumentException.class, () -> ProductQuantity.grams(-1));
    }

    @Test
    void underflowAndOverflowArePrevented() {
        assertThrows(IllegalArgumentException.class, () -> ProductQuantity.grams(1).subtract(ProductQuantity.grams(2)));
        assertThrows(ArithmeticException.class, () -> ProductQuantity.grams(Long.MAX_VALUE).add(ProductQuantity.grams(1)));
    }
}
