package com.butchercraft.world.transaction;

import java.util.Arrays;

public enum TransactionStatus {
    PENDING("pending"),
    VALIDATED("validated"),
    APPLIED("applied"),
    REJECTED("rejected"),
    ROLLED_BACK("rolled_back");

    private final String serializedName;

    TransactionStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static TransactionStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction status: " + serializedName));
    }
}
