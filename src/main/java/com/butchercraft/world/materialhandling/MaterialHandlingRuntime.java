package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public record MaterialHandlingRuntime(
        int schemaVersion,
        long ownerRevision,
        long nextTransferSequence,
        WorldIdentityRootIdentity worldIdentity,
        String configurationIdentity,
        List<MaterialTransferRecord> transfers
) {
    public MaterialHandlingRuntime {
        if (schemaVersion != MaterialHandlingSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Material Handling schema version: " + schemaVersion);
        }
        ownerRevision = MaterialHandlingValidation.nonNegative(ownerRevision, "Material Handling owner revision");
        nextTransferSequence = MaterialHandlingValidation.positive(nextTransferSequence, "next transfer sequence");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        configurationIdentity = MaterialHandlingValidation.id(configurationIdentity, "configuration identity");
        transfers = Objects.requireNonNull(transfers, "transfers").stream().sorted().toList();
        Map<MaterialTransferId, MaterialTransferRecord> ids = new LinkedHashMap<>();
        Map<Long, MaterialTransferRecord> sequences = new LinkedHashMap<>();
        for (MaterialTransferRecord transfer : transfers) {
            if (!transfer.worldIdentity().equals(worldIdentity)
                    || !transfer.configurationIdentity().equals(configurationIdentity)) {
                throw new IllegalArgumentException("Material Transfer root authority mismatch");
            }
            if (ids.put(transfer.transferId(), transfer) != null) {
                throw new IllegalArgumentException("Duplicate Material Transfer identity");
            }
            if (sequences.put(transfer.sequence(), transfer) != null) {
                throw new IllegalArgumentException("Duplicate Material Transfer sequence");
            }
            if (transfer.sequence() >= nextTransferSequence || transfer.lastUpdateRevision() > ownerRevision) {
                throw new IllegalArgumentException("Material Handling monotonic state regressed");
            }
        }
    }

    public static MaterialHandlingRuntime empty(
            WorldIdentityRootIdentity worldIdentity,
            String configurationIdentity
    ) {
        return new MaterialHandlingRuntime(
                MaterialHandlingSchema.CURRENT_VERSION,
                0L,
                1L,
                worldIdentity,
                configurationIdentity,
                List.of()
        );
    }

    public Optional<MaterialTransferRecord> find(MaterialTransferId transferId) {
        return transfers.stream().filter(transfer -> transfer.transferId().equals(transferId)).findFirst();
    }

    public AllocationCandidate request(
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            int maximumTransfers
    ) {
        if (transfers.size() >= maximumTransfers) throw new IllegalStateException("Material Transfer capacity exhausted");
        long revision = Math.addExact(ownerRevision, 1L);
        MaterialTransferRecord record = MaterialTransferRecord.requested(
                worldIdentity,
                nextTransferSequence,
                source,
                destination,
                materialIdentity,
                quantity,
                assignmentTypeIdentity,
                configurationIdentity,
                revision
        );
        List<MaterialTransferRecord> candidate = new ArrayList<>(transfers);
        candidate.add(record);
        return new AllocationCandidate(
                new MaterialHandlingRuntime(
                        schemaVersion,
                        revision,
                        Math.addExact(nextTransferSequence, 1L),
                        worldIdentity,
                        configurationIdentity,
                        candidate
                ),
                record
        );
    }

    public MaterialHandlingRuntime update(
            MaterialTransferId transferId,
            BiFunction<MaterialTransferRecord, Long, MaterialTransferRecord> update
    ) {
        long revision = Math.addExact(ownerRevision, 1L);
        boolean found = false;
        List<MaterialTransferRecord> candidate = new ArrayList<>(transfers.size());
        for (MaterialTransferRecord record : transfers) {
            if (record.transferId().equals(transferId)) {
                MaterialTransferRecord updated = Objects.requireNonNull(update.apply(record, revision), "updated transfer");
                if (updated.lastUpdateRevision() != revision) {
                    throw new IllegalArgumentException("Updated transfer must bind candidate owner revision");
                }
                candidate.add(updated);
                found = true;
            } else {
                candidate.add(record);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown Material Transfer: " + transferId.value());
        return new MaterialHandlingRuntime(
                schemaVersion,
                revision,
                nextTransferSequence,
                worldIdentity,
                configurationIdentity,
                candidate
        );
    }

    public record AllocationCandidate(MaterialHandlingRuntime runtime, MaterialTransferRecord transfer) {
        public AllocationCandidate {
            runtime = Objects.requireNonNull(runtime, "runtime");
            transfer = Objects.requireNonNull(transfer, "transfer");
        }
    }
}
