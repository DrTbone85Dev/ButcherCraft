package com.butchercraft.workstation.projection;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.condition.ConditionProjection;
import com.butchercraft.workstation.condition.ConditionInitializationEvidence;
import com.butchercraft.workstation.condition.MachineConditionPolicyRegistry;
import com.butchercraft.workstation.condition.MachineConditionState;
import com.butchercraft.workstation.condition.ConditionReceiptStorage;
import com.butchercraft.workstation.condition.ConditionEffectReceipt;
import com.butchercraft.workstation.condition.ConditionEffectKind;
import com.butchercraft.workstation.condition.ConditionTransitionBinding;
import com.butchercraft.workstation.condition.ConditionExposureType;
import com.butchercraft.workstation.condition.ConditionDueIndex;
import com.butchercraft.workstation.condition.ConditionDigest;
import com.butchercraft.workstation.condition.WorkstationConditionEngine;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.MachineOperatingStateService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.world.checkpoint.StartupRecoveryService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Workstation-owned durable projection publication and loaded-block reconciliation boundary. */
public final class DurableWorkstationProjectionService {
    public static final DurableWorkstationProjectionService INSTANCE = new DurableWorkstationProjectionService(
            WorkstationEndpointService.INSTANCE,
            WorldIdentityService.INSTANCE,
            MachineOperatingStateService.INSTANCE,
            ExecutionService.INSTANCE,
            new ExactItemStackCodec(),
            new WorkstationProjectionCodec()
    );

    private final WorkstationEndpointService endpointService;
    private final WorldIdentityService worldIdentityService;
    private final MachineOperatingStateService operatingStateService;
    private final ExecutionService executionService;
    private final ExactItemStackCodec stackCodec;
    private final WorkstationProjectionCodec projectionCodec;
    private Optional<MachineConditionPolicyRegistry> conditionPolicies = Optional.empty();
    private MinecraftServer conditionReceiptServer;
    private ConditionReceiptStorage conditionReceiptStorage;
    private final ConditionDueIndex conditionDue = new ConditionDueIndex();
    private final java.util.Set<WorkstationInstanceId> conditionContinuouslyLoaded = new java.util.HashSet<>();
    private long lastConditionClockTick = -1;

    public synchronized void installConditionPolicies(MachineConditionPolicyRegistry policies) {
        Objects.requireNonNull(policies, "policies");
        if (conditionReceiptServer != null && conditionPolicies.isPresent()
                && !conditionPolicies.orElseThrow().identity().equals(policies.identity())) {
            throw new IllegalStateException("Condition policy composition cannot change outside an authorized boundary");
        }
        conditionPolicies = Optional.of(policies);
    }

    public synchronized String conditionPolicyRegistryIdentity() {
        return conditionPolicies.orElseThrow().identity();
    }

