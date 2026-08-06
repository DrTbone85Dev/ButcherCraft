package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import net.minecraft.world.item.ItemStack;

public interface WorkstationTransferEndpoint {
    String endpointTypeIdentity();

    String endpointOperationStateIdentity();

    String endpointPostOperationStateIdentity(WorkstationEndpointEffectKind kind);

    WorkstationEndpointProjection endpointProjection();

    ItemStack endpointStackSnapshot(int slotIndex);

    boolean endpointAccepts(WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactStack);

    void bindEndpointInstance(WorkstationInstanceId instanceId, long generation);

    void lockPreparedEndpointEffect(
            WorkstationEndpointEffectId effectId,
            int slotIndex,
            long expectedInventoryRevision
    );

    void releasePreparedEndpointEffect(WorkstationEndpointEffectId effectId);

    void applyCommittedEndpointEffect(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactStack,
            long expectedInventoryRevision,
            long postInventoryRevision,
            long endpointEffectRevision,
            long journalSequence,
            WorkstationEndpointEffectId effectId,
            String ownerResultIdentity
    );
}
