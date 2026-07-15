package com.butchercraft.engine;

import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YieldRatioTest {
    @Test
    void fullReducedAndIncreasedYieldAreExactAndUnitPreserving() {
        assertEquals(ProductQuantity.grams(1_000), YieldRatio.identity().apply(ProductQuantity.grams(1_000)));
        assertEquals(ProductQuantity.grams(900), new YieldRatio(9, 10).apply(ProductQuantity.grams(1_000)));
        assertEquals(ProductQuantity.grams(1_100), new YieldRatio(9, 10).apply(ProductQuantity.grams(1_000), 2_000));
        assertEquals(QuantityUnit.GRAM, new YieldRatio(9, 10).apply(ProductQuantity.grams(1_000), 2_000).unit());
    }

    @Test
    void smallQuantityAndHalfwayRoundingAreDocumented() {
        assertEquals(ProductQuantity.grams(0), new YieldRatio(1, 3).apply(ProductQuantity.grams(1)));
        assertEquals(ProductQuantity.grams(1), new YieldRatio(1, 2).apply(ProductQuantity.grams(1)));
        assertEquals(ProductQuantity.grams(2), new YieldRatio(1, 2).apply(ProductQuantity.grams(3)));
    }

    @Test
    void zeroNegativeAndOverflowCasesAreProtected() {
        assertEquals(ProductQuantity.grams(0), new YieldRatio(0, 1).apply(ProductQuantity.grams(1_000)));
        assertThrows(IllegalArgumentException.class, () -> new YieldRatio(1, 10).apply(ProductQuantity.grams(1_000), -2_000));
        assertThrows(ArithmeticException.class, () -> new YieldRatio(2, 1).apply(ProductQuantity.grams(Long.MAX_VALUE)));
    }
}
