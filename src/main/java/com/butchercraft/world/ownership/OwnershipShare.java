package com.butchercraft.world.ownership;

public record OwnershipShare(int basisPoints) {
    public static final int FULL_OWNERSHIP = 10_000;

    public OwnershipShare {
        if (basisPoints <= 0 || basisPoints > FULL_OWNERSHIP) {
            throw new IllegalArgumentException("Ownership share basis points must be between 1 and 10000: " + basisPoints);
        }
    }

    public double percentage() {
        return basisPoints / 100.0D;
    }
}