    /** Explicit owner-safe configuration transition; no command or gameplay policy enables this in IM-033A. */
    public synchronized DurableWorkstationProjection changeConditionPolicy(MinecraftServer server,
            WorkstationInstanceId identity, String policyIdentity, long tick) {
        var read = readWithLoadedValidation(server, identity);
        if (read.code() != WorkstationProjectionReadCode.AVAILABLE) throw new IllegalStateException(read.detail());
        var current = read.projection().orElseThrow();
        var operating = operatingStateService.find(server, identity.value());
        if (operating.flatMap(MachineOperatingRecord::activeChildOperationId).isPresent()
                || current.processingOperationIdentity().map(ExecutionOperationId::of)
                .flatMap(id -> executionService.managerFor(server).find(id)).filter(value -> !value.status().terminal()).isPresent()) {
            throw new IllegalStateException("Prepared or admitted child freezes its condition policy");
        }
        var next = conditionPolicies.orElseThrow().require(policyIdentity, current.endpointKey().workstationTypeIdentity());
        if (current.condition().orElseThrow().state().orElseThrow().policy().equals(next)) return current;
        current = settleCondition(server, current, tick, true);
        var condition = current.condition().orElseThrow();
        var before = condition.state().orElseThrow();
        String effect = ConditionDigest.identity("butchercraft:condition_policy_transition/v1", before.instanceId().value(),
                before.digest(), next.identity(), Long.toString(tick));
        var after = WorkstationConditionEngine.changePolicy(before, effect, next, tick);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.POLICY_TRANSITION,
                effect, condition.receiptHead(), effect);
        var result = publishCondition(server, current, condition.committed(receipt), Optional.of(receipt));
        openConditionExposure(server, result, tick);
        return read(server, identity).projection().orElseThrow();
    }

    DurableWorkstationProjectionService(
            WorkstationEndpointService endpointService,
            WorldIdentityService worldIdentityService,
            MachineOperatingStateService operatingStateService,
            ExecutionService executionService,
            ExactItemStackCodec stackCodec,
            WorkstationProjectionCodec projectionCodec
    ) {
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.operatingStateService = Objects.requireNonNull(operatingStateService, "operatingStateService");
        this.executionService = Objects.requireNonNull(executionService, "executionService");
        this.stackCodec = Objects.requireNonNull(stackCodec, "stackCodec");
        this.projectionCodec = Objects.requireNonNull(projectionCodec, "projectionCodec");
    }

    /** Publishes an operator-authorized, proof-complete pre-R3A projection into a disposable world root. */
    synchronized FrozenWorkstationProjectionSnapshot publishProvenLegacyBootstrap(
            Path worldRoot,
            DurableWorkstationProjection projection
    ) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        Objects.requireNonNull(projection, "projection");
        if (projection.status() != WorkstationProjectionStatus.ACTIVE || projection.projectionRevision() != 1L) {
            throw new IllegalArgumentException("Legacy projection bootstrap must publish active revision 1");
        }
        Path ownerRoot = worldRoot.toAbsolutePath().normalize().resolve("butchercraft");
        return LegacyWorkstationProjectionAnalyzer.projectionStorage(ownerRoot).save(projection);
    }

    public synchronized WorkstationProjectionReconciliationResult reconcileLoaded(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(workstation, "workstation");
        boolean consequentialMutationPermitted = StartupMutationGateService.INSTANCE.permits(
                level.getServer(), LegacySplitRecoveryParticipants.WORKSTATION);
        if (!consequentialMutationPermitted
                && !StartupMutationGateService.INSTANCE.startupDecisionComplete(level.getServer())) {
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.DEFERRED_BY_STARTUP_GATE,
                    Optional.empty(),
                    "Durable Workstation projection reconciliation waits for startup authority"
            );
        }
        boolean previouslyBound = workstation.checkpointInstanceIdentity().isPresent();
        WorkstationEndpointReferenceResult reference = consequentialMutationPermitted
                ? endpointService.referenceFor(level, workstation.getBlockPos())
                : endpointService.existingReferenceForRecoveryReconciliation(
                        level, workstation.getBlockPos());
        if (!reference.succeeded()) {
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.IDENTITY_CONFLICT,
                    Optional.empty(), reference.detail());
        }
        WorkstationInstanceRecord instance = endpointService.instanceRecord(
                level.getServer(), reference.reference().orElseThrow().instanceId()).orElseThrow();
        WorkstationProjectionReadResult persisted = storage(level.getServer()).read(instance);
        if (persisted.code() == WorkstationProjectionReadCode.LEGACY_UNAVAILABLE) {
            if (!consequentialMutationPermitted) {
                return new WorkstationProjectionReconciliationResult(
                        WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                        Optional.empty(),
                        "Authority-blocked Workstation has no exact durable projection");
            }
            if (workstation.durableProjectionRevision() > 0L) {
                return new WorkstationProjectionReconciliationResult(
                        WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                        Optional.empty(),
                        "Loaded Workstation references a missing durable projection"
                );
            }
            return bootstrapLoadedLegacy(level, workstation, instance, previouslyBound);
        }
        if (persisted.code() != WorkstationProjectionReadCode.AVAILABLE) {
            return new WorkstationProjectionReconciliationResult(
                    persisted.code() == WorkstationProjectionReadCode.IDENTITY_CONFLICT
                            ? WorkstationProjectionReconciliationCode.IDENTITY_CONFLICT
                            : WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                    Optional.empty(), persisted.detail());
        }

        DurableWorkstationProjection durable = persisted.projection().orElseThrow();
        conditionReceipts(level.getServer());
        if (consequentialMutationPermitted) durable = finishConditionTransition(level.getServer(), durable);
        if (consequentialMutationPermitted && !conditionContinuouslyLoaded.contains(durable.instanceId())
                && durable.condition().flatMap(ConditionProjection::state).flatMap(MachineConditionState::activeExposure).isPresent()) {
            durable = settleCondition(level.getServer(), durable,
                    durable.condition().orElseThrow().state().orElseThrow().lastAccountedTick(), true);
        }
        if (consequentialMutationPermitted) conditionContinuouslyLoaded.add(durable.instanceId());
        validateCondition(level.getServer(), durable);
        if (workstation.conditionProjection().isEmpty()) workstation.acceptConditionProjection(durable.condition());
        validateExactStacks(level, durable);
        DurableWorkstationProjection live = capture(level, workstation, instance, durable.projectionRevision());
        boolean registryAdvanceOnly = provenMonotonicInstanceRegistryRevision(
                durable,
                live,
                endpointService.instanceRegistrySnapshot(level.getServer())
        );
        if (live.sameAuthoritativeState(durable) || registryAdvanceOnly) {
            durable = migrateCondition(level.getServer(), durable, !previouslyBound);
            workstation.acceptConditionProjection(durable.condition());
            workstation.acceptDurableProjectionReference(durable.projectionRevision(), durable.stateDigest());
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.EQUAL, Optional.of(durable),
                    registryAdvanceOnly
                            ? "Loaded block entity matches durable Workstation state under newer exact registry evidence"
                            : "Loaded block entity equals durable Workstation projection");
        }

        if (laterLiveProjectionIsProven(level.getServer(), live, durable)) {
            long nextRevision = Math.addExact(
                    Math.max(durable.projectionRevision(), workstation.durableProjectionRevision()),
                    1L
            );
            DurableWorkstationProjection repaired = capture(
                    level, workstation, instance, nextRevision);
            storage(level.getServer()).save(repaired);
            repaired = migrateCondition(level.getServer(), repaired, false);
            workstation.acceptConditionProjection(repaired.condition());
            workstation.acceptDurableProjectionReference(repaired.projectionRevision(), repaired.stateDigest());
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.OWNER_EVIDENCE_REPAIRED_DURABLE_PROJECTION,
                    Optional.of(repaired),
                    "Immutable Workstation owner evidence advanced the stale durable projection");
        }

        if (workstation.durableProjectionRevision() <= durable.projectionRevision()) {
            RestoreAndVerifyResult restored = restoreAndVerify(level, workstation, instance, durable);
            WorkstationProjectionReconciliationCode code = restored.ownerEvidenceAdvanced()
                    ? WorkstationProjectionReconciliationCode.OWNER_EVIDENCE_REPAIRED_DURABLE_PROJECTION
                    : WorkstationProjectionReconciliationCode.DURABLE_APPLIED_TO_LOADED_BLOCK_ENTITY;
            return new WorkstationProjectionReconciliationResult(
                    code,
                    Optional.of(restored.projection()),
                    restored.ownerEvidenceAdvanced()
                            ? "Durable Workstation state reconciled the loaded block entity and immutable owner "
                            + "evidence advanced its external reference"
                            : "Newer proven durable Workstation projection reconciled the loaded block entity");
        }

        return new WorkstationProjectionReconciliationResult(
                WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED,
                Optional.empty(),
                "Loaded Workstation and durable projection differ without immutable owner proof");
    }

    public synchronized DurableWorkstationProjection publishAuthorizedMutation(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(workstation, "workstation");
        WorkstationInstanceId identity = workstation.checkpointInstanceIdentity().orElseThrow(() ->
                new IllegalStateException("Durable projection publication requires Workstation Instance Identity"));
        WorkstationInstanceRecord instance = endpointService.instanceRecord(level.getServer(), identity)
                .orElseThrow(() -> new IllegalStateException("Durable projection instance is absent from authority"));
        if (instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE) {
            WorkstationProjectionReadResult retired = storage(level.getServer()).read(instance);
            return retired.projection().orElseThrow(() ->
                    new IllegalStateException("Non-active Workstation has no durable terminal projection"));
        }
        WorkstationProjectionReadResult persisted = storage(level.getServer()).read(instance);
        if (persisted.code() != WorkstationProjectionReadCode.AVAILABLE) {
            throw new IllegalStateException("Consequential Workstation mutation has no durable baseline: "
                    + persisted.code() + ": " + persisted.detail());
        }
        DurableWorkstationProjection current = persisted.projection().orElseThrow();
        DurableWorkstationProjection candidate = capture(
                level, workstation, instance, Math.addExact(current.projectionRevision(), 1L));
        validateCondition(level.getServer(), candidate);
        if (candidate.sameAuthoritativeState(current)) {
            workstation.acceptDurableProjectionReference(current.projectionRevision(), current.stateDigest());
            return current;
        }
        storage(level.getServer()).save(candidate);
        workstation.acceptDurableProjectionReference(candidate.projectionRevision(), candidate.stateDigest());
        return candidate;
    }

    public synchronized WorkstationProjectionReconciliationResult retire(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord retiredInstance,
            String reason
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(workstation, "workstation");
        Objects.requireNonNull(retiredInstance, "retiredInstance");
        if (retiredInstance.lifecycle() != WorkstationInstanceLifecycle.RETIRED) {
            throw new IllegalArgumentException("Durable projection tombstone requires retired instance authority");
        }
        WorkstationProjectionStorage storage = storage(level.getServer());
        DurableWorkstationProjection persisted = storage.readForRetirement(retiredInstance);
        DurableWorkstationProjection active;
        if (persisted != null && persisted.status() == WorkstationProjectionStatus.TOMBSTONED) {
            DurableWorkstationProjection existing = persisted;
            workstation.acceptDurableProjectionReference(existing.projectionRevision(), existing.stateDigest());
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.RETIRED, Optional.of(existing),
                    "Existing Workstation retirement tombstone observed");
        }
        if (persisted == null) {
            active = capture(
                    level,
                    workstation,
                    retiredInstance,
                    1L,
                    retiredInstance.lastUpdateRevision());
        } else {
            active = persisted;
        }
        if (active.condition().flatMap(ConditionProjection::state).isPresent()) {
            var condition = active.condition().orElseThrow();
            if (condition.pendingOperatingTransition().isPresent()) throw new IllegalStateException("Incomplete condition transition blocks retirement");
            var before = condition.state().orElseThrow();
            String effect = ConditionDigest.identity("butchercraft:condition_retirement/v1", before.instanceId().value(), before.digest());
            var after = WorkstationConditionEngine.retire(before, effect,
                    com.butchercraft.world.simulation.SimulationClockService.INSTANCE.clock(level.getServer()).simulationTick(),
                    conditionContinuouslyLoaded.contains(before.instanceId()));
            var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.RETIREMENT, effect,
                    condition.receiptHead(), effect);
            conditionReceipts(level.getServer()).stage(receipt);
            active = active.withCondition(Math.incrementExact(active.projectionRevision()), condition.committed(receipt));
            storage.save(active);
        }
        DurableWorkstationProjection tombstone = active.tombstone(
                Math.addExact(active.projectionRevision(), 1L), retiredInstance.lastUpdateRevision(), reason);
        storage.save(tombstone);
        conditionContinuouslyLoaded.remove(tombstone.instanceId());
        conditionDue.remove(tombstone.instanceId());
        workstation.acceptConditionProjection(tombstone.condition());
        workstation.acceptDurableProjectionReference(tombstone.projectionRevision(), tombstone.stateDigest());
        return new WorkstationProjectionReconciliationResult(
                WorkstationProjectionReconciliationCode.RETIRED, Optional.of(tombstone),
                "Workstation retirement tombstone published");
    }

    public synchronized void refreshOperatingStateReference(MinecraftServer server, String instanceIdentity) {
        Objects.requireNonNull(server, "server");
        WorkstationInstanceId identity = new WorkstationInstanceId(instanceIdentity);
        WorkstationInstanceRecord instance = endpointService.instanceRecord(server, identity).orElse(null);
        if (instance == null || instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE) return;
        WorkstationProjectionReadResult read = storage(server).read(instance);
        if (read.code() != WorkstationProjectionReadCode.AVAILABLE) return;
        DurableWorkstationProjection current = read.projection().orElseThrow();
        current = finishConditionTransition(server, current);
        DurableWorkstationProjection candidate = current.withOperatingStateReference(
                Math.addExact(current.projectionRevision(), 1L), operatingReference(server, identity));
        if (candidate.equals(current)) {
            synchronizeLoadedProjectionReference(server, instance, current);
            openConditionExposure(server, current);
            return;
        }
        storage(server).save(candidate);
        synchronizeLoadedProjectionReference(server, instance, candidate);
        openConditionExposure(server, candidate);
    }

    public synchronized WorkstationProjectionReadResult read(MinecraftServer server, WorkstationInstanceId instanceId) {
        WorkstationInstanceRecord instance = endpointService.instanceRecord(
                Objects.requireNonNull(server, "server"), Objects.requireNonNull(instanceId, "instanceId")).orElse(null);
        if (instance == null) {
            return WorkstationProjectionReadResult.unavailable(
                    instanceId, WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                    "Unknown Workstation Instance Identity");
        }
        return storage(server).read(instance);
    }

    public synchronized WorkstationProjectionReadResult readWithLoadedValidation(
            MinecraftServer server,
            WorkstationInstanceId instanceId
    ) {
        WorkstationProjectionReadResult read = read(server, instanceId);
        if (read.projection().isEmpty()) return read;
        try {
            validateCondition(server, read.projection().orElseThrow());
        } catch (RuntimeException exception) {
            return WorkstationProjectionReadResult.unavailable(instanceId, WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                    exception.getMessage());
        }
        if (read.code() == WorkstationProjectionReadCode.RETIRED) {
            return read;
        }
        DurableWorkstationProjection projection = read.projection().orElseThrow();
        ResourceLocation dimension = ResourceLocation.tryParse(projection.endpointKey().dimensionIdentity());
        if (dimension == null) {
            return WorkstationProjectionReadResult.unavailable(
                    instanceId, WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                    "Projection dimension identity is invalid");
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        BlockPos position = new BlockPos(
                projection.endpointKey().x(), projection.endpointKey().y(), projection.endpointKey().z());
        if (level == null || !level.hasChunkAt(position)) return read;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof AbstractInventoryWorkstationBlockEntity workstation)) {
            return WorkstationProjectionReadResult.unavailable(
                    instanceId, WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                    "Loaded physical Workstation block entity is missing or has the wrong type");
        }
        if (workstation.durablePublicationUncertain()) {
            return WorkstationProjectionReadResult.unavailable(instanceId, WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                    "Workstation joint publication outcome is unresolved");
        }
        if (!workstation.durableBlockEntityTypeIdentity().equals(projection.blockEntityTypeIdentity())
                || workstation.checkpointInstanceIdentity().filter(instanceId::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != projection.instanceGeneration()) {
            return WorkstationProjectionReadResult.unavailable(
                    instanceId, WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                    "Loaded physical Workstation does not match exact projection identity");
        }
        WorkstationInstanceRecord instance = endpointService.instanceRecord(server, instanceId).orElseThrow();
        try {
            DurableWorkstationProjection loadedProjection = capture(
                    level, workstation, instance, projection.projectionRevision());
            if (!loadedProjection.sameAuthoritativeState(projection)
                    && !provenMonotonicInstanceRegistryRevision(
                    projection,
                    loadedProjection,
                    endpointService.instanceRegistrySnapshot(server))) {
                return WorkstationProjectionReadResult.unavailable(
                        instanceId, WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                        "Loaded Workstation authoritative state differs from its durable projection; mismatched fields="
                                + projection.authoritativeMismatchFields(loadedProjection));
            }
        } catch (RuntimeException exception) {
            return WorkstationProjectionReadResult.unavailable(
                    instanceId, WorkstationProjectionReadCode.RECOVERY_REQUIRED,
                    exception.getMessage() == null
                            ? "Loaded Workstation projection validation failed"
                            : exception.getMessage());
        }
        return read;
    }

    public synchronized List<WorkstationProjectionReadResult> enumerate(MinecraftServer server) {
        WorkstationInstanceRegistry registry = endpointService.instanceRegistrySnapshot(
                Objects.requireNonNull(server, "server"));
        return storage(server).enumerate(registry);
    }

    public synchronized WorkstationProjectionDiagnostics diagnostics(MinecraftServer server) {
        List<WorkstationProjectionReadResult> results = enumerate(server);
        int available = 0;
        int retired = 0;
        int legacyUnavailable = 0;
        int blocked = 0;
        long bytes = 0L;
        WorkstationProjectionStorage storage = storage(server);
        for (WorkstationProjectionReadResult result : results) {
            switch (result.code()) {
                case AVAILABLE -> available++;
                case RETIRED -> retired++;
                case LEGACY_UNAVAILABLE -> legacyUnavailable++;
                case IDENTITY_CONFLICT, CORRUPT, UNSUPPORTED_SCHEMA, RECOVERY_REQUIRED -> blocked++;
            }
            if (result.projection().isPresent()) {
                bytes = Math.addExact(bytes, storage.size(result.instanceId()));
            }
        }
        return new WorkstationProjectionDiagnostics(
                results.size(), available, retired, legacyUnavailable, blocked, bytes);
    }

    public synchronized FrozenWorkstationProjectionSnapshot freezeForCheckpoint(
            MinecraftServer server,
            WorkstationInstanceId instanceId
    ) {
        WorkstationInstanceRecord instance = endpointService.instanceRecord(server, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Workstation Instance Identity"));
        return storage(server).freezeForCheckpoint(instance);
    }

    public static Path projectionRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve("butchercraft")
                .resolve(WorkstationProjectionSchema.DIRECTORY_NAME)
                .resolve(WorkstationProjectionSchema.PROJECTION_DIRECTORY_NAME)
                .resolve(WorkstationProjectionSchema.SCHEMA_DIRECTORY_NAME)
                .toAbsolutePath().normalize();
    }

    private WorkstationProjectionReconciliationResult bootstrapLoadedLegacy(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord instance,
            boolean previouslyBound
    ) {
        if (instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE
                || workstation.checkpointInstanceIdentity().filter(instance.instanceId()::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != instance.generation()) {
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.IDENTITY_CONFLICT, Optional.empty(),
                    "Legacy Workstation identity is not coherent enough to bootstrap a projection");
        }
        if (workstation.durablePreparedEndpointEffectIdentity().isPresent()
                || endpointService.hasUnresolvedEffects(level.getServer(), instance.instanceId())) {
            return new WorkstationProjectionReconciliationResult(
                    WorkstationProjectionReconciliationCode.RECOVERY_REQUIRED, Optional.empty(),
                    "Unresolved endpoint effect blocks legacy durable projection bootstrap");
        }
        DurableWorkstationProjection initial = capture(level, workstation, instance, 1L);
        conditionReceipts(level.getServer());
        conditionContinuouslyLoaded.add(instance.instanceId());
        storage(level.getServer()).save(initial);
        initial = migrateCondition(level.getServer(), initial, !previouslyBound);
        workstation.acceptConditionProjection(initial.condition());
        workstation.acceptDurableProjectionReference(initial.projectionRevision(), initial.stateDigest());
        WorkstationProjectionReconciliationCode code = previouslyBound
                ? WorkstationProjectionReconciliationCode.LEGACY_BOOTSTRAPPED
                : WorkstationProjectionReconciliationCode.INITIALIZED;
        return new WorkstationProjectionReconciliationResult(
                code, Optional.of(initial), "Loaded coherent Workstation durable projection initialized");
    }

    private DurableWorkstationProjection capture(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord instance,
            long projectionRevision
    ) {
        WorkstationInstanceRegistry registry = endpointService.instanceRegistrySnapshot(level.getServer());
        if (registry.find(instance.instanceId()).isEmpty()) {
            throw new IllegalStateException("Workstation projection references an unregistered instance");
        }
        return capture(level, workstation, instance, projectionRevision, registry.ownerRevision());
    }

    private DurableWorkstationProjection capture(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord instance,
            long projectionRevision,
            long instanceRegistryRevision
    ) {
        if (workstation.checkpointInstanceIdentity().filter(instance.instanceId()::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != instance.generation()) {
            throw new IllegalStateException("Loaded Workstation identity differs from instance authority");
        }
        List<ItemStack> liveSlots = workstation.durableInventorySnapshot();
        List<WorkstationProjectionSlot> slots = new ArrayList<>(liveSlots.size());
        for (int slot = 0; slot < liveSlots.size(); slot++) {
            ItemStack stack = liveSlots.get(slot);
            slots.add(new WorkstationProjectionSlot(
                    slot,
                    workstation.durableConfiguredSlotCapacity(slot),
                    workstation.durableEffectiveSlotCapacity(slot, stack),
                    stack.isEmpty() ? Optional.empty() : Optional.of(stackCodec.encode(level.registryAccess(), stack))
            ));
        }
        WorldIdentityRootIdentity worldIdentity = WorldIdentityRootIdentities.from(
                worldIdentityService.getOrCreate(level.getServer()));
        if (!worldIdentity.equals(instance.worldIdentity())) {
            throw new IllegalStateException("Loaded Workstation references another World Identity");
        }
        DurableWorkstationProjection captured = DurableWorkstationProjection.active(
                worldIdentity,
                instance.instanceId(),
                instance.endpointKey(),
                instance.generation(),
                instance.allocationConfigurationIdentity(),
                workstation.durableBlockEntityTypeIdentity(),
                instanceRegistryRevision,
                projectionRevision,
                workstation.durableInventoryRevision(),
                workstation.durableEndpointEffectRevision(),
                workstation.durableLastAppliedJournalSequence(),
                workstation.durableSlotCapacityConfigurationIdentity(),
                slots,
                projectionCodec.encodeBlockEntityProjection(
                        workstation.durableProjectionStateSnapshot(level.registryAccess())),
                workstation.durablePreparedEndpointEffectIdentity(),
                workstation.durableLastEndpointEffectIdentity(),
                workstation.durableLastEndpointOwnerResultIdentity(),
                workstation.durableProcessingOperationIdentity(),
                workstation.durableProcessingOwnerResultIdentity(),
                operatingReference(level.getServer(), instance.instanceId())
        );
        return workstation.conditionProjection().map(value -> captured.withCondition(projectionRevision, value))
                .orElse(captured);
    }

    private RestoreAndVerifyResult restoreAndVerify(
            ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord instance,
            DurableWorkstationProjection durable
    ) {
        workstation.restoreDurableProjectionState(
                projectionCodec.decodeBlockEntityProjection(durable), level.registryAccess());
        workstation.acceptConditionProjection(durable.condition());
        DurableWorkstationProjection verified = capture(level, workstation, instance, durable.projectionRevision());
        if (!verified.sameAuthoritativeState(durable)
                && provenMonotonicInstanceRegistryRevision(
                durable,
                verified,
                endpointService.instanceRegistrySnapshot(level.getServer()))) {
            durable = migrateCondition(level.getServer(), durable, false);
            workstation.acceptConditionProjection(durable.condition());
            workstation.acceptDurableProjectionReference(durable.projectionRevision(), durable.stateDigest());
            return new RestoreAndVerifyResult(durable, false);
        }
        if (!verified.sameAuthoritativeState(durable)
                && laterLiveProjectionIsProven(level.getServer(), verified, durable)) {
            long nextRevision = Math.addExact(
                    Math.max(durable.projectionRevision(), workstation.durableProjectionRevision()),
                    1L
            );
            DurableWorkstationProjection repaired = capture(level, workstation, instance, nextRevision);
            storage(level.getServer()).save(repaired);
            repaired = migrateCondition(level.getServer(), repaired, false);
            workstation.acceptConditionProjection(repaired.condition());
            workstation.acceptDurableProjectionReference(repaired.projectionRevision(), repaired.stateDigest());
            return new RestoreAndVerifyResult(repaired, true);
        }
        if (!verified.sameAuthoritativeState(durable)) {
            throw new IllegalStateException("Durable Workstation projection failed exact loaded reconciliation for "
                    + durable.instanceId().value() + "; mismatched fields="
                    + durable.authoritativeMismatchFields(verified)
                    + "; expected digest=" + durable.stateDigest()
                    + "; actual digest=" + verified.stateDigest()
                    + policyBProofDiagnostic(level.getServer(), durable, verified));
        }
        durable = migrateCondition(level.getServer(), durable, false);
        workstation.acceptConditionProjection(durable.condition());
        workstation.acceptDurableProjectionReference(durable.projectionRevision(), durable.stateDigest());
        return new RestoreAndVerifyResult(durable, false);
    }

    private DurableWorkstationProjection migrateCondition(MinecraftServer server,
            DurableWorkstationProjection projection, boolean newInstance) {
        if (projection.condition().isPresent() || conditionPolicies.isEmpty()) return projection;
        if (!StartupMutationGateService.INSTANCE.permits(server, LegacySplitRecoveryParticipants.WORKSTATION)) return projection;
        if (projection.preparedEndpointEffectIdentity().isPresent()
                || endpointService.hasUnresolvedEffects(server, projection.instanceId())) return projection;
        if (projection.processingOperationIdentity().isPresent()) {
            var operation = executionService.managerFor(server).find(
                    ExecutionOperationId.of(projection.processingOperationIdentity().orElseThrow()));
            if (operation.isEmpty() || !operation.orElseThrow().status().terminal()) return projection;
        }
        DurableWorkstationProjection successor = com.butchercraft.workstation.condition.ConditionInitialization.candidate(
                projection, conditionPolicies.orElseThrow(),
                com.butchercraft.world.simulation.SimulationClockService.INSTANCE.clock(server).simulationTick(), newInstance, true);
        storage(server).save(successor);
        return successor;
    }

    public synchronized DurableWorkstationProjection ensureConditionReady(ServerLevel level,
            AbstractInventoryWorkstationBlockEntity workstation) {
        WorkstationInstanceId instance = workstation.checkpointInstanceIdentity().orElseThrow(() ->
                new IllegalStateException("Condition preparation requires reconciled Workstation Instance Identity"));
        var read = read(level.getServer(), instance);
        DurableWorkstationProjection current = read.projection().orElseThrow(() ->
                new IllegalStateException("Condition preparation requires durable projection: " + read.code() + ": " + read.detail()));
        DurableWorkstationProjection ready = migrateCondition(level.getServer(), current, false);
        validateCondition(level.getServer(), ready);
        workstation.acceptConditionProjection(ready.condition());
        workstation.acceptDurableProjectionReference(ready.projectionRevision(), ready.stateDigest());
        return ready;
    }

    private void validateCondition(MinecraftServer server, DurableWorkstationProjection projection) {
        if (projection.condition().isEmpty()) return;
        var result = com.butchercraft.workstation.condition.ConditionCoherenceValidator.validate(projection,
                conditionPolicies.orElseThrow(() -> new IllegalStateException("Condition policy registry unavailable")),
                conditionReceipts(server)::read);
        if (!result.coherent()) throw new IllegalStateException(result.code() + ": " + result.detail());
    }

    public synchronized ConditionReceiptStorage conditionReceipts(MinecraftServer server) {
        if (conditionReceiptServer != server) {
            conditionReceiptStorage = new ConditionReceiptStorage(projectionRoot(server).getParent().getParent()
                    .resolve("condition_effects").resolve("v1"));
            conditionReceiptServer = server;
            conditionDue.clear();
            conditionContinuouslyLoaded.clear();
            lastConditionClockTick = -1;
        }
        return conditionReceiptStorage;
    }

    /** This is preparation inside the existing Workstation owner boundary, before the operating file changes. */
    public synchronized void prepareConditionTransition(MinecraftServer server, MachineOperatingRecord successor) {
        var read = read(server, successor.workstation().instanceId());
        if (read.code() != WorkstationProjectionReadCode.AVAILABLE) return;
        var current = read.projection().orElseThrow();
        if (current.condition().flatMap(ConditionProjection::state).isEmpty()) return;
        var condition = current.condition().orElseThrow();
        var before = condition.state().orElseThrow();
        if (before.policy().inert()) return;
        // An admitted child freezes condition. Non-exposure bookkeeping cannot invalidate that plan.
        if (before.activeExposure().isEmpty() && (successor.activeChildOperationId().isPresent()
                || (successor.state() != MachineOperatingState.RUNNING_EMPTY
                && successor.state() != MachineOperatingState.OUTPUT_BLOCKED))) return;
        if (condition.pendingOperatingTransition().isPresent()) {
            throw new IllegalStateException("A condition/operating transition is already awaiting reconciliation");
        }
        ConditionTransitionBinding binding = new ConditionTransitionBinding(current.operatingStateReference(),
                operatingReference(successor), successor.lastObservedSimulationTick());
        String effect = ConditionDigest.identity("butchercraft:condition_transition_effect/v1",
                before.instanceId().value(), before.digest(), binding.identity());
        MachineConditionState after;
        if (before.activeExposure().isPresent()) {
            if (conditionContinuouslyLoaded.contains(before.instanceId())) {
                after = WorkstationConditionEngine.closeExposure(before, effect,
                        before.activeExposure().orElseThrow().availabilityProofIdentity(), binding.tick(),
                        successor.endpointAvailability() == com.butchercraft.workstation.operation.MachineEndpointAvailability.AVAILABLE
                                ? MachineConditionState.Suspension.STOPPED : MachineConditionState.Suspension.UNLOADED);
            } else {
                after = WorkstationConditionEngine.suspendAtDurableCutoff(before, effect,
                        MachineConditionState.Suspension.RESTART_REQUIRED);
            }
        } else {
            after = WorkstationConditionEngine.bindOperatingTransition(before, effect, binding.tick());
        }
        ConditionEffectReceipt receipt = ConditionEffectReceipt.prepare(before, after,
                ConditionEffectKind.OPERATING_TRANSITION, binding.identity(), condition.receiptHead(), binding.identity(),
                Optional.of(binding));
        ConditionProjection prepared = condition.committed(receipt);
        prepared = new ConditionProjection(prepared.applicability(), prepared.state(), prepared.receiptHead(),
                Optional.of(receipt.digest()));
        publishCondition(server, current, prepared, Optional.of(receipt));
    }

    private DurableWorkstationProjection finishConditionTransition(MinecraftServer server, DurableWorkstationProjection current) {
        if (current.condition().flatMap(ConditionProjection::pendingOperatingTransition).isEmpty()) return current;
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKSTATION);
        var condition = current.condition().orElseThrow();
        var receipt = conditionReceipts(server).read(condition.pendingOperatingTransition().orElseThrow());
        var binding = receipt.transition().orElseThrow();
        if (!condition.receiptHead().filter(receipt.digest()::equals).isPresent()
                || !condition.state().orElseThrow().equals(receipt.postState())
                || !current.operatingStateReference().equals(binding.previous())
                || !operatingReference(server, current.instanceId()).filter(binding.successor()::equals).isPresent()) {
            throw new IllegalStateException("Condition closure is durable but exact operating successor is unproven");
        }
        var completed = new ConditionProjection(condition.applicability(), condition.state(), condition.receiptHead(), Optional.empty());
        var paired = current.withOperatingStateReference(Math.incrementExact(current.projectionRevision()),
                Optional.of(binding.successor())).withCondition(Math.incrementExact(current.projectionRevision()), completed);
        storage(server).save(paired);
        synchronizeLoadedProjectionReference(server, endpointService.instanceRecord(server, paired.instanceId()).orElseThrow(), paired);
        paired.condition().flatMap(ConditionProjection::state).ifPresent(conditionDue::observe);
        return paired;
    }

    private DurableWorkstationProjection publishCondition(MinecraftServer server, DurableWorkstationProjection baseline,
            ConditionProjection condition, Optional<ConditionEffectReceipt> receipt) {
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKSTATION);
        var current = read(server, baseline.instanceId()).projection().orElseThrow();
        if (!current.equals(baseline)) throw new IllegalStateException("Stale condition owner candidate");
        receipt.ifPresent(conditionReceipts(server)::stage);
        com.butchercraft.workstation.condition.ConditionEvidenceClosure.verify(new ConditionProjection(
                condition.applicability(), condition.state(), condition.receiptHead(), Optional.empty()),
                conditionReceipts(server)::read);
        var candidate = baseline.withCondition(Math.incrementExact(baseline.projectionRevision()), condition);
        storage(server).save(candidate);
        synchronizeLoadedProjectionReference(server, endpointService.instanceRecord(server, candidate.instanceId()).orElseThrow(), candidate);
        condition.state().ifPresent(conditionDue::observe);
        return candidate;
    }

    private void openConditionExposure(MinecraftServer server, DurableWorkstationProjection projection) {
        openConditionExposure(server, projection, operatingStateService.find(server, projection.instanceId().value())
                .map(MachineOperatingRecord::lastObservedSimulationTick).orElse(0L));
    }

    private void openConditionExposure(MinecraftServer server, DurableWorkstationProjection projection, long boundaryTick) {
        var condition = projection.condition().orElse(null);
        if (condition == null || condition.state().isEmpty() || condition.pendingOperatingTransition().isPresent()) return;
        var before = condition.state().orElseThrow();
        if (before.activeExposure().isPresent() || before.fault().isPresent()) return;
        var operating = operatingStateService.find(server, projection.instanceId().value()).orElse(null);
        if (operating == null || operating.activeChildOperationId().isPresent()
                || operating.endpointAvailability() != com.butchercraft.workstation.operation.MachineEndpointAvailability.AVAILABLE
                || !conditionContinuouslyLoaded.contains(projection.instanceId())) return;
        ConditionExposureType type = switch (operating.state()) {
            case RUNNING_EMPTY -> ConditionExposureType.DRY_RUNNING;
            case OUTPUT_BLOCKED -> ConditionExposureType.BLOCKED_POWERED;
            default -> null;
        };
        if (type == null || before.policy().exposure(type).isEmpty()) return;
        String proof = ConditionDigest.identity("butchercraft:condition_loaded_interval/v1", before.instanceId().value(),
                before.digest(), operating.contentDigest());
        String effect = ConditionDigest.identity("butchercraft:condition_open/v1", proof);
        var after = WorkstationConditionEngine.openExposure(before, effect, type, operating.contentDigest(),
                operating.revision(), proof, boundaryTick, false);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.EXPOSURE_OPENED,
                proof, condition.receiptHead(), operating.contentDigest());
        publishCondition(server, projection, condition.committed(receipt), Optional.of(receipt));
    }

    public synchronized void settleConditionDue(MinecraftServer server, long tick) {
        conditionReceipts(server);
        for (var entry : conditionDue.due(tick, 64)) {
            var read = read(server, entry.instanceId());
            if (read.code() != WorkstationProjectionReadCode.AVAILABLE) { conditionDue.remove(entry.instanceId()); continue; }
            var projection = read.projection().orElseThrow();
            var state = projection.condition().flatMap(ConditionProjection::state).orElseThrow();
            if (!state.digest().equals(entry.conditionDigest())) { conditionDue.observe(state); continue; }
            settleCondition(server, projection, tick, false);
        }
    }

    public synchronized void advanceCondition(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!StartupMutationGateService.INSTANCE.permits(event.getServer(), LegacySplitRecoveryParticipants.WORKSTATION)) return;
        conditionReceipts(event.getServer());
        long tick = com.butchercraft.world.simulation.SimulationClockService.INSTANCE.clock(event.getServer()).simulationTick();
        if (lastConditionClockTick >= 0 && tick != lastConditionClockTick && tick != lastConditionClockTick + 1) {
            // Only active indexed intervals are visited; a Clock jump is never physical exposure.
            conditionContinuouslyLoaded.clear();
            for (var entry : conditionDue.due(Long.MAX_VALUE, Integer.MAX_VALUE)) {
                var read = read(event.getServer(), entry.instanceId());
                if (read.code() == WorkstationProjectionReadCode.AVAILABLE) {
                    settleCondition(event.getServer(), read.projection().orElseThrow(), tick, true);
                } else conditionDue.remove(entry.instanceId());
            }
        }
        lastConditionClockTick = tick;
        settleConditionDue(event.getServer(), tick);
    }

    private DurableWorkstationProjection settleCondition(MinecraftServer server, DurableWorkstationProjection projection,
            long tick, boolean close) {
        var condition = projection.condition().orElseThrow();
        var before = condition.state().orElseThrow();
        if (condition.pendingOperatingTransition().isPresent()) throw new IllegalStateException("Condition transition is incomplete");
        if (before.activeExposure().isEmpty()) return projection;
        String effect = ConditionDigest.identity("butchercraft:condition_settlement/v1", before.instanceId().value(),
                before.digest(), Long.toString(tick), Boolean.toString(close));
        boolean proven = conditionContinuouslyLoaded.contains(before.instanceId());
        var after = !proven ? WorkstationConditionEngine.suspendAtDurableCutoff(before, effect,
                MachineConditionState.Suspension.RESTART_REQUIRED)
                : close ? WorkstationConditionEngine.closeExposure(before, effect,
                before.activeExposure().orElseThrow().availabilityProofIdentity(), tick, MachineConditionState.Suspension.UNLOADED)
                : WorkstationConditionEngine.settle(before, effect,
                before.activeExposure().orElseThrow().availabilityProofIdentity(), tick);
        var receipt = ConditionEffectReceipt.prepare(before, after, !proven ? ConditionEffectKind.EXPOSURE_SUSPENDED
                : close ? ConditionEffectKind.EXPOSURE_CLOSED : ConditionEffectKind.EXPOSURE_SETTLED,
                effect, condition.receiptHead(), effect);
        return publishCondition(server, projection, condition.committed(receipt), Optional.of(receipt));
    }

    public synchronized void conditionUnloaded(MinecraftServer server, WorkstationInstanceId identity, long tick) {
        var read = read(server, identity);
        if (read.code() == WorkstationProjectionReadCode.AVAILABLE
                && read.projection().orElseThrow().condition().flatMap(ConditionProjection::state).isPresent()) {
            settleCondition(server, read.projection().orElseThrow(), tick, true);
        }
        conditionContinuouslyLoaded.remove(identity);
        conditionDue.remove(identity);
    }

    public synchronized void prepareConditionCheckpoint(MinecraftServer server, long tick,
            List<WorkstationInstanceId> required) {
        for (var identity : required) {
            var read = read(server, identity);
            if (read.code() != WorkstationProjectionReadCode.AVAILABLE && read.code() != WorkstationProjectionReadCode.RETIRED) continue;
            var before = read.projection().orElseThrow();
            var projection = migrateCondition(server, before, false);
            if (!projection.equals(before)) synchronizeLoadedProjectionReference(server,
                    endpointService.instanceRecord(server, identity).orElseThrow(), projection);
            if (projection.condition().flatMap(ConditionProjection::state).isPresent()) settleCondition(server, projection, tick, false);
        }
    }

    private boolean laterLiveProjectionIsProven(
            MinecraftServer server,
            DurableWorkstationProjection live,
            DurableWorkstationProjection durable
    ) {
        if (live.processingOwnerResultIdentity().filter(value -> value.startsWith("butchercraft:workstation_result/v2/")).isPresent()
                && !live.processingOwnerResultIdentity().equals(durable.processingOwnerResultIdentity())) {
            return jointProcessingSuccessorIsProven(server, live, durable);
        }
        MachineOperatingRecord operating = operatingStateService
                .find(server, live.instanceId().value()).orElse(null);
        var startup = StartupRecoveryService.INSTANCE.status();
        if (startup.lastRestorationResultIdentity().isPresent()
                && provenPolicyBOperatingSuccessor(durable, live, operating, startup.policyBRuns())) {
            return true;
        }
        if (live.inventoryRevision() <= durable.inventoryRevision()) return false;
        boolean endpointProof = endpointService.provesCommittedProjection(
                server,
                live.instanceId(),
                live.inventoryRevision(),
                live.endpointEffectRevision(),
                live.lastAppliedJournalSequence(),
                live.lastEndpointOwnerResultIdentity()
        );
        boolean processingProof = live.processingOperationIdentity().isPresent()
                && live.processingOwnerResultIdentity().isPresent()
                && processingOwnerResultIsDurable(
                server,
                live.processingOperationIdentity().orElseThrow(),
                live.processingOwnerResultIdentity().orElseThrow()
        );
        return endpointProof || processingProof;
    }

    private boolean jointProcessingSuccessorIsProven(MinecraftServer server,
            DurableWorkstationProjection live, DurableWorkstationProjection durable) {
        if (live.processingOperationIdentity().isEmpty() || live.condition().isEmpty()
                || durable.condition().isEmpty() || live.inventoryRevision() <= durable.inventoryRevision()
                || !live.instanceId().equals(durable.instanceId())
                || live.endpointEffectRevision() != durable.endpointEffectRevision()
                || live.lastAppliedJournalSequence() != durable.lastAppliedJournalSequence()
                || !processingOwnerResultIsDurable(server, live.processingOperationIdentity().orElseThrow(),
                live.processingOwnerResultIdentity().orElseThrow())) return false;
        try {
            validateCondition(server, live);
            var receipt = conditionReceipts(server).closure(live.condition().orElseThrow()).stream()
                    .filter(value -> value.processing().isPresent()
                            && com.butchercraft.workstation.condition.ConditionProcessingCandidates.resultIdentity(value)
                            .equals(live.processingOwnerResultIdentity().orElseThrow()))
                    .findFirst().orElseThrow();
            var binding = receipt.processing().orElseThrow();
            var digest = new ConditionDigest("butchercraft:joint_processing_inventory/v1").add(live.slots().size());
            for (var slot : live.slots()) {
                digest.add(slot.exactStack().isEmpty());
                slot.exactStack().ifPresent(stack -> digest.add(stack.contentDigest()));
            }
            return binding.operation().value().equals(live.processingOperationIdentity().orElseThrow())
                    && binding.postInventoryDigest().equals(digest.finish())
                    && binding.plannedProjectionRevision() > durable.projectionRevision()
                    && receipt.postState().equals(live.condition().orElseThrow().state().orElseThrow())
                    && receipt.preConditionDigest().equals(durable.condition().orElseThrow().state().orElseThrow().digest())
                    && receipt.previousReceiptDigest().equals(durable.condition().orElseThrow().receiptHead());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void synchronizeLoadedProjectionReference(
            MinecraftServer server,
            WorkstationInstanceRecord instance,
            DurableWorkstationProjection projection
    ) {
        ResourceLocation dimension = ResourceLocation.tryParse(instance.endpointKey().dimensionIdentity());
        if (dimension == null) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        BlockPos position = new BlockPos(
                instance.endpointKey().x(), instance.endpointKey().y(), instance.endpointKey().z());
        if (level == null || !level.hasChunkAt(position)) return;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof AbstractInventoryWorkstationBlockEntity workstation)
                || workstation.checkpointInstanceIdentity().filter(instance.instanceId()::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != instance.generation()
                || !workstation.durableBlockEntityTypeIdentity().equals(projection.blockEntityTypeIdentity())) {
            return;
        }
        workstation.acceptDurableProjectionReference(projection.projectionRevision(), projection.stateDigest());
        workstation.acceptConditionProjection(projection.condition());
    }

    static boolean provenMonotonicInstanceRegistryRevision(
            DurableWorkstationProjection durable,
            DurableWorkstationProjection live,
            WorkstationInstanceRegistry registry
    ) {
        if (durable == null || live == null || registry == null
                || !durable.authoritativeMismatchFields(live)
                .equals(List.of("instance_registry_revision"))
                || live.instanceRegistryRevision() <= durable.instanceRegistryRevision()
                || live.instanceRegistryRevision() != registry.ownerRevision()
                || !registry.worldIdentity().equals(live.worldIdentity())
                || !registry.allocationConfigurationIdentity()
                .equals(live.instanceAllocationConfigurationIdentity())) {
            return false;
        }
        WorkstationInstanceRecord record = registry.find(live.instanceId()).orElse(null);
        return record != null
                && record.lifecycle() == WorkstationInstanceLifecycle.ACTIVE
                && record.worldIdentity().equals(live.worldIdentity())
                && record.endpointKey().equals(live.endpointKey())
                && record.generation() == live.instanceGeneration()
                && record.allocationConfigurationIdentity()
                .equals(live.instanceAllocationConfigurationIdentity());
    }

    static boolean provenPolicyBOperatingSuccessor(
            DurableWorkstationProjection durable,
            DurableWorkstationProjection live,
            MachineOperatingRecord current,
            List<String> policyBRunIdentities
    ) {
        if (durable == null || live == null || current == null
                || !durable.authoritativeMismatchFields(live)
                .equals(List.of("operating_state_reference"))) {
            return false;
        }
        WorkstationOperatingStateReference prior = durable.operatingStateReference().orElse(null);
        WorkstationOperatingStateReference observed = live.operatingStateReference().orElse(null);
        if (prior == null || observed == null
                || !prior.workstationInstanceIdentity().equals(durable.instanceId().value())
                || !observed.workstationInstanceIdentity().equals(durable.instanceId().value())
                || !current.workstation().instanceId().equals(durable.instanceId())
                || current.state() != MachineOperatingState.RESTART_REQUIRED
                || current.revision() != Math.addExact(prior.revision(), 1L)
                || current.priorRestartState().map(Enum::name).filter(prior.state()::equals).isEmpty()
                || !observed.equals(new WorkstationOperatingStateReference(
                current.workstation().instanceId().value(),
                current.revision(),
                current.state().name(),
                current.contentDigest()))) {
            return false;
        }
        MachineOperatingState priorState;
        try {
            priorState = MachineOperatingState.valueOf(prior.state());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return priorState.powered()
                && current.currentRunIdentity().map(run -> policyBRunIdentities.contains(run.value())).orElse(false);
    }

    private boolean processingOwnerResultIsDurable(
            MinecraftServer server,
            String operationIdentity,
            String ownerResultIdentity
    ) {
        ExecutionOperationSnapshot operation;
        try {
            operation = executionService.managerFor(server).find(new ExecutionOperationId(operationIdentity)).orElse(null);
        } catch (RuntimeException exception) {
            return false;
        }
        return operation != null && operation.ownerResultEvidence().isPresent()
                && operation.ownerResultEvidence().orElseThrow().ownerResultIdentity().equals(ownerResultIdentity);
    }

    private Optional<WorkstationOperatingStateReference> operatingReference(
            MinecraftServer server,
            WorkstationInstanceId instanceId
    ) {
        return operatingStateService.find(server, instanceId.value()).map(this::operatingReference);
    }

    private WorkstationOperatingStateReference operatingReference(MachineOperatingRecord record) {
        return new WorkstationOperatingStateReference(
                record.workstation().instanceId().value(),
                record.revision(),
                record.state().name(),
                record.contentDigest()
        );
    }

    private String policyBProofDiagnostic(
            MinecraftServer server,
            DurableWorkstationProjection durable,
            DurableWorkstationProjection live
    ) {
        if (!durable.authoritativeMismatchFields(live).equals(List.of("operating_state_reference"))) return "";
        var startup = StartupRecoveryService.INSTANCE.status();
        MachineOperatingRecord current = operatingStateService.find(server, live.instanceId().value()).orElse(null);
        List<String> failures = new ArrayList<>();
        if (startup.lastRestorationResultIdentity().isEmpty()) failures.add("missing_restoration_result");
        if (current == null) {
            failures.add("missing_operating_record");
        } else {
            WorkstationOperatingStateReference prior = durable.operatingStateReference().orElse(null);
            WorkstationOperatingStateReference observed = live.operatingStateReference().orElse(null);
            if (prior == null) failures.add("missing_prior_reference");
            if (observed == null) failures.add("missing_observed_reference");
            if (prior != null
                    && !prior.workstationInstanceIdentity().equals(durable.instanceId().value())) {
                failures.add("prior_instance_mismatch");
            }
            if (observed != null
                    && !observed.workstationInstanceIdentity().equals(durable.instanceId().value())) {
                failures.add("observed_instance_mismatch");
            }
            if (!current.workstation().instanceId().equals(durable.instanceId())) {
                failures.add("owner_instance_mismatch");
            }
            if (current.state() != MachineOperatingState.RESTART_REQUIRED) failures.add("state_not_restart_required");
            if (prior != null && current.revision() != Math.addExact(prior.revision(), 1L)) {
                failures.add("revision_not_single_successor");
            }
            if (prior != null && current.priorRestartState().map(Enum::name)
                    .filter(prior.state()::equals).isEmpty()) {
                failures.add("prior_state_mismatch");
            }
            if (current.currentRunIdentity().map(run -> startup.policyBRuns().contains(run.value())).orElse(false)) {
                // Bound by the immutable Restoration Result.
            } else {
                failures.add("run_not_in_restoration_result");
            }
            WorkstationOperatingStateReference expectedObserved = new WorkstationOperatingStateReference(
                    current.workstation().instanceId().value(),
                    current.revision(),
                    current.state().name(),
                    current.contentDigest());
            if (observed != null && !observed.equals(expectedObserved)) {
                failures.add("observed_owner_reference_mismatch(expected=" + expectedObserved
                        + ", observed=" + observed + ")");
            }
            if (prior != null) {
                try {
                    if (!MachineOperatingState.valueOf(prior.state()).powered()) {
                        failures.add("prior_state_not_powered");
                    }
                } catch (IllegalArgumentException exception) {
                    failures.add("prior_state_unrecognized");
                }
            }
        }
        if (failures.isEmpty()) failures.add("unclassified_policy_b_proof_failure");
        return "; policy B proof failures=" + failures
                + "; restoration result=" + startup.lastRestorationResultIdentity().orElse("none")
                + "; startup state=" + startup.state()
                + "; policy B runs=" + startup.policyBRuns();
    }

    private void validateExactStacks(ServerLevel level, DurableWorkstationProjection projection) {
        projection.slots().forEach(slot -> slot.exactStack().ifPresent(payload -> {
            ItemStack decoded = stackCodec.decode(level.registryAccess(), payload);
            if (decoded.getCount() != payload.count()) {
                throw new IllegalStateException("Durable Workstation projection ItemStack count mismatch");
            }
        }));
    }

    private WorkstationProjectionStorage storage(MinecraftServer server) {
        return new WorkstationProjectionStorage(projectionRoot(server), projectionCodec);
    }

    private record RestoreAndVerifyResult(
            DurableWorkstationProjection projection,
            boolean ownerEvidenceAdvanced
    ) {
    }
}
