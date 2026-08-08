package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointProjection(
        Optional<WorkstationInstanceId> instanceId,
        long instanceGeneration,
        long inventoryRevision,
        long endpointEffectRevision,
        long lastAppliedJournalSequence,
        Optional<WorkstationEndpointEffectId> lastEffectId,
        Optional<String> lastOwnerResultIdentity,
        Optional<WorkstationEndpointEffectId> preparedEffectId,
        int preparedSlotIndex,
        long preparedInventoryRevision
) {
    public WorkstationEndpointProjection {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        if (instanceGeneration < 0L || inventoryRevision < 0L || endpointEffectRevision < 0L
                || lastAppliedJournalSequence < 0L || preparedInventoryRevision < 0L) {
            throw new IllegalArgumentException("Workstation endpoint projection revisions must not be negative");
        }
        if (instanceId.isEmpty() && instanceGeneration != 0L) {
            throw new IllegalArgumentException("Unbound endpoint projection cannot retain a generation");
        }
        if (instanceId.isPresent() && instanceGeneration <= 0L) {
            throw new IllegalArgumentException("Bound endpoint projection requires a positive generation");
        }
        lastEffectId = Objects.requireNonNull(lastEffectId, "lastEffectId");
        lastOwnerResultIdentity = Objects.requireNonNull(lastOwnerResultIdentity, "lastOwnerResultIdentity");
        if (lastEffectId.isEmpty() != lastOwnerResultIdentity.isEmpty()) {
            throw new IllegalArgumentException("Endpoint effect and owner result markers must be published together");
        }
        if (lastEffectId.isEmpty() != (lastAppliedJournalSequence == 0L)) {
            throw new IllegalArgumentException("Last-applied journal sequence must accompany endpoint result markers");
        }
        preparedEffectId = Objects.requireNonNull(preparedEffectId, "preparedEffectId");
        if (preparedEffectId.isEmpty() && (preparedSlotIndex != -1 || preparedInventoryRevision != 0L)) {
            throw new IllegalArgumentException("Unlocked endpoint projection cannot retain prepared lock fields");
        }
        if (preparedEffectId.isPresent() && preparedSlotIndex < 0) {
            throw new IllegalArgumentException("Prepared endpoint lock requires a slot index");
        }
    }

    public static WorkstationEndpointProjection unbound(long inventoryRevision) {
        return new WorkstationEndpointProjection(
                Optional.empty(),
                0L,
                inventoryRevision,
                0L,
                0L,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                -1,
                0L
        );
    }
}
