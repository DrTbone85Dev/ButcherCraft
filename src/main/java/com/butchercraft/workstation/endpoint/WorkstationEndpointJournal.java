package com.butchercraft.workstation.endpoint;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public record WorkstationEndpointJournal(
        int schemaVersion,
        long ownerRevision,
        long nextJournalSequence,
        WorldIdentityRootIdentity worldIdentity,
        String endpointConfigurationIdentity,
        List<WorkstationEndpointJournalRecord> records
) {
    public WorkstationEndpointJournal {
        schemaVersion = WorkstationEndpointValidation.schema(schemaVersion, "endpoint journal schema version");
        ownerRevision = WorkstationEndpointValidation.nonNegative(ownerRevision, "endpoint journal owner revision");
        nextJournalSequence = WorkstationEndpointValidation.positive(
                nextJournalSequence,
                "next endpoint journal sequence"
        );
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        records = Objects.requireNonNull(records, "records").stream().sorted().toList();
        Map<WorkstationEndpointEffectId, WorkstationEndpointJournalRecord> unique = new LinkedHashMap<>();
        Map<Long, WorkstationEndpointJournalRecord> sequences = new LinkedHashMap<>();
        for (WorkstationEndpointJournalRecord record : records) {
            if (unique.put(record.effectId(), record) != null) {
                throw new IllegalArgumentException("Duplicate workstation endpoint effect: " + record.effectId().value());
            }
            if (record.lastUpdateRevision() > ownerRevision) {
                throw new IllegalArgumentException("Endpoint journal record revision exceeds owner revision");
            }
            if (sequences.put(record.journalSequence(), record) != null) {
                throw new IllegalArgumentException("Duplicate workstation endpoint journal sequence");
            }
            if (record.journalSequence() >= nextJournalSequence) {
                throw new IllegalArgumentException("Next workstation endpoint journal sequence has regressed");
            }
        }
    }

    public static WorkstationEndpointJournal empty(
            WorldIdentityRootIdentity worldIdentity,
            String configurationIdentity
    ) {
        return new WorkstationEndpointJournal(
                WorkstationEndpointSchema.CURRENT_VERSION,
                0L,
                1L,
                worldIdentity,
                configurationIdentity,
                List.of()
        );
    }

    public Optional<WorkstationEndpointJournalRecord> find(WorkstationEndpointEffectId effectId) {
        return records.stream().filter(record -> record.effectId().equals(effectId)).findFirst();
    }

    public AppendCandidate request(
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
            int maximumRecords
    ) {
        WorkstationEndpointEffectId effectId = WorkstationEndpointEffectId.create(instanceId, invocationIdentity, kind);
        Optional<WorkstationEndpointJournalRecord> existing = find(effectId);
        if (existing.isPresent()) {
            WorkstationEndpointJournalRecord record = existing.orElseThrow();
            if (record.slotIndex() != slotIndex
                    || !record.exactStack().equals(stack)
                    || record.expectedInventoryRevision() != expectedInventoryRevision
                    || record.expectedEndpointEffectRevision() != expectedEndpointEffectRevision
                    || !record.preOperationStateIdentity().equals(preOperationStateIdentity)
                    || !record.postOperationStateIdentity().equals(postOperationStateIdentity)
                    || record.previousOwnerResultJournalSequence() != previousOwnerResultJournalSequence) {
                throw new IllegalArgumentException("Endpoint Effect Identity conflicts with canonical request content");
            }
            return new AppendCandidate(this, record, true);
        }
        if (records.size() >= maximumRecords) {
            throw new IllegalStateException("Workstation endpoint journal capacity is exhausted");
        }
        long newRevision = Math.addExact(ownerRevision, 1L);
        WorkstationEndpointJournalRecord record = WorkstationEndpointJournalRecord.requested(
                nextJournalSequence,
                instanceId,
                invocationIdentity,
                kind,
                slotIndex,
                stack,
                expectedInventoryRevision,
                expectedEndpointEffectRevision,
                preOperationStateIdentity,
                postOperationStateIdentity,
                previousOwnerResultJournalSequence,
                endpointConfigurationIdentity,
                newRevision
        );
        List<WorkstationEndpointJournalRecord> candidateRecords = new ArrayList<>(records);
        candidateRecords.add(record);
        return new AppendCandidate(
                new WorkstationEndpointJournal(
                        schemaVersion,
                        newRevision,
                        Math.addExact(nextJournalSequence, 1L),
                        worldIdentity,
                        endpointConfigurationIdentity,
                        candidateRecords
                ),
                record,
                false
        );
    }

    public WorkstationEndpointJournal update(
            WorkstationEndpointEffectId effectId,
            BiFunction<WorkstationEndpointJournalRecord, Long, WorkstationEndpointJournalRecord> update
    ) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(update, "update");
        long newRevision = Math.addExact(ownerRevision, 1L);
        boolean found = false;
        List<WorkstationEndpointJournalRecord> candidate = new ArrayList<>(records.size());
        for (WorkstationEndpointJournalRecord record : records) {
            if (record.effectId().equals(effectId)) {
                WorkstationEndpointJournalRecord updated = Objects.requireNonNull(
                        update.apply(record, newRevision),
                        "updated record"
                );
                if (updated.lastUpdateRevision() != newRevision) {
                    throw new IllegalArgumentException("Updated endpoint record must bind candidate owner revision");
                }
                candidate.add(updated);
                found = true;
            } else {
                candidate.add(record);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown workstation endpoint effect: " + effectId.value());
        return new WorkstationEndpointJournal(
                schemaVersion,
                newRevision,
                nextJournalSequence,
                worldIdentity,
                endpointConfigurationIdentity,
                candidate
        );
    }

    public record AppendCandidate(
            WorkstationEndpointJournal journal,
            WorkstationEndpointJournalRecord record,
            boolean duplicate
    ) {
        public AppendCandidate {
            journal = Objects.requireNonNull(journal, "journal");
            record = Objects.requireNonNull(record, "record");
        }
    }
}
