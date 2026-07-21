package com.butchercraft.workstation;

import com.butchercraft.ButcherCraft;

public enum WorkstationFailureCode {
    NO_INPUT,
    INPUT_NOT_PRODUCT,
    MISSING_PRODUCT_DATA,
    UNKNOWN_PRODUCT_DEFINITION,
    PRODUCT_DATA_MISMATCH,
    NO_COMPATIBLE_OPERATION,
    MULTIPLE_COMPATIBLE_OPERATIONS,
    OPERATION_PROFILE_MISMATCH,
    OPERATION_CAPABILITY_MISMATCH,
    INPUT_QUANTITY_TOO_LOW,
    INPUT_QUANTITY_TOO_HIGH,
    OUTPUT_OCCUPIED,
    OUTPUT_INCOMPATIBLE,
    TRANSACTION_ALREADY_ACTIVE,
    INVALID_WORKSTATION_STATE,
    REGISTRY_NOT_AVAILABLE,
    PROCESSING_VALIDATION_REJECTED,
    RESULT_CREATION_FAILED,
    MISSING_REQUIRED_SUPPLY,
    INVALID_SUPPLY_ITEM,
    PACKAGING_DEFINITION_MISSING;

    public String reasonCode() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String messageKey() {
        return "workstation." + ButcherCraft.MOD_ID + ".failure." + reasonCode();
    }
}
