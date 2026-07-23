package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum OrderType {
    PURCHASE("purchase"), SALE("sale"), SUPPLY("supply"), REPLENISHMENT("replenishment"),
    TRANSFER_REQUEST("transfer_request"), PRODUCTION_REQUEST("production_request"),
    DELIVERY_REQUEST("delivery_request"), SERVICE_REQUEST("service_request"),
    INTERNAL("internal"), SYSTEM("system");

    private final String serializedName;

    OrderType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OrderType fromSerializedName(String value) {
        return Arrays.stream(values()).filter(type -> type.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order type: " + value));
    }
}
