package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DurableWorkstationProjection(
        int schemaVersion,
        WorldIdentityRootIdentity worldIdentity,
        WorkstationInstanceId instanceId,
        WorkstationEndpointKey endpointKey,
        long instanceGeneration,
        String instanceAllocationConfigurationIdentity,
        String projectionConfigurationIdentity,
        String blockEntityTypeIdentity,
        long instanceRegistryRevision,
        long projectionRevision,
        long inventoryRevision,
        long endpointEffectRevision,
        long lastAppliedJournalSequence,
        String slotCapacityConfigurationIdentity,
        List<WorkstationProjectionSlot> slots,
        String exactBlockEntityProjection,
        Optional<String> preparedEndpointEffectIdentity,
        Optional<String> lastEndpointEffectIdentity,
        Optional<String> lastEndpointOwnerResultIdentity,
        Optional<String> processingOperationIdentity,
        Optional<String> processingOwnerResultIdentity,
        Optional<WorkstationOperatingStateReference> operatingStateReference,
        WorkstationProjectionStatus status,
        Optional<String> retirementReason,
        Optional<String> priorActiveProjectionDigest,
        String stateDigest
) implements Comparable<DurableWorkstationProjection> {
    public DurableWorkstationProjection {
        if (schemaVersion != WorkstationProjectionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported durable Workstation projection schema: " + schemaVersion);
        }
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        if (instanceGeneration <= 0L) {
            throw new IllegalArgumentException("Projection instance generation must be positive");
        }
        instanceAllocationConfigurationIdentity = requireText(
                instanceAllocationConfigurationIdentity, "instanceAllocationConfigurationIdentity");
        projectionConfigurationIdentity = requireText(
                projectionConfigurationIdentity, "projectionConfigurationIdentity");
        if (!WorkstationProjectionSchema.CONFIGURATION_IDENTITY.equals(projectionConfigurationIdentity)) {
            throw new IllegalArgumentException("Unsupported durable Workstation projection configuration");
        }
        blockEntityTypeIdentity = requireText(blockEntityTypeIdentity, "blockEntityTypeIdentity");
        if (instanceRegistryRevision <= 0L || projectionRevision <= 0L) {
            throw new IllegalArgumentException("Projection and instance registry revisions must be positive");
        }
        if (inventoryRevision < 0L || endpointEffectRevision < 0L || lastAppliedJournalSequence < 0L) {
            throw new IllegalArgumentException("Workstation projection revisions must not be negative");
        }
        slotCapacityConfigurationIdentity = requireText(
                slotCapacityConfigurationIdentity, "slotCapacityConfigurationIdentity");
        slots = Objects.requireNonNull(slots, "slots").stream().sorted().toList();
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).slotIndex() != index) {
                throw new IllegalArgumentException("Projection slots must be complete, contiguous, and ordered");
            }
        }
        exactBlockEntityProjection = requireText(exactBlockEntityProjection, "exactBlockEntityProjection");
        preparedEndpointEffectIdentity = optionalIdentity(preparedEndpointEffectIdentity, "preparedEndpointEffectIdentity");
        lastEndpointEffectIdentity = optionalIdentity(lastEndpointEffectIdentity, "lastEndpointEffectIdentity");
        lastEndpointOwnerResultIdentity = optionalIdentity(
                lastEndpointOwnerResultIdentity, "lastEndpointOwnerResultIdentity");
        processingOperationIdentity = optionalIdentity(processingOperationIdentity, "processingOperationIdentity");
        processingOwnerResultIdentity = optionalIdentity(
                processingOwnerResultIdentity, "processingOwnerResultIdentity");
        operatingStateReference = Objects.requireNonNull(operatingStateReference, "operatingStateReference");
        if (operatingStateReference.isPresent()
                && !operatingStateReference.orElseThrow().workstationInstanceIdentity().equals(instanceId.value())) {
            throw new IllegalArgumentException("Operating-state reference targets another Workstation instance");
        }
        status = Objects.requireNonNull(status, "status");
        retirementReason = optionalText(retirementReason, "retirementReason");
        priorActiveProjectionDigest = optionalText(priorActiveProjectionDigest, "priorActiveProjectionDigest");
        if (status == WorkstationProjectionStatus.ACTIVE
                && (retirementReason.isPresent() || priorActiveProjectionDigest.isPresent())) {
            throw new IllegalArgumentException("Active projection cannot contain retirement evidence");
        }
        if (status == WorkstationProjectionStatus.TOMBSTONED && retirementReason.isEmpty()) {
            throw new IllegalArgumentException("Tombstoned projection requires retirement evidence");
        }
        WorkstationInstanceId canonical = WorkstationInstanceId.create(
                worldIdentity,
                endpointKey,
                instanceGeneration,
                instanceAllocationConfigurationIdentity
        );
        if (!canonical.equals(instanceId)) {
            throw new IllegalArgumentException("Projection identity does not match canonical Workstation instance inputs");
        }
        stateDigest = requireText(stateDigest, "stateDigest");
        String expectedDigest = calculateStateDigest(
                schemaVersion, worldIdentity, instanceId, endpointKey, instanceGeneration,
                instanceAllocationConfigurationIdentity, projectionConfigurationIdentity,
                blockEntityTypeIdentity, instanceRegistryRevision, inventoryRevision,
                endpointEffectRevision, lastAppliedJournalSequence, slotCapacityConfigurationIdentity,
                slots, exactBlockEntityProjection, preparedEndpointEffectIdentity,
                lastEndpointEffectIdentity, lastEndpointOwnerResultIdentity, processingOperationIdentity,
                processingOwnerResultIdentity, operatingStateReference, status, retirementReason,
                priorActiveProjectionDigest
        );
        if (!expectedDigest.equals(stateDigest)) {
            throw new IllegalArgumentException("Durable Workstation projection state digest mismatch");
        }
    }

    public static DurableWorkstationProjection active(
            WorldIdentityRootIdentity worldIdentity,
            WorkstationInstanceId instanceId,
            WorkstationEndpointKey endpointKey,
            long instanceGeneration,
            String instanceAllocationConfigurationIdentity,
            String blockEntityTypeIdentity,
            long instanceRegistryRevision,
            long projectionRevision,
            long inventoryRevision,
            long endpointEffectRevision,
            long lastAppliedJournalSequence,
            String slotCapacityConfigurationIdentity,
            List<WorkstationProjectionSlot> slots,
            String exactBlockEntityProjection,
            Optional<String> preparedEndpointEffectIdentity,
            Optional<String> lastEndpointEffectIdentity,
            Optional<String> lastEndpointOwnerResultIdentity,
            Optional<String> processingOperationIdentity,
            Optional<String> processingOwnerResultIdentity,
            Optional<WorkstationOperatingStateReference> operatingStateReference
    ) {
        String digest = calculateStateDigest(
                WorkstationProjectionSchema.CURRENT_VERSION, worldIdentity, instanceId, endpointKey,
                instanceGeneration, instanceAllocationConfigurationIdentity,
                WorkstationProjectionSchema.CONFIGURATION_IDENTITY, blockEntityTypeIdentity,
                instanceRegistryRevision, inventoryRevision, endpointEffectRevision,
                lastAppliedJournalSequence, slotCapacityConfigurationIdentity, slots,
                exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                operatingStateReference, WorkstationProjectionStatus.ACTIVE, Optional.empty(), Optional.empty()
        );
        return new DurableWorkstationProjection(
                WorkstationProjectionSchema.CURRENT_VERSION, worldIdentity, instanceId, endpointKey,
                instanceGeneration, instanceAllocationConfigurationIdentity,
                WorkstationProjectionSchema.CONFIGURATION_IDENTITY, blockEntityTypeIdentity,
                instanceRegistryRevision, projectionRevision, inventoryRevision, endpointEffectRevision,
                lastAppliedJournalSequence, slotCapacityConfigurationIdentity, slots,
                exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                operatingStateReference, WorkstationProjectionStatus.ACTIVE, Optional.empty(), Optional.empty(), digest
        );
    }

    public DurableWorkstationProjection tombstone(
            long nextProjectionRevision,
            long registryRevision,
            String reason
    ) {
        Optional<String> retirement = Optional.of(requireText(reason, "reason"));
        Optional<String> prior = Optional.of(stateDigest);
        String digest = calculateStateDigest(
                schemaVersion, worldIdentity, instanceId, endpointKey, instanceGeneration,
                instanceAllocationConfigurationIdentity, projectionConfigurationIdentity,
                blockEntityTypeIdentity, registryRevision, inventoryRevision, endpointEffectRevision,
                lastAppliedJournalSequence, slotCapacityConfigurationIdentity, slots,
                exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                operatingStateReference, WorkstationProjectionStatus.TOMBSTONED, retirement, prior
        );
        return new DurableWorkstationProjection(
                schemaVersion, worldIdentity, instanceId, endpointKey, instanceGeneration,
                instanceAllocationConfigurationIdentity, projectionConfigurationIdentity,
                blockEntityTypeIdentity, registryRevision, nextProjectionRevision, inventoryRevision,
                endpointEffectRevision, lastAppliedJournalSequence, slotCapacityConfigurationIdentity,
                slots, exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                operatingStateReference, WorkstationProjectionStatus.TOMBSTONED, retirement, prior, digest
        );
    }

    public DurableWorkstationProjection withOperatingStateReference(
            long nextProjectionRevision,
            Optional<WorkstationOperatingStateReference> reference
    ) {
        if (status != WorkstationProjectionStatus.ACTIVE) return this;
        Optional<WorkstationOperatingStateReference> exactReference = Objects.requireNonNull(reference, "reference");
        String digest = calculateStateDigest(
                schemaVersion, worldIdentity, instanceId, endpointKey, instanceGeneration,
                instanceAllocationConfigurationIdentity, projectionConfigurationIdentity,
                blockEntityTypeIdentity, instanceRegistryRevision, inventoryRevision, endpointEffectRevision,
                lastAppliedJournalSequence, slotCapacityConfigurationIdentity, slots,
                exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                exactReference, status, retirementReason, priorActiveProjectionDigest
        );
        if (digest.equals(stateDigest)) return this;
        return new DurableWorkstationProjection(
                schemaVersion, worldIdentity, instanceId, endpointKey, instanceGeneration,
                instanceAllocationConfigurationIdentity, projectionConfigurationIdentity,
                blockEntityTypeIdentity, instanceRegistryRevision, nextProjectionRevision, inventoryRevision,
                endpointEffectRevision, lastAppliedJournalSequence, slotCapacityConfigurationIdentity,
                slots, exactBlockEntityProjection, preparedEndpointEffectIdentity, lastEndpointEffectIdentity,
                lastEndpointOwnerResultIdentity, processingOperationIdentity, processingOwnerResultIdentity,
                exactReference, status, retirementReason, priorActiveProjectionDigest, digest
        );
    }

    public boolean sameAuthoritativeState(DurableWorkstationProjection other) {
        return other != null && stateDigest.equals(other.stateDigest);
    }

    public List<String> authoritativeMismatchFields(DurableWorkstationProjection other) {
        if (other == null) return List.of("missing_projection");
        List<String> fields = new ArrayList<>();
        addMismatch(fields, "schema_version", schemaVersion, other.schemaVersion);
        addMismatch(fields, "world_identity", worldIdentity, other.worldIdentity);
        addMismatch(fields, "instance_id", instanceId, other.instanceId);
        addMismatch(fields, "endpoint_key", endpointKey, other.endpointKey);
        addMismatch(fields, "instance_generation", instanceGeneration, other.instanceGeneration);
        addMismatch(fields, "instance_allocation_configuration_identity",
                instanceAllocationConfigurationIdentity, other.instanceAllocationConfigurationIdentity);
        addMismatch(fields, "projection_configuration_identity",
                projectionConfigurationIdentity, other.projectionConfigurationIdentity);
        addMismatch(fields, "block_entity_type_identity", blockEntityTypeIdentity, other.blockEntityTypeIdentity);
        addMismatch(fields, "instance_registry_revision", instanceRegistryRevision, other.instanceRegistryRevision);
        addMismatch(fields, "inventory_revision", inventoryRevision, other.inventoryRevision);
        addMismatch(fields, "endpoint_effect_revision", endpointEffectRevision, other.endpointEffectRevision);
        addMismatch(fields, "last_applied_journal_sequence",
                lastAppliedJournalSequence, other.lastAppliedJournalSequence);
        addMismatch(fields, "slot_capacity_configuration_identity",
                slotCapacityConfigurationIdentity, other.slotCapacityConfigurationIdentity);
        addMismatch(fields, "slots", slots, other.slots);
        addMismatch(fields, "exact_block_entity_projection",
                exactBlockEntityProjection, other.exactBlockEntityProjection);
        addMismatch(fields, "prepared_endpoint_effect_identity",
                preparedEndpointEffectIdentity, other.preparedEndpointEffectIdentity);
        addMismatch(fields, "last_endpoint_effect_identity",
                lastEndpointEffectIdentity, other.lastEndpointEffectIdentity);
        addMismatch(fields, "last_endpoint_owner_result_identity",
                lastEndpointOwnerResultIdentity, other.lastEndpointOwnerResultIdentity);
        addMismatch(fields, "processing_operation_identity",
                processingOperationIdentity, other.processingOperationIdentity);
        addMismatch(fields, "processing_owner_result_identity",
                processingOwnerResultIdentity, other.processingOwnerResultIdentity);
        addMismatch(fields, "operating_state_reference", operatingStateReference, other.operatingStateReference);
        addMismatch(fields, "status", status, other.status);
        addMismatch(fields, "retirement_reason", retirementReason, other.retirementReason);
        addMismatch(fields, "prior_active_projection_digest",
                priorActiveProjectionDigest, other.priorActiveProjectionDigest);
        return List.copyOf(fields);
    }

    @Override
    public int compareTo(DurableWorkstationProjection other) {
        return instanceId.compareTo(other.instanceId);
    }

    private static String calculateStateDigest(
            int schemaVersion,
            WorldIdentityRootIdentity worldIdentity,
            WorkstationInstanceId instanceId,
            WorkstationEndpointKey endpointKey,
            long instanceGeneration,
            String instanceAllocationConfigurationIdentity,
            String projectionConfigurationIdentity,
            String blockEntityTypeIdentity,
            long instanceRegistryRevision,
            long inventoryRevision,
            long endpointEffectRevision,
            long lastAppliedJournalSequence,
            String slotCapacityConfigurationIdentity,
            List<WorkstationProjectionSlot> slots,
            String exactBlockEntityProjection,
            Optional<String> preparedEndpointEffectIdentity,
            Optional<String> lastEndpointEffectIdentity,
            Optional<String> lastEndpointOwnerResultIdentity,
            Optional<String> processingOperationIdentity,
            Optional<String> processingOwnerResultIdentity,
            Optional<WorkstationOperatingStateReference> operatingStateReference,
            WorkstationProjectionStatus status,
            Optional<String> retirementReason,
            Optional<String> priorActiveProjectionDigest
    ) {
        Digest digest = new Digest("butchercraft:durable_workstation_projection_state/v1")
                .add(schemaVersion).add(worldIdentity.identity()).add(worldIdentity.schemaVersion())
                .add(worldIdentity.rootDigest()).add(instanceId.value()).add(endpointKey.canonicalValue())
                .add(instanceGeneration).add(instanceAllocationConfigurationIdentity)
                .add(projectionConfigurationIdentity).add(blockEntityTypeIdentity)
                .add(instanceRegistryRevision).add(inventoryRevision).add(endpointEffectRevision)
                .add(lastAppliedJournalSequence).add(slotCapacityConfigurationIdentity)
                .add(exactBlockEntityProjection).add(status.name());
        for (WorkstationProjectionSlot slot : slots) {
            digest.add(slot.slotIndex()).add(slot.configuredCapacity()).add(slot.effectiveCapacity())
                    .add(slot.exactStack().isPresent());
            slot.exactStack().ifPresent(stack -> digest.add(stack.encodingIdentity()).add(stack.itemIdentity())
                    .add(stack.count()).add(stack.contentDigest()).add(stack.encodedStack()));
        }
        addOptional(digest, preparedEndpointEffectIdentity);
        addOptional(digest, lastEndpointEffectIdentity);
        addOptional(digest, lastEndpointOwnerResultIdentity);
        addOptional(digest, processingOperationIdentity);
        addOptional(digest, processingOwnerResultIdentity);
        digest.add(operatingStateReference.isPresent());
        operatingStateReference.ifPresent(reference -> digest.add(reference.workstationInstanceIdentity())
                .add(reference.revision()).add(reference.state()).add(reference.contentDigest()));
        addOptional(digest, retirementReason);
        addOptional(digest, priorActiveProjectionDigest);
        return digest.finish();
    }

    private static void addOptional(Digest digest, Optional<String> value) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }

    private static void addMismatch(List<String> fields, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) fields.add(field);
    }

    private static Optional<String> optionalIdentity(Optional<String> value, String field) {
        return optionalText(value, field);
    }

    private static Optional<String> optionalText(Optional<String> value, String field) {
        return Objects.requireNonNull(value, field).map(item -> requireText(item, field));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static final class Digest {
        private final MessageDigest digest;

        private Digest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        private Digest add(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            add(bytes.length);
            digest.update(bytes);
            return this;
        }

        private Digest add(long value) {
            for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
            return this;
        }

        private Digest add(int value) {
            return add((long) value);
        }

        private Digest add(boolean value) {
            digest.update((byte) (value ? 1 : 0));
            return this;
        }

        private String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
