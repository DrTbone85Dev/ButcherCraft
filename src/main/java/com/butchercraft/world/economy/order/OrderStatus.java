package com.butchercraft.world.economy.order;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    SUBMITTED("submitted"), ACCEPTED("accepted"), PARTIALLY_FULFILLED("partially_fulfilled"),
    FULFILLED("fulfilled"), REJECTED("rejected"), CANCELLED("cancelled"), EXPIRED("expired"), FAILED("failed");

    private final String serializedName;

    OrderStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean isTerminal() {
        return switch (this) {
            case FULFILLED, REJECTED, CANCELLED, EXPIRED, FAILED -> true;
            default -> false;
        };
    }

    public boolean isFulfillable() {
        return this == ACCEPTED || this == PARTIALLY_FULFILLED;
    }

    public Set<OrderStatus> allowedNextStatuses() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(ACCEPTED, REJECTED, CANCELLED, EXPIRED);
            case ACCEPTED -> EnumSet.of(PARTIALLY_FULFILLED, FULFILLED, CANCELLED, EXPIRED, FAILED);
            case PARTIALLY_FULFILLED -> EnumSet.of(FULFILLED, CANCELLED, EXPIRED, FAILED);
            default -> Set.of();
        };
    }

    public static OrderStatus fromSerializedName(String value) {
        return Arrays.stream(values()).filter(status -> status.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order status: " + value));
    }
}
