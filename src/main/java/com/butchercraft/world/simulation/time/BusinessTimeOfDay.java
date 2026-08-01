package com.butchercraft.world.simulation.time;

public record BusinessTimeOfDay(int hour, int minute) {
    public BusinessTimeOfDay {
        if (hour < 0 || hour >= 24) {
            throw new IllegalArgumentException("Business hour must be within 0-23: " + hour);
        }
        if (minute < 0 || minute >= 60) {
            throw new IllegalArgumentException("Business minute must be within 0-59: " + minute);
        }
    }

    public String displayText() {
        return "%02d:%02d".formatted(hour, minute);
    }
}
