package com.butchercraft.world.inventory;

import java.util.Objects;

public record InventoryMovementValidation(InventoryMovementCode code, String message) {
    public InventoryMovementValidation {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Inventory movement validation message cannot be blank");
        }
    }

    public static InventoryMovementValidation allowed() {
        return new InventoryMovementValidation(InventoryMovementCode.ALLOWED, "Inventory movement is valid");
    }

    public static InventoryMovementValidation rejected(InventoryMovementCode code, String message) {
        if (Objects.requireNonNull(code, "code") == InventoryMovementCode.ALLOWED) {
            throw new IllegalArgumentException("Rejected inventory movement must use a failure code");
        }
        return new InventoryMovementValidation(code, message);
    }

    public boolean isAllowed() {
        return code == InventoryMovementCode.ALLOWED;
    }
}
