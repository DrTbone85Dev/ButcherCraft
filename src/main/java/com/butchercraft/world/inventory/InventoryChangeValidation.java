package com.butchercraft.world.inventory;

import java.util.Objects;

public record InventoryChangeValidation(InventoryChangeCode code, String message) {
    public InventoryChangeValidation {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Inventory change validation message cannot be blank");
        }
    }

    public static InventoryChangeValidation allowed() {
        return new InventoryChangeValidation(InventoryChangeCode.ALLOWED, "Inventory changes are valid");
    }

    public static InventoryChangeValidation rejected(InventoryChangeCode code, String message) {
        if (Objects.requireNonNull(code, "code") == InventoryChangeCode.ALLOWED) {
            throw new IllegalArgumentException("Rejected inventory changes must use a failure code");
        }
        return new InventoryChangeValidation(code, message);
    }

    public boolean isAllowed() {
        return code == InventoryChangeCode.ALLOWED;
    }
}
