package com.butchercraft.workstation.endpoint;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public record WorkstationInstanceRegistry(
        int schemaVersion,
        long ownerRevision,
        WorldIdentityRootIdentity worldIdentity,
        long nextInstanceGeneration,
        String allocationConfigurationIdentity,
        List<WorkstationInstanceRecord> records
) {
    public WorkstationInstanceRegistry {
        schemaVersion = WorkstationEndpointValidation.schema(schemaVersion, "instance registry schema version");
        ownerRevision = WorkstationEndpointValidation.nonNegative(ownerRevision, "instance registry owner revision");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        nextInstanceGeneration = WorkstationEndpointValidation.positive(
                nextInstanceGeneration,
                "next instance generation"
        );
        allocationConfigurationIdentity = WorkstationEndpointValidation.id(
                allocationConfigurationIdentity,
                "instance allocation configuration identity"
        );
        records = Objects.requireNonNull(records, "records").stream().sorted().toList();
        Map<WorkstationInstanceId, WorkstationInstanceRecord> byId = new LinkedHashMap<>();
        Map<Long, WorkstationInstanceRecord> byGeneration = new LinkedHashMap<>();
        for (WorkstationInstanceRecord record : records) {
            if (!record.worldIdentity().equals(worldIdentity)) {
                throw new IllegalArgumentException("Workstation instance references another World Identity");
            }
            if (!record.allocationConfigurationIdentity().equals(allocationConfigurationIdentity)) {
                throw new IllegalArgumentException("Workstation instance allocation configuration mismatch");
            }
            if (byId.put(record.instanceId(), record) != null) {
                throw new IllegalArgumentException("Duplicate Workstation Instance Identity: " + record.instanceId());
            }
            if (byGeneration.put(record.generation(), record) != null) {
                throw new IllegalArgumentException("Duplicate workstation instance generation: " + record.generation());
            }
            if (record.generation() >= nextInstanceGeneration) {
                throw new IllegalArgumentException("Next workstation generation has regressed");
            }
        }
    }

    public static WorkstationInstanceRegistry empty(
            WorldIdentityRootIdentity worldIdentity,
            String configurationIdentity
    ) {
        return new WorkstationInstanceRegistry(
                WorkstationEndpointSchema.CURRENT_VERSION,
                0L,
                worldIdentity,
                1L,
                configurationIdentity,
                List.of()
        );
    }

    public Optional<WorkstationInstanceRecord> find(WorkstationInstanceId instanceId) {
        return records.stream().filter(record -> record.instanceId().equals(instanceId)).findFirst();
    }

    public Optional<WorkstationInstanceRecord> activeAt(WorkstationEndpointKey key) {
        return records.stream()
                .filter(record -> record.endpointKey().equals(key))
                .filter(record -> record.lifecycle() == WorkstationInstanceLifecycle.ACTIVE
                        || record.lifecycle() == WorkstationInstanceLifecycle.PENDING_BINDING)
                .max(Comparator.comparingLong(WorkstationInstanceRecord::generation));
    }

    public AllocationCandidate allocate(WorkstationEndpointKey key, int maximumRecords) {
        Objects.requireNonNull(key, "key");
        if (records.size() >= maximumRecords) {
            throw new IllegalStateException("Workstation instance registry capacity is exhausted");
        }
        if (activeAt(key).isPresent()) {
            throw new IllegalStateException("An active workstation instance already owns endpoint " + key.canonicalValue());
        }
        long newRevision = Math.addExact(ownerRevision, 1L);
        WorkstationInstanceRecord record = WorkstationInstanceRecord.pending(
                worldIdentity,
                key,
                nextInstanceGeneration,
                allocationConfigurationIdentity,
                newRevision
        );
        List<WorkstationInstanceRecord> candidateRecords = new ArrayList<>(records);
        candidateRecords.add(record);
        WorkstationInstanceRegistry candidate = new WorkstationInstanceRegistry(
                schemaVersion,
                newRevision,
                worldIdentity,
                Math.addExact(nextInstanceGeneration, 1L),
                allocationConfigurationIdentity,
                candidateRecords
        );
        return new AllocationCandidate(candidate, record);
    }

    public WorkstationInstanceRegistry update(WorkstationInstanceId instanceId, UnaryOperator<WorkstationInstanceRecord> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        long newRevision = Math.addExact(ownerRevision, 1L);
        boolean found = false;
        List<WorkstationInstanceRecord> candidate = new ArrayList<>(records.size());
        for (WorkstationInstanceRecord record : records) {
            if (record.instanceId().equals(instanceId)) {
                WorkstationInstanceRecord updated = Objects.requireNonNull(update.apply(record), "updated record");
                if (updated.lastUpdateRevision() != newRevision) {
                    throw new IllegalArgumentException("Updated instance record must bind the candidate owner revision");
                }
                candidate.add(updated);
                found = true;
            } else {
                candidate.add(record);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown Workstation Instance Identity: " + instanceId.value());
        }
        return new WorkstationInstanceRegistry(
                schemaVersion,
                newRevision,
                worldIdentity,
                nextInstanceGeneration,
                allocationConfigurationIdentity,
                candidate
        );
    }

    public record AllocationCandidate(
            WorkstationInstanceRegistry registry,
            WorkstationInstanceRecord record
    ) {
        public AllocationCandidate {
            registry = Objects.requireNonNull(registry, "registry");
            record = Objects.requireNonNull(record, "record");
        }
    }
}
