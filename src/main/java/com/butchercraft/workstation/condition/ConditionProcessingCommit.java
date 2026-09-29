package com.butchercraft.workstation.condition;

import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import java.util.Objects;

public record ConditionProcessingCommit(
        ConditionProjection projection, ConditionEffectReceipt receipt, ExecutionOwnerResultEvidence ownerResult
) {
    public ConditionProcessingCommit {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(ownerResult, "ownerResult");
        if (!projection.receiptHead().orElseThrow().equals(receipt.digest())
                || !projection.state().orElseThrow().equals(receipt.postState()) || !ownerResult.digestMatches()) {
            throw new IllegalArgumentException("Joint condition/product result is incoherent");
        }
    }
}
