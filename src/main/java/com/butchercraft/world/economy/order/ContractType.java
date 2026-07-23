package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum ContractType {
    SUPPLY("supply"), PURCHASE("purchase"), SALES("sales"), SERVICE("service"), TRANSPORT("transport"),
    STORAGE("storage"), PRODUCTION("production"), UTILITY("utility"), FRAMEWORK("framework"), INTERNAL("internal");

    private final String serializedName;

    ContractType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ContractType fromSerializedName(String value) {
        return Arrays.stream(values()).filter(type -> type.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown contract type: " + value));
    }
}
