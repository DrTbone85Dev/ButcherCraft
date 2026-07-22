package com.butchercraft.world.ownership;

import java.util.Arrays;

public enum OwnershipAcquisitionMethod {
    FOUNDED("founded"),
    PURCHASED("purchased"),
    INHERITED("inherited"),
    PARTNERSHIP("partnership"),
    MERGER("merger"),
    BANKRUPTCY_PURCHASE("bankruptcy_purchase"),
    GOVERNMENT_TRANSFER("government_transfer");

    private final String serializedName;

    OwnershipAcquisitionMethod(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OwnershipAcquisitionMethod fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ownership acquisition method: " + serializedName));
    }
}
