package com.butchercraft.world.goods;

public record GoodYieldRatio(long numerator, long denominator) {
    public GoodYieldRatio {
        if (numerator <= 0) {
            throw new IllegalArgumentException("Good yield numerator must be positive");
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("Good yield denominator must be positive");
        }
    }

    public static GoodYieldRatio identity() {
        return new GoodYieldRatio(1, 1);
    }
}
