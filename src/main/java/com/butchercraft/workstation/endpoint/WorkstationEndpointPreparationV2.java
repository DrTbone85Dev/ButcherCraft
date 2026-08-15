package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointPreparationV2(
        String evidenceIdentity,
        String contentDigest,
        int protocolVersion,
        long journalSequence,
        long committedJournalRevision,
        WorkstationEndpointEffectIdV2 effectId,
        String invocationIdentity,
        WorkstationEndpointObservationV2 observation,
        long postInventoryRevision,
        long postEndpointEffectRevision,
        String postOperationStateIdentity,
        WorkstationEndpointFreshnessIdentityV2 postFreshnessIdentity
) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_preparation/v2/";

    public WorkstationEndpointPreparationV2 {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "preparation evidence identity");
        if (!evidenceIdentity.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported preparation prefix");
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "preparation content digest");
        if (protocolVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported stack-aware endpoint protocol version");
        }
        WorkstationEndpointValidation.positive(journalSequence, "journal sequence");
        WorkstationEndpointValidation.positive(committedJournalRevision, "committed journal revision");
        effectId = Objects.requireNonNull(effectId, "effectId");
        invocationIdentity = WorkstationEndpointValidation.id(invocationIdentity, "invocation identity");
        observation = Objects.requireNonNull(observation, "observation");
        if (!effectId.equals(WorkstationEndpointEffectIdV2.create(
                observation.instanceId(), invocationIdentity, observation.effectKind()))) {
            throw new IllegalArgumentException("Preparation Effect Identity is not canonical");
        }
        if (postInventoryRevision != Math.addExact(observation.inventoryRevision(), 1L)
                || postEndpointEffectRevision != Math.addExact(observation.endpointEffectRevision(), 1L)) {
            throw new IllegalArgumentException("Preparation must bind exact next endpoint revisions");
        }
        postOperationStateIdentity = WorkstationEndpointValidation.id(
                postOperationStateIdentity,
                "post-operation state identity"
        );
        postFreshnessIdentity = Objects.requireNonNull(postFreshnessIdentity, "postFreshnessIdentity");
        WorkstationEndpointFreshnessIdentityV2 expectedPostFreshness = WorkstationEndpointFreshnessIdentityV2.create(
                observation.instanceId(), observation.slotIndex(), postInventoryRevision, postEndpointEffectRevision,
                observation.postStack(), false, Optional.empty(), postOperationStateIdentity, journalSequence,
                committedJournalRevision, observation.effectiveSlotCapacity(),
                observation.endpointConfigurationIdentity()
        );
        if (!expectedPostFreshness.equals(postFreshnessIdentity)) {
            throw new IllegalArgumentException("Preparation post-freshness identity is not canonical");
        }
        String expectedDigest = digest(journalSequence, committedJournalRevision, effectId, invocationIdentity,
                observation, postInventoryRevision, postEndpointEffectRevision, postOperationStateIdentity,
                postFreshnessIdentity);
        if (!expectedDigest.equals(contentDigest)
                || !(PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Schema-2 preparation evidence is not canonical");
        }
    }

    public static WorkstationEndpointPreparationV2 create(
            long journalSequence,
            long committedJournalRevision,
            String invocationIdentity,
            WorkstationEndpointObservationV2 observation,
            String postOperationStateIdentity
    ) {
        WorkstationEndpointEffectIdV2 effectId = WorkstationEndpointEffectIdV2.create(
                observation.instanceId(), invocationIdentity, observation.effectKind()
        );
        long postInventoryRevision = Math.addExact(observation.inventoryRevision(), 1L);
        long postEndpointEffectRevision = Math.addExact(observation.endpointEffectRevision(), 1L);
        WorkstationEndpointFreshnessIdentityV2 postFreshness = WorkstationEndpointFreshnessIdentityV2.create(
                observation.instanceId(), observation.slotIndex(), postInventoryRevision, postEndpointEffectRevision,
                observation.postStack(), false, Optional.empty(), postOperationStateIdentity, journalSequence,
                committedJournalRevision, observation.effectiveSlotCapacity(),
                observation.endpointConfigurationIdentity()
        );
        String digest = digest(journalSequence, committedJournalRevision, effectId, invocationIdentity, observation,
                postInventoryRevision, postEndpointEffectRevision, postOperationStateIdentity, postFreshness);
        return new WorkstationEndpointPreparationV2(
                PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest), digest,
                WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION, journalSequence,
                committedJournalRevision, effectId, invocationIdentity, observation, postInventoryRevision,
                postEndpointEffectRevision, postOperationStateIdentity, postFreshness
        );
    }

    private static String digest(
            long journalSequence, long committedJournalRevision, WorkstationEndpointEffectIdV2 effectId,
            String invocationIdentity, WorkstationEndpointObservationV2 observation, long postInventoryRevision,
            long postEndpointEffectRevision, String postOperationStateIdentity,
            WorkstationEndpointFreshnessIdentityV2 postFreshnessIdentity
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_preparation")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION).add(journalSequence)
                .add(committedJournalRevision).add(effectId.value()).add(invocationIdentity)
                .add(observation.evidenceIdentity()).add(postInventoryRevision).add(postEndpointEffectRevision)
                .add(postOperationStateIdentity).add(postFreshnessIdentity.value()).finish();
    }
}
