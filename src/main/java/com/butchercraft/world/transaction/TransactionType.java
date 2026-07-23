package com.butchercraft.world.transaction;

import java.util.Arrays;

public enum TransactionType {
    INVENTORY_ADD("inventory_add"),
    INVENTORY_REMOVE("inventory_remove"),
    INVENTORY_TRANSFER("inventory_transfer"),
    INVENTORY_ADJUSTMENT("inventory_adjustment"),
    PRODUCTION("production"),
    PURCHASE("purchase"),
    SALE("sale"),
    DELIVERY("delivery"),
    CONSUMPTION("consumption"),
    SPOILAGE("spoilage"),
    MANUAL("manual"),
    SYSTEM("system");

    private final String serializedName;

    TransactionType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static TransactionType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction type: " + serializedName));
    }
}
