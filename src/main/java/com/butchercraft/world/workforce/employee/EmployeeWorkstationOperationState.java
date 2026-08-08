package com.butchercraft.world.workforce.employee;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public enum EmployeeWorkstationOperationState {
    IDLE,
    PREPARING,
    OPERATING,
    WAITING_FOR_COMPLETION,
    OPERATION_COMPLETE,
    FAILURE;

    private static final Map<EmployeeWorkstationOperationState, Set<EmployeeWorkstationOperationState>> TRANSITIONS =
            transitions();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean active() {
        return this == PREPARING || this == OPERATING || this == WAITING_FOR_COMPLETION;
    }

    public boolean canTransitionTo(EmployeeWorkstationOperationState next) {
        return TRANSITIONS.get(this).contains(Objects.requireNonNull(next, "next"));
    }

    private static Map<EmployeeWorkstationOperationState, Set<EmployeeWorkstationOperationState>> transitions() {
        Map<EmployeeWorkstationOperationState, Set<EmployeeWorkstationOperationState>> transitions =
                new EnumMap<>(EmployeeWorkstationOperationState.class);
        transitions.put(IDLE, EnumSet.of(IDLE, PREPARING));
        transitions.put(PREPARING, EnumSet.of(PREPARING, OPERATING, FAILURE, IDLE));
        transitions.put(OPERATING, EnumSet.of(OPERATING, WAITING_FOR_COMPLETION, FAILURE));
        transitions.put(WAITING_FOR_COMPLETION,
                EnumSet.of(WAITING_FOR_COMPLETION, OPERATION_COMPLETE, FAILURE));
        transitions.put(OPERATION_COMPLETE, EnumSet.of(OPERATION_COMPLETE, IDLE));
        transitions.put(FAILURE, EnumSet.of(FAILURE, IDLE));
        return Map.copyOf(transitions);
    }
}
