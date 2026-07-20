package com.butchercraft.transformation;

/**
 * Lifecycle state for a pure transformation material transaction.
 */
public enum TransformationTransactionState {
    CREATED,
    PREPARED,
    COMMITTED,
    REJECTED,
    ROLLED_BACK
}
