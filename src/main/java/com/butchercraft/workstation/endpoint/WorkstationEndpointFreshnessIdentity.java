package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointFreshnessIdentity(String value) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_freshness/v1/";
    public static final String EMPTY_SLOT_CONTENT_DIGEST = WorkstationEndpointCanonicalDigest
            .create("butchercraft:workstation_endpoint_empty_slot")
            .finish();

    public WorkstationEndpointFreshnessIdentity {
        value = WorkstationEndpointValidation.id(value, "Workstation endpoint freshness identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported Workstation endpoint freshness identity prefix");
        }
    }

    public static WorkstationEndpointFreshnessIdentity create(
            WorkstationInstanceId instanceId,
            int slotIndex,
            long inventoryRevision,
            long endpointEffectRevision,
            String slotContentDigest,
            boolean endpointLocked,
            Optional<WorkstationEndpointEffectId> activeEffectId,
            String operationStateIdentity,
            long ownerResultJournalSequence,
            String configurationIdentity
    ) {
        if (slotIndex < 0) throw new IllegalArgumentException("Endpoint slot index must not be negative");
        WorkstationEndpointValidation.nonNegative(inventoryRevision, "inventory revision");
        WorkstationEndpointValidation.nonNegative(endpointEffectRevision, "endpoint effect revision");
        WorkstationEndpointValidation.digest(slotContentDigest, "slot content digest");
        activeEffectId = Objects.requireNonNull(activeEffectId, "activeEffectId");
        if (endpointLocked != activeEffectId.isPresent()) {
            throw new IllegalArgumentException("Endpoint lock state must match its active Effect Identity");
        }
        WorkstationEndpointValidation.id(operationStateIdentity, "endpoint operation state identity");
        WorkstationEndpointValidation.nonNegative(ownerResultJournalSequence, "owner-result journal sequence");
        WorkstationEndpointValidation.id(configurationIdentity, "endpoint configuration identity");
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_freshness")
                .add(WorkstationEndpointSchema.CURRENT_VERSION)
                .add(instanceId.value())
                .add(slotIndex)
                .add(inventoryRevision)
                .add(endpointEffectRevision)
                .add(slotContentDigest)
                .add(endpointLocked)
                .add(activeEffectId.map(WorkstationEndpointEffectId::value).orElse(""))
                .add(operationStateIdentity)
                .add(ownerResultJournalSequence)
                .add(configurationIdentity)
                .finish();
        return new WorkstationEndpointFreshnessIdentity(PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest));
    }
}
