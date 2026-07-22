package com.butchercraft.world.inventory;

import java.util.Arrays;

public enum InventoryStatus {
    ACTIVE("active", true, true),
    LOCKED("locked", false, false),
    IN_TRANSIT("in_transit", true, true),
    MAINTENANCE("maintenance", false, false),
    DISABLED("disabled", false, false);

    private final String serializedName;
    private final boolean canReceive;
    private final boolean canRelease;

    InventoryStatus(String serializedName, boolean canReceive, boolean canRelease) {
        this.serializedName = serializedName;
        this.canReceive = canReceive;
        this.canRelease = canRelease;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean canReceive() {
        return canReceive;
    }

    public boolean canRelease() {
        return canRelease;
    }

    public static InventoryStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown inventory status: " + serializedName));
    }
}
