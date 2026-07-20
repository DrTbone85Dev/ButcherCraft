package com.butchercraft.workstation;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.transaction.ProcessingTransaction;

final class LegacyWorkstationExecutionStrategy implements WorkstationExecutionStrategy {
    static final LegacyWorkstationExecutionStrategy INSTANCE = new LegacyWorkstationExecutionStrategy();

    private LegacyWorkstationExecutionStrategy() {
    }

    @Override
    public OperationResult prepare(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        return transaction(operation).prepare();
    }

    @Override
    public OperationResult commit(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        ProcessingTransaction transaction = transaction(operation);
        OperationResult prepared = transaction.prepare();
        if (!prepared.succeeded()) {
            return prepared;
        }
        return transaction.commit();
    }

    private static ProcessingTransaction transaction(ResolvedWorkstationOperation operation) {
        return ProcessingTransaction.create(context(operation.inputProduct(), operation.engineOperation()));
    }

    private static ProcessingContext context(Product input, com.butchercraft.engine.operation.ProcessingOperation operation) {
        return PrototypeProcessingContextValues.context(input, operation);
    }
}
