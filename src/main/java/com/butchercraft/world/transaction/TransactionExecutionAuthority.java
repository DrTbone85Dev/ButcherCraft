package com.butchercraft.world.transaction;

public final class TransactionExecutionAuthority {
    private static final TransactionExecutionAuthority INSTANCE = new TransactionExecutionAuthority();

    private TransactionExecutionAuthority() {
    }

    static TransactionExecutionAuthority instance() {
        return INSTANCE;
    }
}
