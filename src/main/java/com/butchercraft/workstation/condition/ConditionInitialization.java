package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import java.util.Optional;

public final class ConditionInitialization {
    private ConditionInitialization() { }

    public static DurableWorkstationProjection candidate(DurableWorkstationProjection historical,
            MachineConditionPolicyRegistry policies, long tick, boolean provenNewInstance, boolean safeOwnerBoundary) {
        if (historical.condition().isPresent()) return historical;
        if (!safeOwnerBoundary || historical.preparedEndpointEffectIdentity().isPresent()) {
            throw new IllegalStateException("Legacy condition initialization requires a proven safe owner boundary");
        }
        ConditionProjection condition = policies.forMachine(historical.endpointKey().workstationTypeIdentity())
                .map(policy -> ConditionProjection.initial(MachineConditionState.healthy(historical.instanceId(), policy,
                        ConditionInitializationEvidence.create(historical.instanceId(), policy.identity(),
                                provenNewInstance ? Optional.empty() : Optional.of(historical.stateDigest())), tick)))
                .orElseGet(ConditionProjection::notApplicable);
        return historical.withCondition(Math.incrementExact(historical.projectionRevision()), condition);
    }
}
