package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Workstation-owned projection contract for schema-2 effects. Not registered by live routes in IM-030A. */
public interface StackAwareWorkstationTransferEndpoint {
    void activateStackAwareEndpoint();

    WorkstationInstanceId endpointInstanceId();

    WorkstationEndpointKey endpointKey();

    String endpointOperationStateIdentity();

    String endpointPostOperationStateIdentity(WorkstationEndpointObservationV2 observation);

    String endpointConfigurationIdentity();

    int endpointSlotIndex(WorkstationEndpointEffectKind kind);

    ItemStack endpointStackSnapshot(int slotIndex);

    int endpointEffectiveCapacity(int slotIndex, ItemStack stack);

    long endpointInventoryRevision();

    long endpointEffectRevision();

    long endpointLastAppliedJournalSequence();

    Optional<WorkstationEndpointEffectIdV2> endpointPreparedEffectId();

    Optional<WorkstationEndpointEffectIdV2> endpointLastEffectId();

    Optional<String> endpointLastOwnerResultIdentity();

    boolean endpointAcceptsCandidate(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactPreStack,
            ItemStack exactPostStack
    );

    void lockPreparedEndpointEffect(
            WorkstationEndpointEffectIdV2 effectId,
            int slotIndex,
            long expectedInventoryRevision
    );

    void releasePreparedEndpointEffect(WorkstationEndpointEffectIdV2 effectId);

    void applyCommittedEndpointEffect(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactPreStack,
            ItemStack exactPostStack,
            long expectedInventoryRevision,
            long postInventoryRevision,
            long endpointEffectRevision,
            long journalSequence,
            WorkstationEndpointEffectIdV2 effectId,
            String ownerResultIdentity
    );
}
