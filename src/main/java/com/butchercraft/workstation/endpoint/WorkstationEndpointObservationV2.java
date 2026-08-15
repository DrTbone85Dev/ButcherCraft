package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointObservationV2(
        String evidenceIdentity,
        String contentDigest,
        int protocolVersion,
        WorkstationInstanceId instanceId,
        WorkstationEndpointKey endpointKey,
        WorkstationEndpointEffectKind effectKind,
        int slotIndex,
        int requestedQuantity,
        WorkstationEndpointStackStateV2 preStack,
        WorkstationEndpointStackStateV2 transferStack,
        WorkstationEndpointStackStateV2 remainderStack,
        WorkstationEndpointStackStateV2 postStack,
        long inventoryRevision,
        long endpointEffectRevision,
        String operationStateIdentity,
        long ownerResultJournalSequence,
        long journalRevision,
        int effectiveSlotCapacity,
        WorkstationEndpointFreshnessIdentityV2 freshnessIdentity,
        String endpointConfigurationIdentity
) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_observation/v2/";

    public WorkstationEndpointObservationV2 {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "observation evidence identity");
        if (!evidenceIdentity.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported observation prefix");
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "observation content digest");
        if (protocolVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported stack-aware endpoint protocol version");
        }
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        effectKind = Objects.requireNonNull(effectKind, "effectKind");
        if (slotIndex < 0) throw new IllegalArgumentException("Endpoint slot index must not be negative");
        WorkstationEndpointValidation.positive(requestedQuantity, "requested quantity");
        preStack = Objects.requireNonNull(preStack, "preStack");
        transferStack = Objects.requireNonNull(transferStack, "transferStack");
        remainderStack = Objects.requireNonNull(remainderStack, "remainderStack");
        postStack = Objects.requireNonNull(postStack, "postStack");
        WorkstationEndpointValidation.nonNegative(inventoryRevision, "inventory revision");
        WorkstationEndpointValidation.nonNegative(endpointEffectRevision, "endpoint effect revision");
        operationStateIdentity = WorkstationEndpointValidation.id(operationStateIdentity, "operation state identity");
        WorkstationEndpointValidation.nonNegative(ownerResultJournalSequence, "owner-result journal sequence");
        WorkstationEndpointValidation.nonNegative(journalRevision, "journal revision");
        WorkstationEndpointValidation.positive(effectiveSlotCapacity, "effective slot capacity");
        freshnessIdentity = Objects.requireNonNull(freshnessIdentity, "freshnessIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        validateEffect(effectKind, requestedQuantity, preStack, transferStack, remainderStack, postStack,
                effectiveSlotCapacity);
        WorkstationEndpointFreshnessIdentityV2 expectedFreshness = WorkstationEndpointFreshnessIdentityV2.create(
                instanceId, slotIndex, inventoryRevision, endpointEffectRevision, preStack, false, Optional.empty(),
                operationStateIdentity, ownerResultJournalSequence, journalRevision, effectiveSlotCapacity,
                endpointConfigurationIdentity
        );
        if (!expectedFreshness.equals(freshnessIdentity)) {
            throw new IllegalArgumentException("Schema-2 observation freshness is not canonical");
        }
        String expectedDigest = digest(instanceId, endpointKey, effectKind, slotIndex, requestedQuantity, preStack,
                transferStack, remainderStack, postStack, inventoryRevision, endpointEffectRevision,
                operationStateIdentity, ownerResultJournalSequence, journalRevision, effectiveSlotCapacity,
                freshnessIdentity, endpointConfigurationIdentity);
        if (!expectedDigest.equals(contentDigest)
                || !(PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Schema-2 observation evidence is not canonical");
        }
    }

    public static WorkstationEndpointObservationV2 create(
            WorkstationInstanceId instanceId,
            WorkstationEndpointKey endpointKey,
            WorkstationEndpointEffectKind effectKind,
            int slotIndex,
            int requestedQuantity,
            WorkstationEndpointStackStateV2 preStack,
            WorkstationEndpointStackStateV2 transferStack,
            WorkstationEndpointStackStateV2 remainderStack,
            WorkstationEndpointStackStateV2 postStack,
            long inventoryRevision,
            long endpointEffectRevision,
            String operationStateIdentity,
            long ownerResultJournalSequence,
            long journalRevision,
            int effectiveSlotCapacity,
            String endpointConfigurationIdentity
    ) {
        WorkstationEndpointFreshnessIdentityV2 freshness = WorkstationEndpointFreshnessIdentityV2.create(
                instanceId, slotIndex, inventoryRevision, endpointEffectRevision, preStack, false, Optional.empty(),
                operationStateIdentity, ownerResultJournalSequence, journalRevision, effectiveSlotCapacity,
                endpointConfigurationIdentity
        );
        String digest = digest(instanceId, endpointKey, effectKind, slotIndex, requestedQuantity, preStack,
                transferStack, remainderStack, postStack, inventoryRevision, endpointEffectRevision,
                operationStateIdentity, ownerResultJournalSequence, journalRevision, effectiveSlotCapacity,
                freshness, endpointConfigurationIdentity);
        return new WorkstationEndpointObservationV2(
                PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest), digest,
                WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION, instanceId, endpointKey, effectKind,
                slotIndex, requestedQuantity, preStack, transferStack, remainderStack, postStack, inventoryRevision,
                endpointEffectRevision, operationStateIdentity, ownerResultJournalSequence, journalRevision,
                effectiveSlotCapacity, freshness, endpointConfigurationIdentity
        );
    }

    static void validateEffect(
            WorkstationEndpointEffectKind kind,
            int quantity,
            WorkstationEndpointStackStateV2 pre,
            WorkstationEndpointStackStateV2 transfer,
            WorkstationEndpointStackStateV2 remainder,
            WorkstationEndpointStackStateV2 post,
            int capacity
    ) {
        if (transfer.isEmpty() || transfer.count() != quantity) {
            throw new IllegalArgumentException("Transfer payload count must equal requested quantity");
        }
        if (post.count() > capacity) throw new IllegalArgumentException("Post-stack exceeds effective capacity");
        if (kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL) {
            if (pre.isEmpty() || quantity > pre.count() || !pre.compatibleWith(transfer)) {
                throw new IllegalArgumentException("Withdrawal does not bind a compatible sufficient pre-stack");
            }
            int expectedRemainder = pre.count() - quantity;
            if (remainder.count() != expectedRemainder || !remainder.equals(post)
                    || (expectedRemainder > 0 && !pre.compatibleWith(remainder))) {
                throw new IllegalArgumentException("Withdrawal remainder/post-state is not exact");
            }
        } else {
            if (!remainder.isEmpty()) throw new IllegalArgumentException("Merge effect must use empty remainder state");
            if (!pre.isEmpty() && !pre.compatibleWith(transfer)) {
                throw new IllegalArgumentException("Merge pre-stack is incompatible with transfer payload");
            }
            if (post.count() != pre.count() + quantity || !post.compatibleWith(transfer)) {
                throw new IllegalArgumentException("Merge post-state is not the exact all-or-nothing result");
            }
        }
    }

    private static String digest(
            WorkstationInstanceId instanceId, WorkstationEndpointKey endpointKey,
            WorkstationEndpointEffectKind effectKind, int slotIndex, int requestedQuantity,
            WorkstationEndpointStackStateV2 preStack, WorkstationEndpointStackStateV2 transferStack,
            WorkstationEndpointStackStateV2 remainderStack, WorkstationEndpointStackStateV2 postStack,
            long inventoryRevision, long endpointEffectRevision, String operationStateIdentity,
            long ownerResultJournalSequence, long journalRevision, int effectiveSlotCapacity,
            WorkstationEndpointFreshnessIdentityV2 freshnessIdentity, String endpointConfigurationIdentity
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_observation")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(instanceId.value()).add(endpointKey.canonicalValue()).add(effectKind.name()).add(slotIndex)
                .add(requestedQuantity).add(preStack.contentIdentity()).add(transferStack.contentIdentity())
                .add(remainderStack.contentIdentity()).add(postStack.contentIdentity()).add(inventoryRevision)
                .add(endpointEffectRevision).add(operationStateIdentity).add(ownerResultJournalSequence)
                .add(journalRevision).add(effectiveSlotCapacity).add(freshnessIdentity.value())
                .add(endpointConfigurationIdentity).finish();
    }
}
