package com.butchercraft.world.transaction.binding;

public enum TransactionDuplicateOutcome {
    NEW_TRANSACTION,
    DUPLICATE_OBSERVATION,
    TRANSACTION_IDENTITY_CONFLICT,
    CONTENT_IDENTITY_CONFLICT
}
