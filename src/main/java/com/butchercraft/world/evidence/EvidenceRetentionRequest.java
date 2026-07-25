package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceRetentionRequest(
        EvidenceDescriptor evidence,
        EvidenceRetentionPolicy policy,
        EvidenceLifecycleDisposition requestedDisposition,
        boolean sourceEvidenceAvailable
) {
    public EvidenceRetentionRequest {
        evidence = Objects.requireNonNull(evidence, "evidence");
        policy = Objects.requireNonNull(policy, "policy");
        requestedDisposition = Objects.requireNonNull(requestedDisposition, "requestedDisposition");
    }
}
