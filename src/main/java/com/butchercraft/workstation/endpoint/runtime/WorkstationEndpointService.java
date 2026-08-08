package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecord;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class WorkstationEndpointService {
    public static final WorkstationEndpointService INSTANCE = new WorkstationEndpointService(
            WorldIdentityService.INSTANCE,
            WorkstationEndpointConfiguration.standard(),
            new ExactItemStackCodec()
    );

    private final WorldIdentityService worldIdentityService;
    private final WorkstationEndpointConfiguration configuration;
    private final ExactItemStackCodec stackCodec;
    private final AtomicReference<ActiveEndpoints> active = new AtomicReference<>();

    WorkstationEndpointService(
            WorldIdentityService worldIdentityService,
            WorkstationEndpointConfiguration configuration,
            ExactItemStackCodec stackCodec
    ) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.stackCodec = Objects.requireNonNull(stackCodec, "stackCodec");
    }

    public void initialize(ServerStartedEvent event) {
        ActiveEndpoints runtime = load(event.getServer());
        int reconciled = 0;
        for (WorkstationInstanceRecord instance : runtime.registry().records()) {
            if (reconciled >= configuration.maximumReconciliationsPerAction()) break;
            if (instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE
                    && instance.lifecycle() != WorkstationInstanceLifecycle.PENDING_BINDING) {
                continue;
            }
            ServerLevel level = loadedLevelFor(event.getServer(), instance.endpointKey().dimensionIdentity());
            BlockPos position = new BlockPos(
                    instance.endpointKey().x(),
                    instance.endpointKey().y(),
                    instance.endpointKey().z()
            );
            if (level != null && level.hasChunkAt(position)) {
                reconcileLoadedEndpoint(level, position);
                reconciled++;
            }
        }
    }

    public void stop(ServerStoppingEvent event) {
        ActiveEndpoints current = active.get();
        if (current != null && current.server() == event.getServer()) active.compareAndSet(current, null);
    }

    public synchronized WorkstationEndpointObservationResult observeWithdrawalOne(
            ServerLevel level,
            BlockPos sourcePosition
    ) {
        ResolvedEndpoint resolved = resolveAndEnroll(level, sourcePosition);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointObservationResult.failed(failure.code(), failure.detail());
        }
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        int sourceSlot = endpoint.endpointSlotIndex(WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL);
        if (sourceSlot < 0) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.UNSUPPORTED_EFFECT,
                    "Workstation does not expose an authoritative withdrawal slot"
            );
        }
        ItemStack exactStack = endpoint.endpointStackSnapshot(sourceSlot);
        if (exactStack.isEmpty()) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.SOURCE_EMPTY,
                    "Workstation source slot is empty"
            );
        }
        if (exactStack.getCount() != 1) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.SOURCE_MISMATCH,
                    "Schema 1 transfers require exactly one source item"
            );
        }
        WorkstationEndpointStackPayload payload;
        try {
            payload = stackCodec.encode(level.registryAccess(), exactStack);
        } catch (RuntimeException exception) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Source ItemStack could not be encoded exactly: " + exception.getMessage()
            );
        }
        return observeEffect(
                level,
                resolved,
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                sourceSlot,
                payload
        );
    }

    public synchronized WorkstationEndpointObservationResult observeDepositOne(
            ServerLevel level,
            BlockPos destinationPosition,
            WorkstationEndpointStackPayload exactStack
    ) {
        Objects.requireNonNull(exactStack, "exactStack");
        if (exactStack.count() != 1) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Schema 1 transfers require exactly one destination item"
            );
        }
        ResolvedEndpoint resolved = resolveAndEnroll(level, destinationPosition);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointObservationResult.failed(failure.code(), failure.detail());
        }
        int destinationSlot = resolved.endpoint().orElseThrow()
                .endpointSlotIndex(WorkstationEndpointEffectKind.DESTINATION_DEPOSIT);
        if (destinationSlot < 0) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.UNSUPPORTED_EFFECT,
                    "Workstation does not expose an authoritative deposit slot"
            );
        }
        return observeEffect(
                level,
                resolved,
                WorkstationEndpointEffectKind.DESTINATION_DEPOSIT,
                destinationSlot,
                exactStack
        );
    }

    public synchronized WorkstationEndpointObservationResult observeReturnOne(
            ServerLevel level,
            BlockPos sourcePosition,
            WorkstationEndpointStackPayload exactStack
    ) {
        Objects.requireNonNull(exactStack, "exactStack");
        if (exactStack.count() != 1) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Schema 1 returns require exactly one source item"
            );
        }
        ResolvedEndpoint resolved = resolveAndEnroll(level, sourcePosition);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointObservationResult.failed(failure.code(), failure.detail());
        }
        int sourceSlot = resolved.endpoint().orElseThrow()
                .endpointSlotIndex(WorkstationEndpointEffectKind.SOURCE_RETURN);
        if (sourceSlot < 0) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.UNSUPPORTED_EFFECT,
                    "Workstation does not expose an authoritative source-return slot"
            );
        }
        return observeEffect(
                level,
                resolved,
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                sourceSlot,
                exactStack
        );
    }

    public synchronized WorkstationEndpointPreparationResult prepareObservedEffect(
            ServerLevel level,
            BlockPos position,
            String invocationIdentity,
            WorkstationEndpointObservation observation
    ) {
        Objects.requireNonNull(observation, "observation");
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointPreparationResult.failed(failure.code(), failure.detail());
        }
        return prepareEffect(level, resolved, invocationIdentity, observation);
    }

    public synchronized WorkstationEndpointReferenceResult referenceFor(ServerLevel level, BlockPos position) {
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointReferenceResult.failed(failure.code(), failure.detail());
        }
        WorkstationInstanceRecord instance = resolved.instance().orElseThrow();
        return WorkstationEndpointReferenceResult.resolved(new WorkstationEndpointReference(
                instance.instanceId(),
                instance.endpointKey(),
                instance.generation()
        ));
    }

    public synchronized WorkstationEndpointEffectResult commitPrepared(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparation preparation
    ) {
        Objects.requireNonNull(preparation, "preparation");
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) return resolved.failure().orElseThrow();
        if (!resolved.instance().orElseThrow().instanceId().equals(preparation.instanceId())) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Prepared effect references another Workstation instance"
            );
        }
        ActiveEndpoints runtime = load(level.getServer());
        WorkstationEndpointJournalRecord record = runtime.journal().find(preparation.effectId()).orElse(null);
        if (record == null) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Prepared endpoint effect is missing from the Workstation journal"
            );
        }
        if (record.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED
                || record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED
                || record.state() == WorkstationEndpointJournalState.RECONCILED
                || record.state().terminal()) {
            return reconcileExisting(level, resolved, record);
        }
        if (record.state() != WorkstationEndpointJournalState.PREPARED
                || !WorkstationEndpointPreparation.from(record).equals(preparation)) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Prepared endpoint evidence does not match the authoritative journal"
            );
        }
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.preparedEffectId().isEmpty()) {
            endpoint.lockPreparedEndpointEffect(
                    record.effectId(),
                    record.slotIndex(),
                    record.expectedInventoryRevision()
            );
        } else if (projection.preparedEffectId().filter(record.effectId()::equals).isEmpty()) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Endpoint slot is locked by another prepared effect"
            );
        }
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), record.exactStack());
        } catch (RuntimeException exception) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Exact endpoint stack could not be decoded: " + exception.getMessage()
            );
        }
        if (endpoint.endpointProjection().inventoryRevision() != record.expectedInventoryRevision()
                || endpoint.endpointProjection().endpointEffectRevision()
                != record.expectedEndpointEffectRevision()
                || endpoint.endpointProjection().lastAppliedJournalSequence()
                != record.previousOwnerResultJournalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(record.preOperationStateIdentity())) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Workstation inventory changed after endpoint preparation"
            );
        }
        if (!endpoint.endpointAccepts(record.effectKind(), record.slotIndex(), stack)) {
            return WorkstationEndpointEffectResult.failed(
                    record.effectKind() == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                            ? WorkstationEndpointResultCode.SOURCE_MISMATCH
                            : WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Workstation owner rejected the prepared endpoint effect"
            );
        }
        WorkstationEndpointOwnerResult ownerResult = WorkstationEndpointOwnerResult.create(
                record,
                WorkstationEndpointResultCode.APPLIED,
                Optional.empty()
        );
        WorkstationEndpointJournal committedJournal = runtime.journal().update(
                record.effectId(),
                (current, ownerRevision) -> current.transition(
                        WorkstationEndpointJournalState.EFFECT_COMMITTED,
                        ownerRevision,
                        current.postInventoryRevision(),
                        current.endpointEffectRevision(),
                        Optional.of(ownerResult),
                        Optional.empty()
                )
        );
        publishJournal(runtime, committedJournal);
        try {
            endpoint.applyCommittedEndpointEffect(
                    record.effectKind(),
                    record.slotIndex(),
                    stack,
                    record.expectedInventoryRevision(),
                    record.postInventoryRevision(),
                    record.endpointEffectRevision(),
                    record.journalSequence(),
                    record.effectId(),
                    ownerResult.evidenceIdentity()
            );
            if (!endpoint.endpointOperationStateIdentity().equals(record.postOperationStateIdentity())) {
                markJournalBlocked(
                        level.getServer(),
                        record.effectId(),
                        WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                        "Committed endpoint projection published an unexpected Workstation operation state"
                );
                return WorkstationEndpointEffectResult.failed(
                        WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                        "Endpoint effect committed but its operation-state projection requires recovery"
                );
            }
        } catch (RuntimeException exception) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Endpoint effect is durably committed and requires projection reconciliation: "
                            + exception.getMessage()
            );
        }
        publishResultAndReconcile(level.getServer(), record.effectId());
        return WorkstationEndpointEffectResult.applied(ownerResult);
    }

    public synchronized WorkstationEndpointEffectResult observeCommittedResult(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparation preparation
    ) {
        Objects.requireNonNull(preparation, "preparation");
        WorkstationEndpointJournalRecord record = load(level.getServer()).journal()
                .find(preparation.effectId())
                .orElse(null);
        if (record == null) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Prepared endpoint effect is missing from the authoritative journal"
            );
        }
        if (!record.instanceId().equals(preparation.instanceId())) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Prepared effect references another Workstation instance"
            );
        }
        if (record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME
                || record.state() == WorkstationEndpointJournalState.RECOVERY_REQUIRED) {
            return WorkstationEndpointEffectResult.failed(
                    record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME
                            ? WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                            : WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    record.failureDetail().orElse("Endpoint reconciliation is blocked")
            );
        }
        if (record.state() == WorkstationEndpointJournalState.REJECTED) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE,
                    record.failureDetail().orElse("Endpoint effect was rejected before commitment")
            );
        }
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) return resolved.failure().orElseThrow();
        if (!resolved.instance().orElseThrow().instanceId().equals(preparation.instanceId())) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Prepared effect references another Workstation instance"
            );
        }
        if (record.state() == WorkstationEndpointJournalState.REQUESTED
                || record.state() == WorkstationEndpointJournalState.PREPARED) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE,
                    "Endpoint effect is not committed; consequential work was not reissued"
            );
        }
        return reconcileExisting(level, resolved, record);
    }

    public synchronized WorkstationEndpointCancellationResult cancelPrepared(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparation preparation
    ) {
        Objects.requireNonNull(preparation, "preparation");
        ActiveEndpoints runtime = load(level.getServer());
        WorkstationEndpointJournalRecord record = runtime.journal().find(preparation.effectId()).orElse(null);
        if (record == null) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Prepared endpoint effect is missing from the authoritative journal"
            );
        }
        if (record.state() == WorkstationEndpointJournalState.REJECTED) {
            return WorkstationEndpointCancellationResult.cancelled();
        }
        if (record.state() != WorkstationEndpointJournalState.PREPARED
                || !WorkstationEndpointPreparation.from(record).equals(preparation)) {
            return WorkstationEndpointCancellationResult.failed(
                    record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME
                            ? WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                            : WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Endpoint preparation can no longer be cancelled safely: " + record.state()
            );
        }
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) {
            WorkstationEndpointEffectResult failure = resolved.failure().orElseThrow();
            return WorkstationEndpointCancellationResult.failed(failure.code(), failure.detail());
        }
        if (!resolved.instance().orElseThrow().instanceId().equals(record.instanceId())) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Prepared effect references another Workstation instance"
            );
        }
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.preparedEffectId().filter(record.effectId()::equals).isEmpty()
                || projection.preparedSlotIndex() != record.slotIndex()
                || projection.preparedInventoryRevision() != record.expectedInventoryRevision()
                || projection.inventoryRevision() != record.expectedInventoryRevision()
                || projection.endpointEffectRevision() != record.expectedEndpointEffectRevision()) {
            return WorkstationEndpointCancellationResult.failed(
                    WorkstationEndpointResultCode.UNKNOWN_OUTCOME,
                    "Prepared endpoint lock no longer matches the live Workstation projection"
            );
        }
        endpoint.releasePreparedEndpointEffect(record.effectId());
        try {
            WorkstationEndpointJournal rejected = runtime.journal().update(
                    record.effectId(),
                    (current, ownerRevision) -> current.transition(
                            WorkstationEndpointJournalState.REJECTED,
                            ownerRevision,
                            current.postInventoryRevision(),
                            current.endpointEffectRevision(),
                            Optional.empty(),
                            Optional.of("Prepared endpoint effect was explicitly cancelled before commitment")
                    )
            );
            publishJournal(runtime, rejected);
        } catch (RuntimeException exception) {
            endpoint.lockPreparedEndpointEffect(
                    record.effectId(),
                    record.slotIndex(),
                    record.expectedInventoryRevision()
            );
            throw exception;
        }
        return WorkstationEndpointCancellationResult.cancelled();
    }

    public synchronized void reconcileLoadedEndpoint(ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof WorkstationTransferEndpoint endpoint)
                || endpoint.endpointProjection().instanceId().isEmpty()) {
            return;
        }
        ResolvedEndpoint resolved = resolveAndEnroll(level, position);
        if (resolved.failure().isPresent()) return;
        WorkstationInstanceRecord instance = resolved.instance().orElseThrow();
        ActiveEndpoints runtime = load(level.getServer());
        clearProvenObsoletePreparedLock(level, endpoint, runtime.journal(), instance.instanceId());
        Optional<String> projectionConflict = projectionJournalConflict(
                endpoint.endpointProjection(),
                runtime.journal(),
                instance.instanceId()
        );
        if (projectionConflict.isPresent()) {
            long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
            WorkstationInstanceRegistry blocked = runtime.registry().update(
                    instance.instanceId(),
                    current -> current.transition(
                            WorkstationInstanceLifecycle.RECOVERY_REQUIRED,
                            revision,
                            projectionConflict,
                            projectionEffectReferences(endpoint.endpointProjection())
                    )
            );
            publishRegistry(runtime, blocked);
            return;
        }
        int processed = 0;
        for (WorkstationEndpointJournalRecord record : runtime.journal().records()) {
            if (processed >= configuration.maximumReconciliationsPerAction()) break;
            if (!record.instanceId().equals(instance.instanceId())) continue;
            if (record.state() == WorkstationEndpointJournalState.PREPARED) {
                restorePreparedLock(level, resolved, record);
                processed++;
            } else if (record.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED
                    || record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED) {
                reconcileExisting(level, resolved, record);
                processed++;
            }
        }
    }

    public synchronized boolean retireEndpoint(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof WorkstationTransferEndpoint endpoint)) return true;
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.instanceId().isEmpty()) return true;
        ActiveEndpoints runtime = load(level.getServer());
        WorkstationInstanceRecord record = runtime.registry().find(projection.instanceId().orElseThrow()).orElse(null);
        WorkstationEndpointKey actualKey = new WorkstationEndpointKey(
                endpoint.endpointTypeIdentity(),
                level.dimension().location().toString(),
                position.getX(),
                position.getY(),
                position.getZ()
        );
        if (record == null
                || !record.endpointKey().equals(actualKey)
                || record.generation() != projection.instanceGeneration()) {
            return false;
        }
        List<String> unresolved = unresolvedReferences(runtime.journal(), record.instanceId());
        if (!unresolved.isEmpty()) {
            for (WorkstationEndpointJournalRecord effect : runtime.journal().records()) {
                if (!effect.instanceId().equals(record.instanceId()) || effect.state().terminal()) continue;
                WorkstationEndpointJournalState blockedState = switch (effect.state()) {
                    case REQUESTED -> WorkstationEndpointJournalState.FAILED;
                    case PREPARED -> WorkstationEndpointJournalState.UNKNOWN_OUTCOME;
                    case EFFECT_COMMITTED, RESULT_PUBLISHED -> WorkstationEndpointJournalState.RECOVERY_REQUIRED;
                    case RECONCILED, REJECTED, FAILED, RECOVERY_REQUIRED, UNKNOWN_OUTCOME -> null;
                };
                if (blockedState == null) continue;
                ActiveEndpoints latest = load(level.getServer());
                WorkstationEndpointJournal blocked = latest.journal().update(
                        effect.effectId(),
                        (current, ownerRevision) -> current.transition(
                                blockedState,
                                ownerRevision,
                                current.postInventoryRevision(),
                                current.endpointEffectRevision(),
                                current.ownerResult(),
                                Optional.of("Workstation endpoint was removed before reconciliation completed")
                        )
                );
                publishJournal(latest, blocked);
            }
            runtime = load(level.getServer());
            unresolved = unresolvedReferences(runtime.journal(), record.instanceId());
        }
        WorkstationInstanceLifecycle target = unresolved.isEmpty()
                ? WorkstationInstanceLifecycle.RETIRED
                : WorkstationInstanceLifecycle.RECOVERY_REQUIRED;
        List<String> finalUnresolved = unresolved;
        long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
        WorkstationInstanceRegistry candidate = runtime.registry().update(
                record.instanceId(),
                current -> current.transition(
                        target,
                        revision,
                        Optional.of(finalUnresolved.isEmpty()
                                ? "workstation endpoint removed"
                                : "workstation removed with unresolved endpoint effects"),
                        finalUnresolved
                )
        );
        publishRegistry(runtime, candidate);
        return unresolved.isEmpty();
    }

    public static Path instanceFile(MinecraftServer server) {
        return ownerDirectory(server).resolve(WorkstationEndpointSchema.INSTANCE_FILE_NAME);
    }

    public static Path journalFile(MinecraftServer server) {
        return ownerDirectory(server).resolve(WorkstationEndpointSchema.JOURNAL_FILE_NAME);
    }

    private WorkstationEndpointObservationResult observeEffect(
            ServerLevel level,
            ResolvedEndpoint resolved,
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            WorkstationEndpointStackPayload payload
    ) {
        if (payload.decodedStack().length > configuration.maximumPayloadBytes()) {
            return WorkstationEndpointObservationResult.failed(
                    kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                            ? WorkstationEndpointResultCode.SOURCE_MISMATCH
                            : WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Exact endpoint stack exceeds the configured payload limit"
            );
        }
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), payload);
        } catch (RuntimeException exception) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Exact endpoint stack could not be decoded: " + exception.getMessage()
            );
        }
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.preparedEffectId().isPresent()) {
            return WorkstationEndpointObservationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Endpoint slot is already locked by a prepared effect"
            );
        }
        if (!endpoint.endpointAccepts(kind, slotIndex, stack)) {
            return WorkstationEndpointObservationResult.failed(
                    kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                            ? WorkstationEndpointResultCode.SOURCE_MISMATCH
                            : WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Workstation owner rejected the requested endpoint observation"
            );
        }
        return WorkstationEndpointObservationResult.observed(WorkstationEndpointObservation.create(
                resolved.instance().orElseThrow().instanceId(),
                kind,
                slotIndex,
                payload,
                projection.inventoryRevision(),
                projection.endpointEffectRevision(),
                endpoint.endpointOperationStateIdentity(),
                projection.lastAppliedJournalSequence(),
                configuration.endpointConfigurationIdentity()
        ));
    }

    private WorkstationEndpointPreparationResult prepareEffect(
            ServerLevel level,
            ResolvedEndpoint resolved,
            String invocationIdentity,
            WorkstationEndpointObservation observation
    ) {
        Objects.requireNonNull(level, "level");
        if (!observation.instanceId().equals(resolved.instance().orElseThrow().instanceId())
                || !observation.endpointConfigurationIdentity().equals(
                        configuration.endpointConfigurationIdentity()
                )) {
            return WorkstationEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Endpoint observation does not bind the resolved Workstation instance and configuration"
            );
        }
        WorkstationEndpointEffectKind kind = observation.effectKind();
        WorkstationEndpointStackPayload payload = observation.exactEffectStack();
        WorkstationEndpointEffectId effectId = WorkstationEndpointEffectId.create(
                resolved.instance().orElseThrow().instanceId(),
                invocationIdentity,
                kind
        );
        ActiveEndpoints runtime = load(level.getServer());
        Optional<WorkstationEndpointJournalRecord> existing = runtime.journal().find(effectId);
        if (existing.isPresent()) {
            WorkstationEndpointJournalRecord record = existing.orElseThrow();
            if (!record.exactStack().equals(payload)
                    || record.slotIndex() != observation.slotIndex()
                    || record.expectedInventoryRevision() != observation.inventoryRevision()
                    || record.expectedEndpointEffectRevision() != observation.endpointEffectRevision()
                    || !record.preFreshnessIdentity().equals(observation.freshnessIdentity())
                    || !record.preOperationStateIdentity().equals(observation.operationStateIdentity())
                    || !record.postOperationStateIdentity().equals(
                            resolved.endpoint().orElseThrow().endpointPostOperationStateIdentity(
                                    observation.effectKind()
                            )
                    )
                    || record.previousOwnerResultJournalSequence()
                    != observation.ownerResultJournalSequence()
                    || !record.endpointConfigurationIdentity().equals(
                            observation.endpointConfigurationIdentity()
                    )) {
                return WorkstationEndpointPreparationResult.failed(
                        WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                        "Endpoint invocation identity was reused with different canonical preparation content"
                );
            }
            if (record.state() == WorkstationEndpointJournalState.PREPARED
                    || record.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED
                    || record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED
                    || record.state() == WorkstationEndpointJournalState.RECONCILED) {
                return WorkstationEndpointPreparationResult.prepared(WorkstationEndpointPreparation.from(record));
            }
            if (record.state().terminal()) {
                return WorkstationEndpointPreparationResult.failed(
                        record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME
                                ? WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                                : WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                        record.failureDetail().orElse("Endpoint preparation is terminally blocked")
                );
            }
        }
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), payload);
        } catch (RuntimeException exception) {
            return WorkstationEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Exact endpoint stack could not be decoded: " + exception.getMessage()
            );
        }
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.inventoryRevision() != observation.inventoryRevision()
                || projection.endpointEffectRevision() != observation.endpointEffectRevision()
                || projection.lastAppliedJournalSequence() != observation.ownerResultJournalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(observation.operationStateIdentity())) {
            return WorkstationEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                    "Endpoint changed after the bound read-only observation"
            );
        }
        long expectedRevision = observation.inventoryRevision();
        int slotIndex = observation.slotIndex();
        if (endpoint.endpointSlotIndex(kind) != slotIndex
                || !endpoint.endpointAccepts(kind, slotIndex, stack)) {
            return WorkstationEndpointPreparationResult.failed(
                    kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                            ? WorkstationEndpointResultCode.SOURCE_MISMATCH
                            : WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    "Workstation owner rejected the requested endpoint effect"
            );
        }
        WorkstationEndpointJournal.AppendCandidate append = runtime.journal().request(
                resolved.instance().orElseThrow().instanceId(),
                invocationIdentity,
                kind,
                slotIndex,
                payload,
                expectedRevision,
                observation.endpointEffectRevision(),
                observation.operationStateIdentity(),
                endpoint.endpointPostOperationStateIdentity(kind),
                observation.ownerResultJournalSequence(),
                configuration.maximumJournalRecords()
        );
        if (!append.duplicate()) publishJournal(runtime, append.journal());
        runtime = load(level.getServer());
        WorkstationEndpointJournal preparedJournal = runtime.journal().update(
                effectId,
                (record, ownerRevision) -> record.transition(
                        WorkstationEndpointJournalState.PREPARED,
                        ownerRevision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
        publishJournal(runtime, preparedJournal);
        WorkstationEndpointJournalRecord prepared = preparedJournal.find(effectId).orElseThrow();
        try {
            endpoint.lockPreparedEndpointEffect(effectId, prepared.slotIndex(), prepared.expectedInventoryRevision());
        } catch (RuntimeException exception) {
            markJournalBlocked(
                    level.getServer(),
                    effectId,
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Durable preparation could not lock the live endpoint projection: " + exception.getMessage()
            );
            return WorkstationEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Endpoint preparation requires projection reconciliation"
            );
        }
        return WorkstationEndpointPreparationResult.prepared(WorkstationEndpointPreparation.from(prepared));
    }

    private WorkstationEndpointEffectResult reconcileExisting(
            ServerLevel level,
            ResolvedEndpoint resolved,
            WorkstationEndpointJournalRecord record
    ) {
        if (record.state() == WorkstationEndpointJournalState.RECONCILED
                || record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED) {
            WorkstationEndpointOwnerResult result = record.ownerResult().orElse(null);
            if (result == null) {
                return WorkstationEndpointEffectResult.failed(
                        WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                        "Published endpoint journal record has no owner result"
                );
            }
            if (record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED) {
                publishResultAndReconcile(level.getServer(), record.effectId());
            }
            return WorkstationEndpointEffectResult.duplicate(result);
        }
        if (record.state() == WorkstationEndpointJournalState.REJECTED
                || record.state() == WorkstationEndpointJournalState.FAILED) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.DESTINATION_REJECTED,
                    record.failureDetail().orElse("Endpoint request was rejected")
            );
        }
        if (record.state() == WorkstationEndpointJournalState.RECOVERY_REQUIRED
                || record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME) {
            return WorkstationEndpointEffectResult.failed(
                    record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME
                            ? WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                            : WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    record.failureDetail().orElse("Endpoint reconciliation is blocked")
            );
        }
        if (record.state() != WorkstationEndpointJournalState.EFFECT_COMMITTED) {
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Endpoint request has not reached a committed effect boundary"
            );
        }
        WorkstationEndpointOwnerResult result = record.ownerResult().orElseThrow();
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.lastEffectId().filter(record.effectId()::equals).isPresent()
                && projection.lastOwnerResultIdentity().filter(result.evidenceIdentity()::equals).isPresent()
                && projection.inventoryRevision() == record.postInventoryRevision()
                && projection.endpointEffectRevision() == record.endpointEffectRevision()
                && projection.lastAppliedJournalSequence() == record.journalSequence()
                && endpoint.endpointOperationStateIdentity().equals(record.postOperationStateIdentity())) {
            publishResultAndReconcile(level.getServer(), record.effectId());
            return WorkstationEndpointEffectResult.duplicate(result);
        }
        if (projection.inventoryRevision() != record.expectedInventoryRevision()
                || projection.endpointEffectRevision() != record.expectedEndpointEffectRevision()
                || projection.lastAppliedJournalSequence() != record.previousOwnerResultJournalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(record.preOperationStateIdentity())) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Live endpoint projection does not match committed pre-state or post-state"
            );
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed endpoint authority is proven but its live projection requires recovery"
            );
        }
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), record.exactStack());
        } catch (RuntimeException exception) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Persisted exact stack cannot be decoded: " + exception.getMessage()
            );
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Persisted exact stack cannot be decoded"
            );
        }
        if (!endpoint.endpointAccepts(record.effectKind(), record.slotIndex(), stack)) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Live endpoint pre-state does not accept the committed effect"
            );
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed endpoint projection cannot be reconciled automatically"
            );
        }
        if (projection.preparedEffectId().isEmpty()) {
            endpoint.lockPreparedEndpointEffect(
                    record.effectId(),
                    record.slotIndex(),
                    record.expectedInventoryRevision()
            );
        } else if (projection.preparedEffectId().filter(record.effectId()::equals).isEmpty()) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Live endpoint is locked by another effect"
            );
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed endpoint effect conflicts with another live lock"
            );
        }
        endpoint.applyCommittedEndpointEffect(
                record.effectKind(),
                record.slotIndex(),
                stack,
                record.expectedInventoryRevision(),
                record.postInventoryRevision(),
                record.endpointEffectRevision(),
                record.journalSequence(),
                record.effectId(),
                result.evidenceIdentity()
        );
        if (!endpoint.endpointOperationStateIdentity().equals(record.postOperationStateIdentity())) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Reconciled endpoint projection published an unexpected Workstation operation state"
            );
            return WorkstationEndpointEffectResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Committed endpoint operation state requires recovery"
            );
        }
        publishResultAndReconcile(level.getServer(), record.effectId());
        return WorkstationEndpointEffectResult.duplicate(result);
    }

    private void clearProvenObsoletePreparedLock(
            ServerLevel level,
            WorkstationTransferEndpoint endpoint,
            WorkstationEndpointJournal journal,
            WorkstationInstanceId instanceId
    ) {
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.preparedEffectId().isEmpty()) return;
        WorkstationEndpointJournalRecord rejected = journal.find(projection.preparedEffectId().orElseThrow())
                .orElse(null);
        if (rejected == null || rejected.state() != WorkstationEndpointJournalState.REJECTED
                || !rejected.instanceId().equals(instanceId)
                || rejected.slotIndex() != projection.preparedSlotIndex()
                || rejected.expectedInventoryRevision() != projection.preparedInventoryRevision()
                || projection.inventoryRevision() != rejected.expectedInventoryRevision()
                || projection.endpointEffectRevision() != rejected.expectedEndpointEffectRevision()
                || projection.lastAppliedJournalSequence() != rejected.previousOwnerResultJournalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(rejected.preOperationStateIdentity())) {
            return;
        }
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), rejected.exactStack());
        } catch (RuntimeException exception) {
            return;
        }
        if (!endpoint.endpointAccepts(rejected.effectKind(), rejected.slotIndex(), stack)) return;
        endpoint.releasePreparedEndpointEffect(rejected.effectId());
    }

    private void restorePreparedLock(
            ServerLevel level,
            ResolvedEndpoint resolved,
            WorkstationEndpointJournalRecord record
    ) {
        WorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        if (projection.preparedEffectId().filter(record.effectId()::equals).isPresent()
                && projection.preparedSlotIndex() == record.slotIndex()
                && projection.preparedInventoryRevision() == record.expectedInventoryRevision()) {
            return;
        }
        if (projection.preparedEffectId().isPresent()
                || projection.inventoryRevision() != record.expectedInventoryRevision()
                || projection.endpointEffectRevision() != record.expectedEndpointEffectRevision()
                || projection.lastAppliedJournalSequence() != record.previousOwnerResultJournalSequence()
                || !endpoint.endpointOperationStateIdentity().equals(record.preOperationStateIdentity())) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.UNKNOWN_OUTCOME,
                    "Prepared endpoint lock could not be reconciled with the live projection"
            );
            return;
        }
        ItemStack stack;
        try {
            stack = stackCodec.decode(level.registryAccess(), record.exactStack());
        } catch (RuntimeException exception) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.RECOVERY_REQUIRED,
                    "Prepared exact endpoint stack cannot be decoded: " + exception.getMessage()
            );
            return;
        }
        if (!endpoint.endpointAccepts(record.effectKind(), record.slotIndex(), stack)) {
            markJournalBlocked(
                    level.getServer(),
                    record.effectId(),
                    WorkstationEndpointJournalState.UNKNOWN_OUTCOME,
                    "Prepared endpoint pre-state no longer matches the live projection"
            );
            return;
        }
        endpoint.lockPreparedEndpointEffect(
                record.effectId(),
                record.slotIndex(),
                record.expectedInventoryRevision()
        );
    }

    private ResolvedEndpoint resolveAndEnroll(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof WorkstationTransferEndpoint endpoint)) {
            return ResolvedEndpoint.failed(
                    WorkstationEndpointResultCode.UNSUPPORTED_EFFECT,
                    "Block is not an IM-028A transfer-capable Workstation endpoint"
            );
        }
        WorkstationEndpointKey key = new WorkstationEndpointKey(
                endpoint.endpointTypeIdentity(),
                level.dimension().location().toString(),
                position.getX(),
                position.getY(),
                position.getZ()
        );
        ActiveEndpoints runtime = load(level.getServer());
        WorkstationEndpointProjection projection = endpoint.endpointProjection();
        WorkstationInstanceRecord record;
        if (projection.instanceId().isEmpty()) {
            Optional<WorkstationInstanceRecord> existing = runtime.registry().activeAt(key);
            if (existing.isPresent()) {
                WorkstationInstanceRecord conflicting = existing.orElseThrow();
                if (conflicting.lifecycle() == WorkstationInstanceLifecycle.PENDING_BINDING) {
                    endpoint.bindEndpointInstance(conflicting.instanceId(), conflicting.generation());
                    long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
                    WorkstationInstanceRegistry activated = runtime.registry().update(
                            conflicting.instanceId(),
                            current -> current.transition(
                                    WorkstationInstanceLifecycle.ACTIVE,
                                    revision,
                                    Optional.empty(),
                                    List.of()
                            )
                    );
                    publishRegistry(runtime, activated);
                    return ResolvedEndpoint.succeeded(
                            endpoint,
                            activated.find(conflicting.instanceId()).orElseThrow()
                    );
                }
                long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
                List<String> unresolved = unresolvedReferences(runtime.journal(), conflicting.instanceId());
                WorkstationInstanceRegistry conflictRegistry = runtime.registry().update(
                        conflicting.instanceId(),
                        current -> current.transition(
                                WorkstationInstanceLifecycle.IDENTITY_CONFLICT,
                                revision,
                                Optional.of("unbound block entity encountered at an already-owned endpoint"),
                                unresolved
                        )
                );
                publishRegistry(runtime, conflictRegistry);
                return ResolvedEndpoint.failed(
                        WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                        "Endpoint position was already owned by another Workstation instance"
                );
            }
            WorkstationInstanceRegistry.AllocationCandidate allocation = runtime.registry().allocate(
                    key,
                    configuration.maximumInstanceRecords()
            );
            publishRegistry(runtime, allocation.registry());
            record = allocation.record();
            endpoint.bindEndpointInstance(record.instanceId(), record.generation());
            runtime = load(level.getServer());
            long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
            WorkstationInstanceRegistry activated = runtime.registry().update(
                    record.instanceId(),
                    current -> current.transition(
                            WorkstationInstanceLifecycle.ACTIVE,
                            revision,
                            Optional.empty(),
                            List.of()
                    )
            );
            publishRegistry(runtime, activated);
            record = activated.find(record.instanceId()).orElseThrow();
        } else {
            record = runtime.registry().find(projection.instanceId().orElseThrow()).orElse(null);
            if (record == null
                    || !record.endpointKey().equals(key)
                    || record.generation() != projection.instanceGeneration()) {
                return ResolvedEndpoint.failed(
                        WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                        "Block-entity endpoint projection does not match the Workstation instance registry"
                );
            }
            if (record.lifecycle() == WorkstationInstanceLifecycle.PENDING_BINDING) {
                long revision = Math.addExact(runtime.registry().ownerRevision(), 1L);
                WorkstationInstanceRegistry activated = runtime.registry().update(
                        record.instanceId(),
                        current -> current.transition(
                                WorkstationInstanceLifecycle.ACTIVE,
                                revision,
                                Optional.empty(),
                                List.of()
                        )
                );
                publishRegistry(runtime, activated);
                record = activated.find(record.instanceId()).orElseThrow();
            }
            if (record.lifecycle() != WorkstationInstanceLifecycle.ACTIVE) {
                return ResolvedEndpoint.failed(
                        WorkstationEndpointResultCode.ENDPOINT_NOT_ACTIVE,
                        "Workstation endpoint is not ACTIVE: " + record.lifecycle()
                );
            }
        }
        return ResolvedEndpoint.succeeded(endpoint, record);
    }

    private void publishResultAndReconcile(MinecraftServer server, WorkstationEndpointEffectId effectId) {
        ActiveEndpoints runtime = load(server);
        WorkstationEndpointJournalRecord current = runtime.journal().find(effectId).orElseThrow();
        if (current.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED) {
            WorkstationEndpointJournal published = runtime.journal().update(
                    effectId,
                    (record, revision) -> record.transition(
                            WorkstationEndpointJournalState.RESULT_PUBLISHED,
                            revision,
                            record.postInventoryRevision(),
                            record.endpointEffectRevision(),
                            record.ownerResult(),
                            Optional.empty()
                    )
            );
            publishJournal(runtime, published);
            runtime = load(server);
            current = runtime.journal().find(effectId).orElseThrow();
        }
        if (current.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED) {
            WorkstationEndpointJournal reconciled = runtime.journal().update(
                    effectId,
                    (record, revision) -> record.transition(
                            WorkstationEndpointJournalState.RECONCILED,
                            revision,
                            record.postInventoryRevision(),
                            record.endpointEffectRevision(),
                            record.ownerResult(),
                            Optional.empty()
                    )
            );
            publishJournal(runtime, reconciled);
        }
    }

    private void markJournalBlocked(
            MinecraftServer server,
            WorkstationEndpointEffectId effectId,
            WorkstationEndpointJournalState state,
            String detail
    ) {
        ActiveEndpoints runtime = load(server);
        WorkstationEndpointJournal blocked = runtime.journal().update(
                effectId,
                (record, revision) -> record.transition(
                        state,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        record.ownerResult(),
                        Optional.of(detail)
                )
        );
        publishJournal(runtime, blocked);
    }

    private synchronized ActiveEndpoints load(MinecraftServer server) {
        ActiveEndpoints current = active.get();
        if (current != null && current.server() == server) return current;
        WorldIdentityRootIdentity worldIdentity = WorldIdentityRootIdentities.from(
                worldIdentityService.getOrCreate(server)
        );
        WorkstationInstanceStorage instanceStorage = new WorkstationInstanceStorage(instanceFile(server));
        WorkstationEndpointJournalStorage journalStorage = new WorkstationEndpointJournalStorage(journalFile(server));
        WorkstationInstanceRegistry registry = instanceStorage.loadExisting().orElseGet(() ->
                WorkstationInstanceRegistry.empty(
                        worldIdentity,
                        configuration.instanceAllocationConfigurationIdentity()
                )
        );
        WorkstationEndpointJournal journal = journalStorage.loadExisting().orElseGet(() ->
                WorkstationEndpointJournal.empty(worldIdentity, configuration.endpointConfigurationIdentity())
        );
        if (!registry.worldIdentity().equals(worldIdentity) || !journal.worldIdentity().equals(worldIdentity)) {
            throw new IllegalStateException("Workstation endpoint persistence references another World Identity");
        }
        if (!registry.allocationConfigurationIdentity().equals(
                configuration.instanceAllocationConfigurationIdentity()
        ) || !journal.endpointConfigurationIdentity().equals(configuration.endpointConfigurationIdentity())) {
            throw new IllegalStateException("Workstation endpoint persistence configuration identity mismatch");
        }
        if (registry.records().size() > configuration.maximumInstanceRecords()
                || journal.records().size() > configuration.maximumJournalRecords()) {
            throw new IllegalStateException("Workstation endpoint persistence exceeds configured bounded capacity");
        }
        for (WorkstationEndpointJournalRecord effect : journal.records()) {
            if (registry.find(effect.instanceId()).isEmpty()) {
                throw new IllegalStateException(
                        "Workstation endpoint journal references a missing instance: " + effect.instanceId().value()
                );
            }
            if (effect.exactStack().decodedStack().length > configuration.maximumPayloadBytes()) {
                throw new IllegalStateException("Workstation endpoint payload exceeds configured bounded capacity");
            }
            stackCodec.decode(server.registryAccess(), effect.exactStack());
        }
        ActiveEndpoints loaded = new ActiveEndpoints(server, instanceStorage, journalStorage, registry, journal);
        active.set(loaded);
        return loaded;
    }

    private void publishRegistry(ActiveEndpoints runtime, WorkstationInstanceRegistry candidate) {
        runtime.instanceStorage().save(candidate);
        active.set(runtime.withRegistry(candidate));
    }

    private void publishJournal(ActiveEndpoints runtime, WorkstationEndpointJournal candidate) {
        runtime.journalStorage().save(candidate);
        active.set(runtime.withJournal(candidate));
    }

    private static List<String> unresolvedReferences(
            WorkstationEndpointJournal journal,
            com.butchercraft.workstation.endpoint.WorkstationInstanceId instanceId
    ) {
        return journal.records().stream()
                .filter(record -> record.instanceId().equals(instanceId))
                .filter(record -> record.state() != WorkstationEndpointJournalState.RECONCILED
                        && record.state() != WorkstationEndpointJournalState.REJECTED
                        && record.state() != WorkstationEndpointJournalState.FAILED)
                .map(record -> record.effectId().value())
                .toList();
    }

    private static Optional<String> projectionJournalConflict(
            WorkstationEndpointProjection projection,
            WorkstationEndpointJournal journal,
            com.butchercraft.workstation.endpoint.WorkstationInstanceId instanceId
    ) {
        if (projection.preparedEffectId().isPresent()) {
            WorkstationEndpointJournalRecord prepared = journal.find(projection.preparedEffectId().orElseThrow())
                    .orElse(null);
            if (prepared == null
                    || !prepared.instanceId().equals(instanceId)
                    || prepared.state() != WorkstationEndpointJournalState.PREPARED
                    || prepared.slotIndex() != projection.preparedSlotIndex()
                    || prepared.expectedInventoryRevision() != projection.preparedInventoryRevision()) {
                return Optional.of("Block-entity prepared marker has no matching authoritative journal record");
            }
        }
        if (projection.lastEffectId().isPresent()) {
            WorkstationEndpointJournalRecord applied = journal.find(projection.lastEffectId().orElseThrow())
                    .orElse(null);
            if (applied == null
                    || !applied.instanceId().equals(instanceId)
                    || applied.journalSequence() != projection.lastAppliedJournalSequence()
                    || applied.ownerResult().isEmpty()
                    || projection.lastOwnerResultIdentity()
                    .filter(applied.ownerResult().orElseThrow().evidenceIdentity()::equals)
                    .isEmpty()) {
                return Optional.of("Block-entity applied-effect marker has no matching authoritative journal result");
            }
        }
        return Optional.empty();
    }

    private static List<String> projectionEffectReferences(WorkstationEndpointProjection projection) {
        java.util.LinkedHashSet<String> references = new java.util.LinkedHashSet<>();
        projection.preparedEffectId().map(WorkstationEndpointEffectId::value).ifPresent(references::add);
        projection.lastEffectId().map(WorkstationEndpointEffectId::value).ifPresent(references::add);
        return List.copyOf(references);
    }

    private static Path ownerDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(WorkstationEndpointSchema.DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private static ServerLevel loadedLevelFor(MinecraftServer server, String dimensionIdentity) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionIdentity);
        if (location == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    private record ActiveEndpoints(
            MinecraftServer server,
            WorkstationInstanceStorage instanceStorage,
            WorkstationEndpointJournalStorage journalStorage,
            WorkstationInstanceRegistry registry,
            WorkstationEndpointJournal journal
    ) {
        private ActiveEndpoints withRegistry(WorkstationInstanceRegistry candidate) {
            return new ActiveEndpoints(server, instanceStorage, journalStorage, candidate, journal);
        }

        private ActiveEndpoints withJournal(WorkstationEndpointJournal candidate) {
            return new ActiveEndpoints(server, instanceStorage, journalStorage, registry, candidate);
        }
    }

    private record ResolvedEndpoint(
            Optional<WorkstationTransferEndpoint> endpoint,
            Optional<WorkstationInstanceRecord> instance,
            Optional<WorkstationEndpointEffectResult> failure
    ) {
        private static ResolvedEndpoint succeeded(
                WorkstationTransferEndpoint endpoint,
                WorkstationInstanceRecord instance
        ) {
            return new ResolvedEndpoint(Optional.of(endpoint), Optional.of(instance), Optional.empty());
        }

        private static ResolvedEndpoint failed(WorkstationEndpointResultCode code, String detail) {
            return new ResolvedEndpoint(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(WorkstationEndpointEffectResult.failed(code, detail))
            );
        }
    }
}
