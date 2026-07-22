package com.butchercraft.world.property;

public record BuildingSize(int squareMeters) {
    public BuildingSize {
        if (squareMeters < 0) {
            throw new IllegalArgumentException("Building size must not be negative");
        }
    }
}
