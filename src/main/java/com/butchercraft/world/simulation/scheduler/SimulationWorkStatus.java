package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum SimulationWorkStatus {
    SCHEDULED("scheduled"), ELIGIBLE("eligible"), RUNNING("running"), COMPLETED("completed"),
    DEFERRED("deferred"), RETRY_WAIT("retry_wait"), FAILED("failed"), CANCELLED("cancelled"), EXPIRED("expired");
    private final String serializedName;
    SimulationWorkStatus(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
    public Set<SimulationWorkStatus> allowedNextStatuses() {
        return switch (this) {
            case SCHEDULED -> EnumSet.of(ELIGIBLE, CANCELLED, EXPIRED);
            case ELIGIBLE -> EnumSet.of(RUNNING, DEFERRED, CANCELLED, EXPIRED);
            case RUNNING -> EnumSet.of(COMPLETED, RETRY_WAIT, FAILED, DEFERRED);
            case RETRY_WAIT -> EnumSet.of(ELIGIBLE, CANCELLED, EXPIRED, FAILED);
            case DEFERRED -> EnumSet.of(ELIGIBLE, CANCELLED, EXPIRED);
            default -> Set.of();
        };
    }
    public static SimulationWorkStatus fromSerializedName(String value) {
        return Arrays.stream(values()).filter(status -> status.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation work status: " + value));
    }
}
