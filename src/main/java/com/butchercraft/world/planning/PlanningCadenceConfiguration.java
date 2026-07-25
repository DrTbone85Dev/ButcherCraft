package com.butchercraft.world.planning;

public record PlanningCadenceConfiguration(
        int schemaVersion,
        long periodicIntervalTicks,
        long minimumSeparationTicks,
        long maximumIntervalTicks,
        int pendingTriggerLimit
) {
    public static final long DEFAULT_PERIODIC_INTERVAL_TICKS = 1_200L;
    public static final long DEFAULT_MINIMUM_SEPARATION_TICKS = 20L;
    public static final long DEFAULT_MAXIMUM_INTERVAL_TICKS = 72_000L;
    public static final int DEFAULT_PENDING_TRIGGER_LIMIT = 1_024;

    public PlanningCadenceConfiguration {
        schemaVersion = PlanningValidation.schema(schemaVersion);
        if (periodicIntervalTicks <= 0L) {
            throw new IllegalArgumentException("Planning periodic interval must be positive");
        }
        if (minimumSeparationTicks <= 0L) {
            throw new IllegalArgumentException("Planning minimum separation must be positive");
        }
        if (maximumIntervalTicks < periodicIntervalTicks) {
            throw new IllegalArgumentException("Planning maximum interval must not be below the periodic interval");
        }
        if (pendingTriggerLimit <= 0) {
            throw new IllegalArgumentException("Planning pending trigger limit must be positive");
        }
    }

    public static PlanningCadenceConfiguration standard() {
        return new PlanningCadenceConfiguration(
                PlanningValidation.SCHEMA_VERSION,
                DEFAULT_PERIODIC_INTERVAL_TICKS,
                DEFAULT_MINIMUM_SEPARATION_TICKS,
                DEFAULT_MAXIMUM_INTERVAL_TICKS,
                DEFAULT_PENDING_TRIGGER_LIMIT
        );
    }

    public String configurationIdentity() {
        return PlanningValidation.derivedId(
                "planning_cadence_configuration",
                Integer.toString(schemaVersion),
                Long.toString(periodicIntervalTicks),
                Long.toString(minimumSeparationTicks),
                Long.toString(maximumIntervalTicks),
                Integer.toString(pendingTriggerLimit)
        );
    }
}
