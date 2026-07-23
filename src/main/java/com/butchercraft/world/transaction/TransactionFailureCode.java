package com.butchercraft.world.transaction;

public enum TransactionFailureCode {
    UNKNOWN_GOOD,
    UNKNOWN_INVENTORY,
    UNKNOWN_ACTOR,
    NEGATIVE_QUANTITY,
    INSUFFICIENT_INVENTORY,
    CAPACITY_EXCEEDED,
    INVALID_STATUS,
    VALIDATION_FAILED,
    UNKNOWN
}
