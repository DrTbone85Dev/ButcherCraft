package com.butchercraft.workstation.endpoint;

import java.util.Objects;

public record WorkstationEndpointObservation(
        String evidenceIdentity,
        String contentDigest,
        WorkstationInstanceId instanceId,
        WorkstationEndpointEffectKind effectKind,
        int slotIndex,
        WorkstationEndpointStackPayload exactEffectStack,
        String observedSlotContentDigest,
        long inventoryRevision,
        long endpointEffectRevision,
        String operationStateIdentity,
        long ownerResultJournalSequence,
        WorkstationEndpointFreshnessIdentity freshnessIdentity,
        String endpointConfigurationIdentity
) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_observation/v1/";

    public WorkstationEndpointObservation {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "endpoint observation evidence identity");
        if (!evidenceIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Endpoint observation identity has unsupported prefix");
        }
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "endpoint observation content digest");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        effectKind = Objects.requireNonNull(effectKind, "effectKind");
        if (slotIndex < 0) throw new IllegalArgumentException("Endpoint observation slot index must not be negative");
        exactEffectStack = Objects.requireNonNull(exactEffectStack, "exactEffectStack");
        observedSlotContentDigest = WorkstationEndpointValidation.digest(
                observedSlotContentDigest,
                "observed slot content digest"
        );
        inventoryRevision = WorkstationEndpointValidation.nonNegative(inventoryRevision, "inventory revision");
        endpointEffectRevision = WorkstationEndpointValidation.nonNegative(
                endpointEffectRevision,
                "endpoint effect revision"
        );
        operationStateIdentity = WorkstationEndpointValidation.id(
                operationStateIdentity,
                "endpoint operation state identity"
        );
        ownerResultJournalSequence = WorkstationEndpointValidation.nonNegative(
                ownerResultJournalSequence,
                "owner-result journal sequence"
        );
        freshnessIdentity = Objects.requireNonNull(freshnessIdentity, "freshnessIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        String expectedSlotDigest = effectKind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? exactEffectStack.contentDigest()
                : WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST;
        if (!expectedSlotDigest.equals(observedSlotContentDigest)) {
            throw new IllegalArgumentException("Endpoint observation does not bind the required pre-state");
        }
        WorkstationEndpointFreshnessIdentity expectedFreshness = WorkstationEndpointFreshnessIdentity.create(
                instanceId,
                slotIndex,
                inventoryRevision,
                endpointEffectRevision,
                observedSlotContentDigest,
                false,
                java.util.Optional.empty(),
                operationStateIdentity,
                ownerResultJournalSequence,
                endpointConfigurationIdentity
        );
        if (!expectedFreshness.equals(freshnessIdentity)) {
            throw new IllegalArgumentException("Endpoint observation freshness identity is not canonical");
        }
        String expectedDigest = digest(
                instanceId,
                effectKind,
                slotIndex,
                exactEffectStack,
                observedSlotContentDigest,
                inventoryRevision,
                endpointEffectRevision,
                operationStateIdentity,
                ownerResultJournalSequence,
                freshnessIdentity,
                endpointConfigurationIdentity
        );
        if (!expectedDigest.equals(contentDigest)
                || !(PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Endpoint observation evidence is not canonical");
        }
    }

    public static WorkstationEndpointObservation create(
            WorkstationInstanceId instanceId,
            WorkstationEndpointEffectKind effectKind,
            int slotIndex,
            WorkstationEndpointStackPayload exactEffectStack,
            long inventoryRevision,
            long endpointEffectRevision,
            String operationStateIdentity,
            long ownerResultJournalSequence,
            String endpointConfigurationIdentity
    ) {
        String slotDigest = effectKind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? exactEffectStack.contentDigest()
                : WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST;
        WorkstationEndpointFreshnessIdentity freshness = WorkstationEndpointFreshnessIdentity.create(
                instanceId,
                slotIndex,
                inventoryRevision,
                endpointEffectRevision,
                slotDigest,
                false,
                java.util.Optional.empty(),
                operationStateIdentity,
                ownerResultJournalSequence,
                endpointConfigurationIdentity
        );
        String digest = digest(
                instanceId,
                effectKind,
                slotIndex,
                exactEffectStack,
                slotDigest,
                inventoryRevision,
                endpointEffectRevision,
                operationStateIdentity,
                ownerResultJournalSequence,
                freshness,
                endpointConfigurationIdentity
        );
        return new WorkstationEndpointObservation(
                PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest),
                digest,
                instanceId,
                effectKind,
                slotIndex,
                exactEffectStack,
                slotDigest,
                inventoryRevision,
                endpointEffectRevision,
                operationStateIdentity,
                ownerResultJournalSequence,
                freshness,
                endpointConfigurationIdentity
        );
    }

    private static String digest(
            WorkstationInstanceId instanceId,
            WorkstationEndpointEffectKind effectKind,
            int slotIndex,
            WorkstationEndpointStackPayload exactEffectStack,
            String observedSlotContentDigest,
            long inventoryRevision,
            long endpointEffectRevision,
            String operationStateIdentity,
            long ownerResultJournalSequence,
            WorkstationEndpointFreshnessIdentity freshnessIdentity,
            String endpointConfigurationIdentity
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_observation")
                .add(instanceId.value())
                .add(effectKind.name())
                .add(slotIndex)
                .add(exactEffectStack.contentDigest())
                .add(observedSlotContentDigest)
                .add(inventoryRevision)
                .add(endpointEffectRevision)
                .add(operationStateIdentity)
                .add(ownerResultJournalSequence)
                .add(freshnessIdentity.value())
                .add(endpointConfigurationIdentity)
                .finish();
    }
}
