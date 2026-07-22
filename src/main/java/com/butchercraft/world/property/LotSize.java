package com.butchercraft.world.property;

public record LotSize(int squareMeters) {
    public LotSize {
        if (squareMeters <= 0) {
            throw new IllegalArgumentException("Lot size must be positive");
        }
    }
}
