package com.butchercraft.workstation.condition;

import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;

public final class ConditionProcessingCandidates {
    private ConditionProcessingCandidates() { }

    public static ConditionProcessingCommit prepare(ConditionProjection current,
            ConditionProcessingPreparation preparation, ExecutionOperationId operation,
            String exactPostInventoryIdentity, ExecutionOwnerResultEvidence productResult, long tick) {
        return prepare(current, preparation, operation, exactPostInventoryIdentity, productResult, tick,
                Math.incrementExact(current.state().orElseThrow().revision()));
    }

    public static ConditionProcessingCommit prepare(ConditionProjection current,
            ConditionProcessingPreparation preparation, ExecutionOperationId operation,
            String exactPostInventoryIdentity, ExecutionOwnerResultEvidence productResult, long tick,
            long plannedProjectionRevision) {
        MachineConditionState before = current.state().orElseThrow();
        if (current.pendingOperatingTransition().isPresent() || !before.equals(preparation.preState())) {
            throw new IllegalStateException("Stale condition preparation or unresolved operating transition");
        }
        String effect = ConditionDigest.identity("butchercraft:processing_condition_effect/v1",
                before.instanceId().value(), operation.value(), productResult.domainEffectIdentity().value());
        MachineConditionState after = preparation.complete(effect, tick);
        String ownerCandidate = ConditionDigest.identity("butchercraft:joint_workstation_candidate/v1",
                operation.value(), productResult.contentDigest(), exactPostInventoryIdentity,
                before.digest(), after.digest(), preparation.freshnessIdentity());
        String request = new ConditionDigest("butchercraft:processing_condition_request/v1")
                .add(operation.value()).add(productResult.domainEffectIdentity().value())
                .add(preparation.freshnessIdentity()).add(exactPostInventoryIdentity)
                .add(productResult.contentDigest()).add(tick).finish();
        ConditionEffectReceipt receipt = ConditionEffectReceipt.prepare(before, after,
                ConditionEffectKind.SUCCESSFUL_PROCESSING, request, current.receiptHead(), ownerCandidate,
                java.util.Optional.empty(), java.util.Optional.of(new ConditionProcessingBinding(operation,
                        exactPostInventoryIdentity, plannedProjectionRevision)));
        String resultDigest = resultDigest(receipt);
        ExecutionOwnerResultEvidence result = ExecutionOwnerResultEvidence.of(productResult.ownerSubsystemId(),
                resultIdentity(receipt), productResult.domainEffectIdentity(), resultDigest);
        return new ConditionProcessingCommit(current.committed(receipt), receipt, result);
    }

    public static String resultIdentity(ConditionEffectReceipt receipt) {
        return "butchercraft:workstation_result/v2/" + resultDigest(receipt).substring(7);
    }

    private static String resultDigest(ConditionEffectReceipt receipt) {
        return new ConditionDigest("butchercraft:joint_workstation_result/v2")
                .add(receipt.ownerCandidateIdentity()).add(receipt.digest()).add(receipt.postState().digest()).finish();
    }
}
