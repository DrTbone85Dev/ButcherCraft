package com.butchercraft.world.materialhandling;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public record MaterialHandlingRuntimeV2(
        int schemaVersion,
        long ownerRevision,
        long nextTransferSequence,
        WorldIdentityRootIdentity worldIdentity,
        String configurationIdentity,
        Optional<String> immutableLegacySchema1Runtime,
        List<MaterialTransferRecordV2> transfers
) {
    public MaterialHandlingRuntimeV2 {
        if (schemaVersion != MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Material Handling schema version: " + schemaVersion);
        }
        MaterialHandlingValidation.nonNegative(ownerRevision, "owner revision");
        MaterialHandlingValidation.positive(nextTransferSequence, "next transfer sequence");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        configurationIdentity = MaterialHandlingValidation.id(configurationIdentity, "configuration identity");
        immutableLegacySchema1Runtime = Objects.requireNonNull(
                immutableLegacySchema1Runtime,
                "immutableLegacySchema1Runtime"
        );
        transfers = Objects.requireNonNull(transfers, "transfers").stream().sorted().toList();
        Set<MaterialTransferIdV2> ids = new HashSet<>();
        long previous = 0L;
        for (MaterialTransferRecordV2 transfer : transfers) {
            if (!ids.add(transfer.transferId()) || transfer.sequence() <= previous
                    || transfer.sequence() >= nextTransferSequence
                    || transfer.lastUpdateRevision() > ownerRevision
                    || !transfer.worldIdentity().equals(worldIdentity)
                    || !transfer.configurationIdentity().equals(configurationIdentity)) {
                throw new IllegalArgumentException("Schema-2 Material Handling authority is inconsistent");
            }
            previous = transfer.sequence();
        }
    }

    public static MaterialHandlingRuntimeV2 empty(
            WorldIdentityRootIdentity worldIdentity,
            String configurationIdentity
    ) {
        return new MaterialHandlingRuntimeV2(
                MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION, 0L, 1L, worldIdentity, configurationIdentity,
                Optional.empty(), List.of()
        );
    }

    public static MaterialHandlingRuntimeV2 migratedFromLegacy(
            MaterialHandlingRuntime legacy,
            String immutableLegacySchema1Runtime,
            String stackAwareConfigurationIdentity
    ) {
        Objects.requireNonNull(legacy, "legacy");
        MaterialHandlingValidation.text(immutableLegacySchema1Runtime, "immutable legacy schema-1 runtime");
        return new MaterialHandlingRuntimeV2(
                MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION, legacy.ownerRevision(),
                legacy.nextTransferSequence(), legacy.worldIdentity(), stackAwareConfigurationIdentity,
                Optional.of(immutableLegacySchema1Runtime), List.of()
        );
    }

    public Optional<MaterialTransferRecordV2> find(MaterialTransferIdV2 transferId) {
        return transfers.stream().filter(transfer -> transfer.transferId().equals(transferId)).findFirst();
    }

    public Optional<MaterialTransferRecordV2> find(String transferIdentity) {
        return transfers.stream().filter(transfer -> transfer.transferId().value().equals(transferIdentity)).findFirst();
    }

    public AllocationCandidate request(
            com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference source,
            com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2 exactTransferStack,
            com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2 sourceObservation,
            int maximumTransfers
    ) {
        if (transfers.size() >= maximumTransfers) {
            throw new IllegalStateException("Schema-2 Material Transfer capacity exhausted");
        }
        long revision = Math.addExact(ownerRevision, 1L);
        MaterialTransferRecordV2 transfer = MaterialTransferRecordV2.create(
                nextTransferSequence, worldIdentity, source, destination, materialIdentity, quantity,
                assignmentTypeIdentity, employeeReference, MaterialTransferLifecycle.REQUESTED,
                exactTransferStack, Optional.empty(), List.of(sourceObservation), List.of(), List.of(),
                configurationIdentity, revision, revision, Optional.empty()
        );
        List<MaterialTransferRecordV2> candidate = new ArrayList<>(transfers);
        candidate.add(transfer);
        return new AllocationCandidate(new MaterialHandlingRuntimeV2(
                schemaVersion, revision, Math.addExact(nextTransferSequence, 1L), worldIdentity,
                configurationIdentity, immutableLegacySchema1Runtime, candidate
        ), transfer);
    }

    public MaterialHandlingRuntimeV2 update(
            MaterialTransferIdV2 transferId,
            BiFunction<MaterialTransferRecordV2, Long, MaterialTransferRecordV2> update
    ) {
        long revision = Math.addExact(ownerRevision, 1L);
        boolean found = false;
        List<MaterialTransferRecordV2> candidate = new ArrayList<>(transfers.size());
        for (MaterialTransferRecordV2 transfer : transfers) {
            if (transfer.transferId().equals(transferId)) {
                MaterialTransferRecordV2 updated = Objects.requireNonNull(
                        update.apply(transfer, revision), "updated schema-2 transfer"
                );
                if (updated.lastUpdateRevision() != revision) {
                    throw new IllegalArgumentException("Updated transfer must bind candidate owner revision");
                }
                candidate.add(updated);
                found = true;
            } else {
                candidate.add(transfer);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown schema-2 Material Transfer: " + transferId.value());
        return new MaterialHandlingRuntimeV2(
                schemaVersion, revision, nextTransferSequence, worldIdentity, configurationIdentity,
                immutableLegacySchema1Runtime, candidate
        );
    }

    public record AllocationCandidate(MaterialHandlingRuntimeV2 runtime, MaterialTransferRecordV2 transfer) {}
}
