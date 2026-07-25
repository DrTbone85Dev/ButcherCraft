package com.butchercraft.world.evidence;

public record EvidenceRetentionPolicy(
        String policyId,
        int schemaVersion,
        long currentSimulationTick,
        long replayHorizonStartTick,
        long diagnosticExpiryBeforeTick,
        int hotEvidenceLimit,
        int diagnosticEvidenceLimit,
        boolean archivePublicationAvailable,
        boolean diagnosticExpiryEnabled,
        boolean derivedSummaryRebuildEnabled,
        boolean capacityPressure
) {
    public EvidenceRetentionPolicy {
        policyId = EvidenceValidation.id(policyId, "policyId");
        currentSimulationTick = EvidenceValidation.nonNegative(currentSimulationTick, "currentSimulationTick");
        replayHorizonStartTick = EvidenceValidation.nonNegative(replayHorizonStartTick, "replayHorizonStartTick");
        diagnosticExpiryBeforeTick = EvidenceValidation.nonNegative(
                diagnosticExpiryBeforeTick,
                "diagnosticExpiryBeforeTick"
        );
        hotEvidenceLimit = EvidenceValidation.nonNegative(hotEvidenceLimit, "hotEvidenceLimit");
        diagnosticEvidenceLimit = EvidenceValidation.nonNegative(
                diagnosticEvidenceLimit,
                "diagnosticEvidenceLimit"
        );
    }
}
