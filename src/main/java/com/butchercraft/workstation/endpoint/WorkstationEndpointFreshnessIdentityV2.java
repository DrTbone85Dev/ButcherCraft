package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointFreshnessIdentityV2(String value) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_freshness/v2/";

    public WorkstationEndpointFreshnessIdentityV2 {
        value = WorkstationEndpointValidation.id(value, "schema-2 endpoint freshness identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported schema-2 endpoint Freshness Identity prefix");
        }
    }

    public static WorkstationEndpointFreshnessIdentityV2 create(
            WorkstationInstanceId instanceId,
            int slotIndex,
            long inventoryRevision,
            long endpointEffectRevision,
            WorkstationEndpointStackStateV2 slotState,
            boolean endpointLocked,
            Optional<WorkstationEndpointEffectIdV2> activeEffectId,
            String operationStateIdentity,
            long ownerResultJournalSequence,
            long journalRevision,
            int effectiveSlotCapacity,
            String configurationIdentity
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (slotIndex < 0) throw new IllegalArgumentException("Endpoint slot index must not be negative");
        WorkstationEndpointValidation.nonNegative(inventoryRevision, "inventory revision");
        WorkstationEndpointValidation.nonNegative(endpointEffectRevision, "endpoint effect revision");
        Objects.requireNonNull(slotState, "slotState");
        activeEffectId = Objects.requireNonNull(activeEffectId, "activeEffectId");
        if (endpointLocked != activeEffectId.isPresent()) {
            throw new IllegalArgumentException("Endpoint lock state must match its active Effect Identity");
        }
        operationStateIdentity = WorkstationEndpointValidation.id(operationStateIdentity, "operation state identity");
        WorkstationEndpointValidation.nonNegative(ownerResultJournalSequence, "owner-result journal sequence");
        WorkstationEndpointValidation.nonNegative(journalRevision, "journal revision");
        WorkstationEndpointValidation.positive(effectiveSlotCapacity, "effective slot capacity");
        configurationIdentity = WorkstationEndpointValidation.id(configurationIdentity, "configuration identity");
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_freshness")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(instanceId.value())
                .add(slotIndex)
                .add(inventoryRevision)
                .add(endpointEffectRevision)
                .add(slotState.contentIdentity())
                .add(endpointLocked)
                .add(activeEffectId.map(WorkstationEndpointEffectIdV2::value).orElse(""))
                .add(operationStateIdentity)
                .add(ownerResultJournalSequence)
                .add(journalRevision)
                .add(effectiveSlotCapacity)
                .add(configurationIdentity)
                .finish();
        return new WorkstationEndpointFreshnessIdentityV2(PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest));
    }
}
