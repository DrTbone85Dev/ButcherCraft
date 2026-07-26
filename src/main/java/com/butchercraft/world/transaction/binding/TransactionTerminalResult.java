package com.butchercraft.world.transaction.binding;

public enum TransactionTerminalResult {
    APPLIED,
    REJECTED,
    DUPLICATE_OBSERVATION,
    CONFLICT
}
