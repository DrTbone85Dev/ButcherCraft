package com.butchercraft.world.economy.order;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum ContractStatus {
    PROPOSED("proposed"), ACTIVE("active"), SUSPENDED("suspended"), COMPLETED("completed"),
    TERMINATED("terminated"), REJECTED("rejected"), EXPIRED("expired"), FAILED("failed");

    private final String serializedName;

    ContractStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, TERMINATED, REJECTED, EXPIRED, FAILED -> true;
            default -> false;
        };
    }

    public Set<ContractStatus> allowedNextStatuses() {
        return switch (this) {
            case PROPOSED -> EnumSet.of(ACTIVE, REJECTED, TERMINATED);
            case ACTIVE -> EnumSet.of(SUSPENDED, COMPLETED, TERMINATED, EXPIRED, FAILED);
            case SUSPENDED -> EnumSet.of(ACTIVE, TERMINATED, EXPIRED, FAILED);
            default -> Set.of();
        };
    }

    public static ContractStatus fromSerializedName(String value) {
        return Arrays.stream(values()).filter(status -> status.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown contract status: " + value));
    }
}
