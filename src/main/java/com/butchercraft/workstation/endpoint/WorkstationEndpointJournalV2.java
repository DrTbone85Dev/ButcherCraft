package com.butchercraft.workstation.endpoint;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public record WorkstationEndpointJournalV2(
        int schemaVersion,
        long ownerRevision,
        long nextJournalSequence,
        WorldIdentityRootIdentity worldIdentity,
        String endpointConfigurationIdentity,
        Optional<String> immutableLegacySchema1Journal,
        List<WorkstationEndpointJournalRecordV2> records
) {
    public WorkstationEndpointJournalV2 {
        if (schemaVersion != WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Workstation endpoint journal schema: " + schemaVersion);
        }
        WorkstationEndpointValidation.nonNegative(ownerRevision, "owner revision");
        WorkstationEndpointValidation.positive(nextJournalSequence, "next journal sequence");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        immutableLegacySchema1Journal = Objects.requireNonNull(
                immutableLegacySchema1Journal,
                "immutableLegacySchema1Journal"
        );
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        List<WorkstationEndpointJournalRecordV2> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingLong(WorkstationEndpointJournalRecordV2::journalSequence));
        if (!ordered.equals(records)) throw new IllegalArgumentException("Endpoint records must be sequence ordered");
        Set<WorkstationEndpointEffectIdV2> effects = new HashSet<>();
        long previous = 0L;
        for (WorkstationEndpointJournalRecordV2 record : records) {
            if (record.journalSequence() <= previous || !effects.add(record.effectId())) {
                throw new IllegalArgumentException("Endpoint record identities and sequences must be unique");
            }
            previous = record.journalSequence();
        }
        if (previous >= nextJournalSequence) {
            throw new IllegalArgumentException("Next journal sequence must exceed retained records");
        }
    }

    public static WorkstationEndpointJournalV2 empty(
            WorldIdentityRootIdentity worldIdentity,
            String endpointConfigurationIdentity
    ) {
        return new WorkstationEndpointJournalV2(
                WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION, 0L, 1L, worldIdentity,
                endpointConfigurationIdentity, Optional.empty(), List.of()
        );
    }

    public static WorkstationEndpointJournalV2 migratedFromLegacy(
            WorkstationEndpointJournal legacy,
            String immutableLegacySchema1Journal,
            String stackAwareConfigurationIdentity
    ) {
        Objects.requireNonNull(legacy, "legacy");
        WorkstationEndpointValidation.text(immutableLegacySchema1Journal, "immutable legacy schema-1 journal");
        return new WorkstationEndpointJournalV2(
                WorkstationEndpointSchema.STACK_AWARE_JOURNAL_SCHEMA_VERSION,
                legacy.ownerRevision(),
                legacy.nextJournalSequence(),
                legacy.worldIdentity(),
                stackAwareConfigurationIdentity,
                Optional.of(immutableLegacySchema1Journal),
                List.of()
        );
    }

    public AppendCandidate prepare(
            String invocationIdentity,
            WorkstationEndpointObservationV2 observation,
            String postOperationStateIdentity
    ) {
        Objects.requireNonNull(observation, "observation");
        if (!endpointConfigurationIdentity.equals(observation.endpointConfigurationIdentity())) {
            throw new IllegalArgumentException("Observation uses a different endpoint configuration");
        }
        WorkstationEndpointEffectIdV2 effectId = WorkstationEndpointEffectIdV2.create(
                observation.instanceId(), invocationIdentity, observation.effectKind()
        );
        Optional<WorkstationEndpointJournalRecordV2> existing = find(effectId);
        if (existing.isPresent()) {
            WorkstationEndpointPreparationV2 retained = existing.orElseThrow().preparation();
            if (!retained.observation().equals(observation)
                    || !retained.invocationIdentity().equals(invocationIdentity)
                    || !retained.postOperationStateIdentity().equals(postOperationStateIdentity)) {
                throw new IllegalArgumentException("Effect Identity was reused with different canonical content");
            }
            return new AppendCandidate(this, existing.orElseThrow(), true);
        }
        long creationRevision = Math.addExact(ownerRevision, 1L);
        WorkstationEndpointPreparationV2 preparation = WorkstationEndpointPreparationV2.create(
                nextJournalSequence, Math.addExact(creationRevision, 1L), invocationIdentity, observation,
                postOperationStateIdentity
        );
        WorkstationEndpointJournalRecordV2 record = WorkstationEndpointJournalRecordV2.prepared(
                preparation,
                creationRevision
        );
        List<WorkstationEndpointJournalRecordV2> updated = new ArrayList<>(records);
        updated.add(record);
        return new AppendCandidate(new WorkstationEndpointJournalV2(
                schemaVersion, creationRevision, Math.addExact(nextJournalSequence, 1L), worldIdentity,
                endpointConfigurationIdentity, immutableLegacySchema1Journal, updated
        ), record, false);
    }

    public Optional<WorkstationEndpointJournalRecordV2> find(WorkstationEndpointEffectIdV2 effectId) {
        return records.stream().filter(record -> record.effectId().equals(effectId)).findFirst();
    }

    public WorkstationEndpointJournalV2 update(
            WorkstationEndpointEffectIdV2 effectId,
            BiFunction<WorkstationEndpointJournalRecordV2, Long, WorkstationEndpointJournalRecordV2> update
    ) {
        long revision = Math.addExact(ownerRevision, 1L);
        List<WorkstationEndpointJournalRecordV2> updated = new ArrayList<>(records.size());
        boolean found = false;
        for (WorkstationEndpointJournalRecordV2 record : records) {
            if (record.effectId().equals(effectId)) {
                updated.add(update.apply(record, revision));
                found = true;
            } else {
                updated.add(record);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown schema-2 endpoint effect: " + effectId.value());
        return new WorkstationEndpointJournalV2(
                schemaVersion, revision, nextJournalSequence, worldIdentity, endpointConfigurationIdentity,
                immutableLegacySchema1Journal, updated
        );
    }

    public record AppendCandidate(
            WorkstationEndpointJournalV2 journal,
            WorkstationEndpointJournalRecordV2 record,
            boolean duplicateObserved
    ) {}
}
