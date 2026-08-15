package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointJournalRecordV2(
        int schemaVersion,
        long journalSequence,
        WorkstationEndpointPreparationV2 preparation,
        WorkstationEndpointJournalState state,
        long creationRevision,
        long lastUpdateRevision,
        Optional<WorkstationEndpointOwnerResultV2> ownerResult,
        Optional<String> failureDetail
) {
    public WorkstationEndpointJournalRecordV2 {
        if (schemaVersion != WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema-2 endpoint journal record");
        }
        WorkstationEndpointValidation.positive(journalSequence, "journal sequence");
        preparation = Objects.requireNonNull(preparation, "preparation");
        if (journalSequence != preparation.journalSequence()) {
            throw new IllegalArgumentException("Journal record sequence must match its preparation");
        }
        state = Objects.requireNonNull(state, "state");
        WorkstationEndpointValidation.positive(creationRevision, "creation revision");
        WorkstationEndpointValidation.positive(lastUpdateRevision, "last-update revision");
        if (lastUpdateRevision < creationRevision) {
            throw new IllegalArgumentException("Journal record revisions must be monotonic");
        }
        ownerResult = Objects.requireNonNull(ownerResult, "ownerResult");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(value -> WorkstationEndpointValidation.text(value, "journal failure detail"));
        if ((state == WorkstationEndpointJournalState.EFFECT_COMMITTED
                || state == WorkstationEndpointJournalState.RESULT_PUBLISHED
                || state == WorkstationEndpointJournalState.RECONCILED) && ownerResult.isEmpty()) {
            throw new IllegalArgumentException("Committed schema-2 endpoint record must freeze an owner result");
        }
        if (ownerResult.isPresent() && !ownerResult.orElseThrow().preparation().equals(preparation)) {
            throw new IllegalArgumentException("Owner result must bind the exact journal preparation");
        }
    }

    public static WorkstationEndpointJournalRecordV2 prepared(
            WorkstationEndpointPreparationV2 preparation,
            long ownerRevision
    ) {
        return new WorkstationEndpointJournalRecordV2(
                WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION,
                preparation.journalSequence(), preparation, WorkstationEndpointJournalState.PREPARED,
                ownerRevision, ownerRevision, Optional.empty(), Optional.empty()
        );
    }

    public WorkstationEndpointJournalRecordV2 commit(long ownerRevision) {
        if (state != WorkstationEndpointJournalState.PREPARED) {
            if (ownerResult.isPresent()) return this;
            throw new IllegalStateException("Only a prepared schema-2 endpoint effect may commit");
        }
        if (ownerRevision != preparation.committedJournalRevision()) {
            throw new IllegalArgumentException("Commit revision does not match prepared durable boundary");
        }
        return new WorkstationEndpointJournalRecordV2(
                schemaVersion, journalSequence, preparation, WorkstationEndpointJournalState.EFFECT_COMMITTED,
                creationRevision, ownerRevision, Optional.of(WorkstationEndpointOwnerResultV2.applied(preparation)),
                Optional.empty()
        );
    }

    public WorkstationEndpointJournalRecordV2 transition(
            WorkstationEndpointJournalState target,
            long ownerRevision,
            Optional<String> detail
    ) {
        if (!state.canTransitionTo(target)) {
            if (state == target) return this;
            throw new IllegalArgumentException("Invalid endpoint journal transition: " + state + " -> " + target);
        }
        return new WorkstationEndpointJournalRecordV2(
                schemaVersion, journalSequence, preparation, target, creationRevision, ownerRevision, ownerResult,
                detail
        );
    }

    public WorkstationEndpointEffectIdV2 effectId() {
        return preparation.effectId();
    }
}
