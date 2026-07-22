package com.butchercraft.world.property;

public record ExpansionCapacity(int squareMeters) {
    public ExpansionCapacity {
        if (squareMeters < 0) {
            throw new IllegalArgumentException("Expansion capacity must not be negative");
        }
    }
}
