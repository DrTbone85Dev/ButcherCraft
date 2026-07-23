package com.butchercraft.world.production;

import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.GoodYieldRatio;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProductionQuantityCalculator {
    private ProductionQuantityCalculator() {
    }

    public static GoodQuantity scaleInput(GoodQuantity perBatch, long batchCount) {
        return scale(perBatch, batchCount, GoodYieldRatio.identity());
    }

    public static GoodQuantity scaleOutput(
            GoodQuantity perBatch,
            long batchCount,
            GoodYieldRatio yieldRatio
    ) {
        return scale(perBatch, batchCount, yieldRatio);
    }

    public static long toInventoryUnits(GoodQuantity quantity) {
        try {
            return Objects.requireNonNull(quantity, "quantity").value().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Production quantity is not representable by the whole-unit Inventory schema",
                    exception
            );
        }
    }

    private static GoodQuantity scale(GoodQuantity quantity, long batchCount, GoodYieldRatio ratio) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(ratio, "ratio");
        if (batchCount <= 0L) {
            throw new IllegalArgumentException("Production batch count must be positive");
        }
        try {
            BigDecimal result = quantity.value()
                    .multiply(BigDecimal.valueOf(batchCount))
                    .multiply(BigDecimal.valueOf(ratio.numerator()))
                    .divide(BigDecimal.valueOf(ratio.denominator()));
            return new GoodQuantity(result);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Production quantity cannot be represented exactly", exception);
        }
    }
}
