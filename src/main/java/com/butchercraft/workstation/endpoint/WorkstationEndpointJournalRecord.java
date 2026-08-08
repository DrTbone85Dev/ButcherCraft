package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointJournalRecord(
        int schemaVersion,
        long journalSequence,
        WorkstationEndpointEffectId effectId,
        WorkstationInstanceId instanceId,
        String invocationIdentity,
        WorkstationEndpointEffectKind effectKind,
        int slotIndex,
        WorkstationEndpointStackPayload exactStack,
        long expectedInventoryRevision,
        long postInventoryRevision,
        long expectedEndpointEffectRevision,
        long endpointEffectRevision,
        String preOperationStateIdentity,
        String postOperationStateIdentity,
        long previousOwnerResultJournalSequence,
        WorkstationEndpointFreshnessIdentity preFreshnessIdentity,
        WorkstationEndpointFreshnessIdentity postFreshnessIdentity,
        String endpointConfigurationIdentity,
        WorkstationEndpointJournalState state,
        long creationRevision,
        long lastUpdateRevision,
        Optional<WorkstationEndpointOwnerResult> ownerResult,
        Optional<String> failureDetail
) implements Comparable<WorkstationEndpointJournalRecord> {
    public WorkstationEndpointJournalRecord {
        schemaVersion = WorkstationEndpointValidation.schema(schemaVersion, "endpoint journal record schema version");
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
        postInventoryRevision = WorkstationEndpointValidation.nonNegative(
                postInventoryRevision,
                "post-inventory revision"
        );
        expectedEndpointEffectRevision = WorkstationEndpointValidation.nonNegative(
                expectedEndpointEffectRevision,
                "expected endpoint effect revision"
        );
        endpointEffectRevision = WorkstationEndpointValidation.nonNegative(
                endpointEffectRevision,
                "endpoint effect revision"
        );
        preOperationStateIdentity = WorkstationEndpointValidation.id(
                preOperationStateIdentity,
                "pre-effect operation state identity"
        );
        postOperationStateIdentity = WorkstationEndpointValidation.id(
                postOperationStateIdentity,
                "post-effect operation state identity"
        );
        previousOwnerResultJournalSequence = WorkstationEndpointValidation.nonNegative(
                previousOwnerResultJournalSequence,
                "previous owner-result journal sequence"
        );
        preFreshnessIdentity = Objects.requireNonNull(preFreshnessIdentity, "preFreshnessIdentity");
        postFreshnessIdentity = Objects.requireNonNull(postFreshnessIdentity, "postFreshnessIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        state = Objects.requireNonNull(state, "state");
        creationRevision = WorkstationEndpointValidation.positive(creationRevision, "journal creation revision");
        lastUpdateRevision = WorkstationEndpointValidation.positive(lastUpdateRevision, "journal update revision");
        if (lastUpdateRevision < creationRevision) {
            throw new IllegalArgumentException("Journal update revision cannot precede creation revision");
        }
        ownerResult = Objects.requireNonNull(ownerResult, "ownerResult");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(value -> WorkstationEndpointValidation.text(value, "journal failure detail"));
        WorkstationEndpointEffectId expectedEffectId = WorkstationEndpointEffectId.create(
                instanceId,
                invocationIdentity,
                effectKind
        );
        if (!expectedEffectId.equals(effectId)) {
            throw new IllegalArgumentException("Endpoint effect identity does not match canonical inputs");
        }
        if (postInventoryRevision != Math.addExact(expectedInventoryRevision, 1L)
                || endpointEffectRevision != Math.addExact(expectedEndpointEffectRevision, 1L)) {
            throw new IllegalArgumentException("Endpoint journal must freeze the exact next post-state revisions");
        }
        String preContentDigest = effectKind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? exactStack.contentDigest()
                : WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST;
        String postContentDigest = effectKind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST
                : exactStack.contentDigest();
        if (!preFreshnessIdentity.equals(WorkstationEndpointFreshnessIdentity.create(
                instanceId,
                slotIndex,
                expectedInventoryRevision,
                expectedEndpointEffectRevision,
                preContentDigest,
                false,
                Optional.empty(),
                preOperationStateIdentity,
                previousOwnerResultJournalSequence,
                endpointConfigurationIdentity
        )) || !postFreshnessIdentity.equals(WorkstationEndpointFreshnessIdentity.create(
                instanceId,
                slotIndex,
                postInventoryRevision,
                endpointEffectRevision,
                postContentDigest,
                false,
                Optional.empty(),
                postOperationStateIdentity,
                journalSequence,
                endpointConfigurationIdentity
        ))) {
            throw new IllegalArgumentException("Endpoint journal freshness identities are not canonical");
        }
        if (state == WorkstationEndpointJournalState.EFFECT_COMMITTED && ownerResult.isEmpty()) {
            throw new IllegalArgumentException("EFFECT_COMMITTED must freeze the immutable owner result");
        }
        if ((state == WorkstationEndpointJournalState.RESULT_PUBLISHED
                || state == WorkstationEndpointJournalState.RECONCILED) && ownerResult.isEmpty()) {
            throw new IllegalArgumentException("Published endpoint effect requires an owner result");
        }
        WorkstationEndpointOwnerResult result = ownerResult.orElse(null);
        if (result != null) {
            if (!result.effectId().equals(effectId)
                    || result.schemaVersion() != schemaVersion
                    || result.journalSequence() != journalSequence
                    || !result.instanceId().equals(instanceId)
                    || !result.invocationIdentity().equals(invocationIdentity)
                    || result.effectKind() != effectKind
                    || !result.exactStack().equals(exactStack)
                    || result.preInventoryRevision() != expectedInventoryRevision
                    || result.postInventoryRevision() != postInventoryRevision
                    || result.preEndpointEffectRevision() != expectedEndpointEffectRevision
                    || result.endpointEffectRevision() != endpointEffectRevision
                    || !result.preFreshnessIdentity().equals(preFreshnessIdentity)
                    || !result.postFreshnessIdentity().equals(postFreshnessIdentity)
                    || !result.endpointConfigurationIdentity().equals(endpointConfigurationIdentity)) {
                throw new IllegalArgumentException("Endpoint owner result does not bind the journal record");
            }
        }
    }

    public static WorkstationEndpointJournalRecord requested(
            long journalSequence,
            WorkstationInstanceId instanceId,
            String invocationIdentity,
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            WorkstationEndpointStackPayload stack,
            long expectedInventoryRevision,
            long expectedEndpointEffectRevision,
            String preOperationStateIdentity,
            String postOperationStateIdentity,
            long previousOwnerResultJournalSequence,
            String endpointConfigurationIdentity,
            long ownerRevision
    ) {
        WorkstationEndpointEffectId effectId = WorkstationEndpointEffectId.create(instanceId, invocationIdentity, kind);
        long postInventoryRevision = Math.addExact(expectedInventoryRevision, 1L);
        long postEndpointEffectRevision = Math.addExact(expectedEndpointEffectRevision, 1L);
        String preContentDigest = kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? stack.contentDigest()
                : WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST;
        String postContentDigest = kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST
                : stack.contentDigest();
        return new WorkstationEndpointJournalRecord(
                WorkstationEndpointSchema.CURRENT_VERSION,
                journalSequence,
                effectId,
                instanceId,
                invocationIdentity,
                kind,
                slotIndex,
                stack,
                expectedInventoryRevision,
                postInventoryRevision,
                expectedEndpointEffectRevision,
                postEndpointEffectRevision,
                preOperationStateIdentity,
                postOperationStateIdentity,
                previousOwnerResultJournalSequence,
                WorkstationEndpointFreshnessIdentity.create(
                        instanceId, slotIndex, expectedInventoryRevision, expectedEndpointEffectRevision,
                        preContentDigest, false, Optional.empty(), preOperationStateIdentity,
                        previousOwnerResultJournalSequence, endpointConfigurationIdentity
                ),
                WorkstationEndpointFreshnessIdentity.create(
                        instanceId, slotIndex, postInventoryRevision, postEndpointEffectRevision,
                        postContentDigest, false, Optional.empty(), postOperationStateIdentity,
                        journalSequence, endpointConfigurationIdentity
                ),
                endpointConfigurationIdentity,
                WorkstationEndpointJournalState.REQUESTED,
                ownerRevision,
                ownerRevision,
                Optional.empty(),
                Optional.empty()
        );
    }

    public WorkstationEndpointJournalRecord transition(
            WorkstationEndpointJournalState target,
            long ownerRevision,
            long candidatePostInventoryRevision,
            long candidateEndpointEffectRevision,
            Optional<WorkstationEndpointOwnerResult> candidateResult,
            Optional<String> candidateFailure
    ) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal endpoint journal transition: " + state + " -> " + target);
        }
        return new WorkstationEndpointJournalRecord(
                schemaVersion,
                journalSequence,
                effectId,
                instanceId,
                invocationIdentity,
                effectKind,
                slotIndex,
                exactStack,
                expectedInventoryRevision,
                candidatePostInventoryRevision,
                expectedEndpointEffectRevision,
                candidateEndpointEffectRevision,
                preOperationStateIdentity,
                postOperationStateIdentity,
                previousOwnerResultJournalSequence,
                preFreshnessIdentity,
                postFreshnessIdentity,
                endpointConfigurationIdentity,
                target,
                creationRevision,
                ownerRevision,
                candidateResult,
                candidateFailure
        );
    }

    @Override
    public int compareTo(WorkstationEndpointJournalRecord other) {
        int sequenceComparison = Long.compare(journalSequence, other.journalSequence);
        return sequenceComparison != 0 ? sequenceComparison : effectId.compareTo(other.effectId);
    }
}
