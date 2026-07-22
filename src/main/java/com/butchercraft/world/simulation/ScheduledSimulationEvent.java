package com.butchercraft.world.simulation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ScheduledSimulationEvent(
        String id,
        long scheduledSimulationTick,
        SimulationEventType eventType,
        String payloadReference,
        SimulationEventStatus executionStatus
) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public ScheduledSimulationEvent {
        id = requireId(id);
        if (scheduledSimulationTick < 0L) {
            throw new IllegalArgumentException("Scheduled simulation event time must not be negative: " + scheduledSimulationTick);
        }
        eventType = Objects.requireNonNull(eventType, "eventType");
        payloadReference = requireNonBlank(payloadReference, "payloadReference");
        executionStatus = Objects.requireNonNull(executionStatus, "executionStatus");
    }

    public ScheduledSimulationEvent executed() {
        return withStatus(SimulationEventStatus.EXECUTED);
    }

    public ScheduledSimulationEvent cancelled() {
        return withStatus(SimulationEventStatus.CANCELLED);
    }

    public ScheduledSimulationEvent pending() {
        return withStatus(SimulationEventStatus.PENDING);
    }

    private ScheduledSimulationEvent withStatus(SimulationEventStatus status) {
        return new ScheduledSimulationEvent(id, scheduledSimulationTick, eventType, payloadReference, status);
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        value = value.toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Scheduled simulation event id must use lowercase snake case: " + value);
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Scheduled simulation event " + fieldName + " must not be blank");
        }
        return value;
    }
}
