package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.WorkstationStackMutationPlan;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecordV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Serialized Workstation-owner boundary for schema-2 endpoint effects. */
public final class StackAwareWorkstationEndpointService {
    private final WorkstationEndpointJournalV2Storage storage;
    private final ExactItemStackCodec stackCodec;
    private WorkstationEndpointJournalV2 journal;

    public StackAwareWorkstationEndpointService(
            WorkstationEndpointJournalV2 journal,
            WorkstationEndpointJournalV2Storage storage,
            ExactItemStackCodec stackCodec
    ) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.stackCodec = Objects.requireNonNull(stackCodec, "stackCodec");
    }

    public synchronized WorkstationEndpointJournalV2 journalSnapshot() {
        return journal;
    }

    public synchronized StackAwareEndpointObservationResult observeWithdrawal(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            int slotIndex,
            int quantity
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.endpointPreparedEffectId().isPresent()) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Endpoint slot is locked by a prepared schema-2 effect"
            );
        }
        ItemStack source = endpoint.endpointStackSnapshot(slotIndex);
        if (source.isEmpty()) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.SOURCE_EMPTY,
                    "Source stack is empty"
            );
        }
        if (quantity <= 0) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INVALID_QUANTITY,
                    "Requested withdrawal quantity must be positive"
            );
        }
        if (quantity > source.getCount()) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INSUFFICIENT_SOURCE_QUANTITY,
                    "Source quantity is insufficient"
            );
        }
        int capacity = endpoint.endpointEffectiveCapacity(slotIndex, source);
        if (source.getCount() > capacity) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.CONFIGURATION_MISMATCH,
                    "Source stack exceeds the effective Workstation slot capacity"
            );
        }
        WorkstationStackMutationPlan plan = WorkstationStackMutationPlan.withdrawal(source, quantity, capacity);
        return StackAwareEndpointObservationResult.observed(observation(
                registries, endpoint, WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL, slotIndex, plan
        ));
    }

    public synchronized StackAwareEndpointObservationResult observeDeposit(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            int slotIndex,
            ItemStack payload
    ) {
        return observeMerge(registries, endpoint, WorkstationEndpointEffectKind.DESTINATION_DEPOSIT,
                slotIndex, payload);
    }

    public synchronized StackAwareEndpointObservationResult observeReturn(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            int slotIndex,
            ItemStack payload
    ) {
        return observeMerge(registries, endpoint, WorkstationEndpointEffectKind.SOURCE_RETURN, slotIndex, payload);
    }

    public synchronized StackAwareEndpointPreparationResult prepare(
            StackAwareWorkstationTransferEndpoint endpoint,
            String invocationIdentity,
            WorkstationEndpointObservationV2 observation
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(observation, "observation");
        if (!endpoint.endpointInstanceId().equals(observation.instanceId())
                || !endpoint.endpointKey().equals(observation.endpointKey())) {
            return StackAwareEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Observation references another Workstation instance"
            );
        }
        if (!endpoint.endpointConfigurationIdentity().equals(observation.endpointConfigurationIdentity())) {
            return StackAwareEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.CONFIGURATION_MISMATCH,
                    "Workstation capacity/configuration changed after observation"
            );
        }
        if (!isFresh(endpoint, observation)) {
            return StackAwareEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Workstation endpoint freshness changed after observation"
            );
        }
        com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2 requestedEffect =
                com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2.create(
                        observation.instanceId(), invocationIdentity, observation.effectKind());
        if (endpoint.endpointPreparedEffectId().isPresent()
                && endpoint.endpointPreparedEffectId().filter(requestedEffect::equals).isEmpty()) {
            return StackAwareEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Endpoint slot is locked by another schema-2 effect"
            );
        }
        WorkstationEndpointJournalV2.AppendCandidate candidate;
        try {
            candidate = journal.prepare(
                    invocationIdentity,
                    observation,
                    endpoint.endpointPostOperationStateIdentity(observation)
            );
        } catch (IllegalArgumentException exception) {
            WorkstationEndpointResultCode code = exception.getMessage().contains("configuration")
                    ? WorkstationEndpointResultCode.CONFIGURATION_MISMATCH
                    : WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT;
            return StackAwareEndpointPreparationResult.failed(code, exception.getMessage());
        }
        if (!candidate.duplicateObserved()) publish(candidate.journal());
        WorkstationEndpointJournalRecordV2 record = candidate.record();
        if (record.state() == WorkstationEndpointJournalState.PREPARED) {
            Optional<com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2> active =
                    endpoint.endpointPreparedEffectId();
            if (active.isEmpty()) {
                try {
                    endpoint.lockPreparedEndpointEffect(
                            record.effectId(), observation.slotIndex(), observation.inventoryRevision()
                    );
                } catch (IllegalStateException exception) {
                    return StackAwareEndpointPreparationResult.failed(
                            WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                            "Preparation is durable but its endpoint lock requires reconciliation: "
                                    + exception.getMessage()
                    );
                }
            } else if (!active.orElseThrow().equals(record.effectId())) {
                return StackAwareEndpointPreparationResult.failed(
                        WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                        "Endpoint slot is locked by another schema-2 effect"
                );
            }
        }
        return StackAwareEndpointPreparationResult.prepared(record.preparation());
    }

    public synchronized StackAwareEndpointEffectResult commit(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointPreparationV2 preparation
    ) {
        WorkstationEndpointJournalRecordV2 record = journal.find(preparation.effectId()).orElse(null);
        if (record == null || !record.preparation().equals(preparation)) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Prepared schema-2 effect is missing or conflicts with the Workstation journal"
            );
        }
        if (record.ownerResult().isPresent()) return reconcile(registries, endpoint, record, true);
        try {
            requireMatchingEndpoint(endpoint, preparation.observation());
            requireFresh(endpoint, preparation.observation());
        } catch (IllegalStateException exception) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    exception.getMessage()
            );
        }
        if (endpoint.endpointPreparedEffectId().filter(record.effectId()::equals).isEmpty()) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Prepared endpoint lock is absent or belongs to another effect"
            );
        }
        ItemStack pre = stackCodec.decodeState(registries, preparation.observation().preStack());
        ItemStack post = stackCodec.decodeState(registries, preparation.observation().postStack());
        if (!endpoint.endpointAcceptsCandidate(
                preparation.observation().effectKind(), preparation.observation().slotIndex(), pre, post)) {
            publish(journal.update(record.effectId(), (current, revision) -> current.transition(
                    WorkstationEndpointJournalState.REJECTED,
                    revision,
                    Optional.of("Workstation owner rejected the exact prepared candidate")
            )));
            endpoint.releasePreparedEndpointEffect(record.effectId());
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Workstation owner rejected the exact prepared candidate"
            );
        }
        WorkstationEndpointJournalV2 committed = journal.update(
                record.effectId(),
                WorkstationEndpointJournalRecordV2::commit
        );
        publish(committed);
        record = journal.find(record.effectId()).orElseThrow();
        try {
            applyProjection(endpoint, record, pre, post);
        } catch (RuntimeException exception) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Effect is durably committed and projection reconciliation is required: " + exception.getMessage()
            );
        }
        if (!exact(endpoint.endpointStackSnapshot(preparation.observation().slotIndex()), post)
                || endpoint.endpointInventoryRevision() != preparation.postInventoryRevision()
                || endpoint.endpointEffectRevision() != preparation.postEndpointEffectRevision()
                || endpoint.endpointLastAppliedJournalSequence() != preparation.journalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(preparation.postOperationStateIdentity())) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed projection does not match the immutable schema-2 owner result"
            );
        }
        return publishResultAndReconcile(endpoint, record, false);
    }

    public synchronized StackAwareEndpointEffectResult reconcile(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointJournalRecordV2 record,
            boolean duplicate
    ) {
        if (record.ownerResult().isEmpty()) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 endpoint effect has no committed owner result"
            );
        }
        if (!endpoint.endpointInstanceId().equals(record.preparation().observation().instanceId())) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Replacement Workstation cannot inherit a prior endpoint effect"
            );
        }
        ItemStack current = endpoint.endpointStackSnapshot(record.preparation().observation().slotIndex());
        ItemStack pre = stackCodec.decodeState(registries, record.preparation().observation().preStack());
        ItemStack post = stackCodec.decodeState(registries, record.preparation().observation().postStack());
        if (exact(current, post)
                && endpoint.endpointInventoryRevision() == record.preparation().postInventoryRevision()
                && endpoint.endpointEffectRevision() == record.preparation().postEndpointEffectRevision()
                && endpoint.endpointLastAppliedJournalSequence() == record.preparation().journalSequence()
                && endpoint.endpointLastEffectId().filter(record.effectId()::equals).isPresent()
                && endpoint.endpointLastOwnerResultIdentity()
                .filter(record.ownerResult().orElseThrow().evidenceIdentity()::equals).isPresent()) {
            return publishResultAndReconcile(endpoint, record, duplicate);
        }
        if (exact(current, pre)
                && endpoint.endpointInventoryRevision() == record.preparation().observation().inventoryRevision()
                && endpoint.endpointEffectRevision() == record.preparation().observation().endpointEffectRevision()) {
            try {
                applyProjection(endpoint, record, pre, post);
                return publishResultAndReconcile(endpoint, record, duplicate);
            } catch (RuntimeException exception) {
                return StackAwareEndpointEffectResult.failed(
                        WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                        "Committed endpoint projection remains stale: " + exception.getMessage()
                );
            }
        }
        return StackAwareEndpointEffectResult.failed(
                WorkstationEndpointResultCode.UNKNOWN_OUTCOME,
                "Projection matches neither exact committed pre-state nor post-state"
        );
    }

    public synchronized StackAwareEndpointEffectResult observeCommittedResult(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointPreparationV2 preparation
    ) {
        WorkstationEndpointJournalRecordV2 record = journal.find(preparation.effectId()).orElse(null);
        if (record == null || !record.preparation().equals(preparation)) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 endpoint preparation is absent or conflicts with the journal"
            );
        }
        if (record.ownerResult().isEmpty()) {
            return StackAwareEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 endpoint effect is prepared but not durably committed"
            );
        }
        return reconcile(registries, endpoint, record, true);
    }

    public synchronized WorkstationEndpointCancellationResult cancelPrepared(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointPreparationV2 preparation
    ) {
        WorkstationEndpointJournalRecordV2 record = journal.find(preparation.effectId()).orElse(null);
        if (record == null || !record.preparation().equals(preparation)) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 endpoint preparation is absent or conflicts with the journal"
            );
        }
        if (record.ownerResult().isPresent()) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed schema-2 endpoint effect cannot be cancelled"
            );
        }
        if (record.state() == WorkstationEndpointJournalState.FAILED
                || record.state() == WorkstationEndpointJournalState.REJECTED) {
            endpoint.releasePreparedEndpointEffect(record.effectId());
            return WorkstationEndpointCancellationResult.cancelled();
        }
        if (record.state() != WorkstationEndpointJournalState.PREPARED) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 endpoint effect is not safely cancellable"
            );
        }
        publish(journal.update(record.effectId(), (current, revision) -> current.transition(
                WorkstationEndpointJournalState.FAILED,
                revision,
                Optional.of("Prepared schema-2 endpoint effect cancelled before commit")
        )));
        endpoint.releasePreparedEndpointEffect(record.effectId());
        return WorkstationEndpointCancellationResult.cancelled();
    }

    private StackAwareEndpointObservationResult observeMerge(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack payload
    ) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(payload, "payload");
        if (endpoint.endpointPreparedEffectId().isPresent()) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Endpoint slot is locked by a prepared schema-2 effect"
            );
        }
        if (payload.isEmpty()) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INVALID_QUANTITY,
                    "Merge payload must not be empty"
            );
        }
        ItemStack destination = endpoint.endpointStackSnapshot(slotIndex);
        if (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(destination, payload)) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INCOMPATIBLE_STACK,
                    "Destination and payload stacks are incompatible"
            );
        }
        int capacity = endpoint.endpointEffectiveCapacity(slotIndex, payload);
        long mergedCount = (long) destination.getCount() + payload.getCount();
        if (mergedCount > capacity) {
            return StackAwareEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.DESTINATION_CAPACITY_EXCEEDED,
                    "Destination capacity is insufficient for an all-or-nothing merge"
            );
        }
        WorkstationStackMutationPlan plan = WorkstationStackMutationPlan.merge(destination, payload, capacity);
        return StackAwareEndpointObservationResult.observed(observation(registries, endpoint, kind, slotIndex, plan));
    }

    private WorkstationEndpointObservationV2 observation(
            HolderLookup.Provider registries,
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            WorkstationStackMutationPlan plan
    ) {
        return WorkstationEndpointObservationV2.create(
                endpoint.endpointInstanceId(), endpoint.endpointKey(), kind, slotIndex, plan.requestedQuantity(),
                stackCodec.encodeState(registries, plan.preStack()),
                stackCodec.encodeState(registries, plan.transferStack()),
                kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                        ? stackCodec.encodeState(registries, plan.postStack())
                        : WorkstationEndpointStackStateV2.empty(),
                stackCodec.encodeState(registries, plan.postStack()), endpoint.endpointInventoryRevision(),
                endpoint.endpointEffectRevision(), endpoint.endpointOperationStateIdentity(),
                endpoint.endpointLastAppliedJournalSequence(), journal.ownerRevision(), plan.effectiveCapacity(),
                endpoint.endpointConfigurationIdentity()
        );
    }

    private void applyProjection(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointJournalRecordV2 record,
            ItemStack pre,
            ItemStack post
    ) {
        endpoint.applyCommittedEndpointEffect(
                record.preparation().observation().effectKind(), record.preparation().observation().slotIndex(), pre,
                post, record.preparation().observation().inventoryRevision(),
                record.preparation().postInventoryRevision(), record.preparation().postEndpointEffectRevision(),
                record.journalSequence(), record.effectId(), record.ownerResult().orElseThrow().evidenceIdentity()
        );
    }

    private StackAwareEndpointEffectResult publishResultAndReconcile(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointJournalRecordV2 record,
            boolean duplicate
    ) {
        if (record.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED) {
            publish(journal.update(record.effectId(), (current, revision) -> current.transition(
                    WorkstationEndpointJournalState.RESULT_PUBLISHED, revision, Optional.empty())));
            record = journal.find(record.effectId()).orElseThrow();
        }
        if (record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED) {
            endpoint.releasePreparedEndpointEffect(record.effectId());
            publish(journal.update(record.effectId(), (current, revision) -> current.transition(
                    WorkstationEndpointJournalState.RECONCILED, revision, Optional.empty())));
            record = journal.find(record.effectId()).orElseThrow();
        }
        return duplicate
                ? StackAwareEndpointEffectResult.duplicate(record.ownerResult().orElseThrow())
                : StackAwareEndpointEffectResult.applied(record.ownerResult().orElseThrow());
    }

    private static void requireMatchingEndpoint(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointObservationV2 observation
    ) {
        if (!endpoint.endpointInstanceId().equals(observation.instanceId())
                || !endpoint.endpointKey().equals(observation.endpointKey())) {
            throw new IllegalStateException("Observation references another Workstation instance");
        }
        if (!endpoint.endpointConfigurationIdentity().equals(observation.endpointConfigurationIdentity())) {
            throw new IllegalStateException("Workstation capacity/configuration changed after observation");
        }
    }

    private static void requireFresh(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointObservationV2 observation
    ) {
        if (!isFresh(endpoint, observation)) {
            throw new IllegalStateException("Workstation endpoint freshness changed after observation");
        }
    }

    private static boolean isFresh(
            StackAwareWorkstationTransferEndpoint endpoint,
            WorkstationEndpointObservationV2 observation
    ) {
        return endpoint.endpointInventoryRevision() == observation.inventoryRevision()
                && endpoint.endpointEffectRevision() == observation.endpointEffectRevision()
                && endpoint.endpointLastAppliedJournalSequence() == observation.ownerResultJournalSequence()
                && endpoint.endpointOperationStateIdentity().equals(observation.operationStateIdentity());
    }

    private static boolean exact(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
    }

    private void publish(WorkstationEndpointJournalV2 candidate) {
        storage.save(candidate);
        journal = candidate;
    }
}
