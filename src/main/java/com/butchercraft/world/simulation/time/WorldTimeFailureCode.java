package com.butchercraft.world.simulation.time;

public enum WorldTimeFailureCode {
    INVALID_CONFIGURED_DURATION("invalid_configured_duration"),
    UNSUPPORTED_CONFIG_SCHEMA("unsupported_config_schema"),
    ARITHMETIC_OVERFLOW("arithmetic_overflow"),
    INVALID_ACCUMULATOR_STATE("invalid_accumulator_state"),
    UNSUPPORTED_PERSISTENCE_SCHEMA("unsupported_persistence_schema"),
    SOURCE_DIMENSION_UNAVAILABLE("source_dimension_unavailable"),
    BACKWARD_TIME_MOVEMENT("backward_time_movement"),
    EXTERNAL_TIME_CONTROLLER_CONFLICT("external_time_controller_conflict"),
    DUPLICATE_TICK_APPLICATION("duplicate_tick_application"),
    CLIENT_SNAPSHOT_MISMATCH("client_snapshot_mismatch"),
    WORLD_IDENTITY_MISMATCH("world_identity_mismatch"),
    UNSUPPORTED_FIXED_TIME_DIMENSION("unsupported_fixed_time_dimension"),
    INCOMPATIBLE_CONFIGURATION_IDENTITY("incompatible_configuration_identity"),
    INVALID_OBSERVATION("invalid_observation");

    private final String serializedName;

    WorldTimeFailureCode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
