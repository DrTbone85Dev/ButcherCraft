package com.butchercraft.world.simulation.time;

public enum WorldTimeMovementClassification {
    INITIALIZED("initialized"),
    PERSISTENCE_RESTORED("persistence_restored"),
    NORMAL_SCALED_ADVANCEMENT("normal_scaled_advancement"),
    FORWARD_JUMP("forward_jump"),
    BACKWARD_JUMP("backward_jump"),
    CONFIGURATION_TRANSITION("configuration_transition"),
    DISABLED_VANILLA_CONTROL("disabled_vanilla_control"),
    SOURCE_DIMENSION_UNAVAILABLE("source_dimension_unavailable"),
    FIXED_TIME_DIMENSION_IGNORED("fixed_time_dimension_ignored"),
    EXTERNAL_AUTHORITY_CONFLICT("external_authority_conflict"),
    DUPLICATE_TICK_APPLICATION("duplicate_tick_application");

    private final String serializedName;

    WorldTimeMovementClassification(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorldTimeMovementClassification fromSerializedName(String value) {
        for (WorldTimeMovementClassification classification : values()) {
            if (classification.serializedName.equals(value)) {
                return classification;
            }
        }
        throw new IllegalArgumentException("Unknown world time movement classification: " + value);
    }
}
