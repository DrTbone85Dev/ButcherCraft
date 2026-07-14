package com.butchercraft.engine.transaction;

/**
 * Explicit lifecycle state for a processing transaction.
 *
 * <p>Normal flow is CREATED -> VALIDATED -> PREPARED -> COMMITTED. CANCELLED, REJECTED, and
 * FAILED are terminal failure outcomes. The enum is Minecraft-independent and suitable for unit
 * tests and future synchronization summaries.</p>
 */
public enum TransactionState {
    CREATED,
    VALIDATED,
    PREPARED,
    COMMITTED,
    CANCELLED,
    REJECTED,
    FAILED;

    public boolean isTerminal() {
        return this == COMMITTED || isFailureTerminal();
    }

    public boolean isFailureTerminal() {
        return this == CANCELLED || this == REJECTED || this == FAILED;
    }
}
