package com.butchercraft.workstation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded server-owned lifecycle for a one-input, one-output processing workstation.
 */
public enum WorkstationState {
    IDLE,
    READY,
    PROCESSING,
    BLOCKED,
    COMPLETE,
    ERROR;

    private static final Map<WorkstationState, Set<WorkstationState>> TRANSITIONS = transitions();

    public boolean canTransitionTo(WorkstationState next) {
        return TRANSITIONS.get(this).contains(Objects.requireNonNull(next, "next"));
    }

    public WorkstationState transitionTo(WorkstationState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("Invalid workstation transition " + this + " -> " + next);
        }
        return next;
    }

    private static Map<WorkstationState, Set<WorkstationState>> transitions() {
        Map<WorkstationState, Set<WorkstationState>> map = new EnumMap<>(WorkstationState.class);
        map.put(IDLE, EnumSet.of(IDLE, READY, BLOCKED, ERROR));
        map.put(READY, EnumSet.of(READY, PROCESSING, BLOCKED, IDLE, ERROR));
        map.put(PROCESSING, EnumSet.of(PROCESSING, COMPLETE, BLOCKED, ERROR, IDLE));
        map.put(BLOCKED, EnumSet.of(BLOCKED, READY, PROCESSING, IDLE, ERROR));
        map.put(COMPLETE, EnumSet.of(COMPLETE, IDLE, ERROR));
        map.put(ERROR, EnumSet.of(ERROR, IDLE));
        return Map.copyOf(map);
    }
}
