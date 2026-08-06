package com.butchercraft.workstation.endpoint;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorkstationInstanceRecord(
        int schemaVersion,
        WorkstationInstanceId instanceId,
        WorldIdentityRootIdentity worldIdentity,
        WorkstationEndpointKey endpointKey,
        long generation,
        String allocationEvidenceIdentity,
        String allocationContentDigest,
        String allocationConfigurationIdentity,
        WorkstationInstanceLifecycle lifecycle,
        long creationRevision,
        long lastUpdateRevision,
        Optional<String> terminalReason,
        List<String> unresolvedJournalReferences
) implements Comparable<WorkstationInstanceRecord> {
    public WorkstationInstanceRecord {
        schemaVersion = WorkstationEndpointValidation.schema(schemaVersion, "instance schema version");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        generation = WorkstationEndpointValidation.positive(generation, "instance generation");
        allocationEvidenceIdentity = WorkstationEndpointValidation.id(
                allocationEvidenceIdentity,
                "allocation evidence identity"
        );
        allocationContentDigest = WorkstationEndpointValidation.digest(
                allocationContentDigest,
                "allocation content digest"
        );
        allocationConfigurationIdentity = WorkstationEndpointValidation.id(
                allocationConfigurationIdentity,
                "allocation configuration identity"
        );
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        creationRevision = WorkstationEndpointValidation.positive(creationRevision, "creation revision");
        lastUpdateRevision = WorkstationEndpointValidation.positive(lastUpdateRevision, "last update revision");
        if (lastUpdateRevision < creationRevision) {
            throw new IllegalArgumentException("Instance update revision cannot precede creation revision");
        }
        terminalReason = Objects.requireNonNull(terminalReason, "terminalReason")
                .map(value -> WorkstationEndpointValidation.text(value, "terminal reason"));
        unresolvedJournalReferences = Objects.requireNonNull(
                unresolvedJournalReferences,
                "unresolvedJournalReferences"
        ).stream().map(value -> WorkstationEndpointValidation.id(value, "journal reference")).sorted().toList();
        WorkstationInstanceId expected = WorkstationInstanceId.create(
                worldIdentity,
                endpointKey,
                generation,
                allocationConfigurationIdentity
        );
        if (!expected.equals(instanceId)) {
            throw new IllegalArgumentException("Workstation instance identity does not match canonical inputs");
        }
    }

    public static WorkstationInstanceRecord pending(
            WorldIdentityRootIdentity worldIdentity,
            WorkstationEndpointKey endpointKey,
            long generation,
            String configurationIdentity,
            long ownerRevision
    ) {
        WorkstationInstanceId instanceId = WorkstationInstanceId.create(
                worldIdentity,
                endpointKey,
                generation,
                configurationIdentity
        );
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_instance_allocation")
                .add(WorkstationEndpointSchema.CURRENT_VERSION)
                .add(instanceId.value())
                .add(worldIdentity.identity())
                .add(worldIdentity.rootDigest())
                .add(endpointKey.canonicalValue())
                .add(generation)
                .add(configurationIdentity)
                .finish();
        String evidence = "butchercraft:workstation_instance_allocation/v1/"
                + WorkstationEndpointCanonicalDigest.suffix(digest);
        return new WorkstationInstanceRecord(
                WorkstationEndpointSchema.CURRENT_VERSION,
                instanceId,
                worldIdentity,
                endpointKey,
                generation,
                evidence,
                digest,
                configurationIdentity,
                WorkstationInstanceLifecycle.PENDING_BINDING,
                ownerRevision,
                ownerRevision,
                Optional.empty(),
                List.of()
        );
    }

    public WorkstationInstanceRecord transition(
            WorkstationInstanceLifecycle target,
            long ownerRevision,
            Optional<String> reason,
            List<String> unresolvedReferences
    ) {
        if (!lifecycle.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal workstation instance transition: " + lifecycle + " -> " + target);
        }
        return new WorkstationInstanceRecord(
                schemaVersion,
                instanceId,
                worldIdentity,
                endpointKey,
                generation,
                allocationEvidenceIdentity,
                allocationContentDigest,
                allocationConfigurationIdentity,
                target,
                creationRevision,
                ownerRevision,
                reason,
                unresolvedReferences
        );
    }

    @Override
    public int compareTo(WorkstationInstanceRecord other) {
        return instanceId.compareTo(other.instanceId);
    }
}
