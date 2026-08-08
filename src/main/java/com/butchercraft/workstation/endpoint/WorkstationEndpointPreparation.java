package com.butchercraft.workstation.endpoint;

import java.util.Objects;

public record WorkstationEndpointPreparation(
        String evidenceIdentity,
        String contentDigest,
        long journalSequence,
        WorkstationEndpointEffectId effectId,
        WorkstationInstanceId instanceId,
        String invocationIdentity,
        WorkstationEndpointEffectKind effectKind,
        int slotIndex,
        WorkstationEndpointStackPayload exactStack,
        long expectedInventoryRevision,
        long expectedEndpointEffectRevision,
        WorkstationEndpointFreshnessIdentity preFreshnessIdentity,
        WorkstationEndpointFreshnessIdentity postFreshnessIdentity,
        String endpointConfigurationIdentity
) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_preparation/v1/";

    public WorkstationEndpointPreparation {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "endpoint preparation evidence identity");
        if (!evidenceIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Endpoint preparation identity has unsupported prefix");
        }
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "endpoint preparation content digest");
        journalSequence = WorkstationEndpointValidation.positive(journalSequence, "endpoint journal sequence");
        effectId = Objects.requireNonNull(effectId, "effectId");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        invocationIdentity = WorkstationEndpointValidation.id(invocationIdentity, "endpoint invocation identity");
        effectKind = Objects.requireNonNull(effectKind, "effectKind");
        if (slotIndex < 0) throw new IllegalArgumentException("Endpoint slot index must not be negative");
        exactStack = Objects.requireNonNull(exactStack, "exactStack");
        expectedInventoryRevision = WorkstationEndpointValidation.nonNegative(
                expectedInventoryRevision,
                "expected inventory revision"
        );
        expectedEndpointEffectRevision = WorkstationEndpointValidation.nonNegative(
                expectedEndpointEffectRevision,
                "expected endpoint effect revision"
        );
        preFreshnessIdentity = Objects.requireNonNull(preFreshnessIdentity, "preFreshnessIdentity");
        postFreshnessIdentity = Objects.requireNonNull(postFreshnessIdentity, "postFreshnessIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        String expectedDigest = digest(
                journalSequence,
                effectId,
                instanceId,
                invocationIdentity,
                effectKind,
                slotIndex,
                exactStack,
                expectedInventoryRevision,
                expectedEndpointEffectRevision,
                preFreshnessIdentity,
                postFreshnessIdentity,
                endpointConfigurationIdentity
        );
        if (!expectedDigest.equals(contentDigest)
                || !(PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Endpoint preparation evidence is not canonical");
        }
    }

    public static WorkstationEndpointPreparation from(WorkstationEndpointJournalRecord record) {
        if (record.state() != WorkstationEndpointJournalState.PREPARED
                && record.state() != WorkstationEndpointJournalState.EFFECT_COMMITTED
                && record.state() != WorkstationEndpointJournalState.RESULT_PUBLISHED
                && record.state() != WorkstationEndpointJournalState.RECONCILED) {
            throw new IllegalArgumentException("Journal record does not represent an accepted preparation");
        }
        String digest = digest(
                record.journalSequence(),
                record.effectId(),
                record.instanceId(),
                record.invocationIdentity(),
                record.effectKind(),
                record.slotIndex(),
                record.exactStack(),
                record.expectedInventoryRevision(),
                record.expectedEndpointEffectRevision(),
                record.preFreshnessIdentity(),
                record.postFreshnessIdentity(),
                record.endpointConfigurationIdentity()
        );
        return new WorkstationEndpointPreparation(
                PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest),
                digest,
                record.journalSequence(),
                record.effectId(),
                record.instanceId(),
                record.invocationIdentity(),
                record.effectKind(),
                record.slotIndex(),
                record.exactStack(),
                record.expectedInventoryRevision(),
                record.expectedEndpointEffectRevision(),
                record.preFreshnessIdentity(),
                record.postFreshnessIdentity(),
                record.endpointConfigurationIdentity()
        );
    }

    private static String digest(
            long journalSequence,
            WorkstationEndpointEffectId effectId,
            WorkstationInstanceId instanceId,
            String invocationIdentity,
            WorkstationEndpointEffectKind effectKind,
            int slotIndex,
            WorkstationEndpointStackPayload exactStack,
            long expectedInventoryRevision,
            long expectedEndpointEffectRevision,
            WorkstationEndpointFreshnessIdentity preFreshnessIdentity,
            WorkstationEndpointFreshnessIdentity postFreshnessIdentity,
            String endpointConfigurationIdentity
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_preparation")
                .add(journalSequence)
                .add(effectId.value())
                .add(instanceId.value())
                .add(invocationIdentity)
                .add(effectKind.name())
                .add(slotIndex)
                .add(exactStack.contentDigest())
                .add(expectedInventoryRevision)
                .add(expectedEndpointEffectRevision)
                .add(preFreshnessIdentity.value())
                .add(postFreshnessIdentity.value())
                .add(endpointConfigurationIdentity)
                .finish();
    }
}
