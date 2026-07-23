package com.butchercraft.world.simulation.scheduler;

public record EligibilityUpdate(int promoted, int expired) {
    public EligibilityUpdate {
        if (promoted < 0 || expired < 0) {
            throw new IllegalArgumentException("Eligibility counts must not be negative");
        }
    }

    public int transitions() {
        return Math.addExact(promoted, expired);
    }
}
