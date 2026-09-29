package com.butchercraft.workstation;

import com.butchercraft.workstation.condition.ConditionProcessingPreparation;
import com.butchercraft.workstation.condition.ConditionProcessingCommit;

import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import java.util.Optional;

/** Internal Workstation-owner composition, not a public mutation or Execution authority API. */
public interface WorkstationConditionProcessingAccess {
    Optional<ConditionProcessingPreparation> prepare();

    ConditionProcessingCommit prepareCommit(ConditionProcessingPreparation preparation,
            ExecutionOperationId operation, WorkstationInventoryCommitPlan inventory,
            ExecutionOwnerResultEvidence productResult, long tick);

    void installPreparedMirror(ConditionProcessingCommit candidate);
}
