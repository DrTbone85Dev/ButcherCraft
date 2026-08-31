package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecordV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Live schema-2 Workstation owner boundary. Schema-1 history remains immutable in its journal root. */
public final class StackAwareWorkstationEndpointRuntimeService {
    public static final StackAwareWorkstationEndpointRuntimeService INSTANCE =
            new StackAwareWorkstationEndpointRuntimeService(
                    WorkstationEndpointService.INSTANCE,
                    WorkstationEndpointConfiguration.standard(),
                    new ExactItemStackCodec()
            );

    private final WorkstationEndpointService instanceAuthority;
    private final WorkstationEndpointConfiguration configuration;
    private final ExactItemStackCodec stackCodec;
    private final AtomicReference<ActiveRuntime> active = new AtomicReference<>();

    StackAwareWorkstationEndpointRuntimeService(
            WorkstationEndpointService instanceAuthority,
            WorkstationEndpointConfiguration configuration,
            ExactItemStackCodec stackCodec
    ) {
        this.instanceAuthority = Objects.requireNonNull(instanceAuthority, "instanceAuthority");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.stackCodec = Objects.requireNonNull(stackCodec, "stackCodec");
    }

    public synchronized void initialize(ServerStartedEvent event) {
        loadExisting(event.getServer());
    }

    public void stop(ServerStoppingEvent event) {
        ActiveRuntime current = active.get();
        if (current != null && current.server() == event.getServer()) active.compareAndSet(current, null);
    }

    public synchronized boolean activeFor(MinecraftServer server) {
        return runtime(server).isPresent();
    }

    public synchronized void activate(MinecraftServer server, WorkstationEndpointJournalV2 candidate) {
        StartupMutationGateService.INSTANCE.require(server, LegacySplitRecoveryParticipants.WORKSTATION);
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(candidate, "candidate");
        Optional<ActiveRuntime> existing = runtime(server);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().service().journalSnapshot().equals(candidate)) {
                throw new IllegalStateException("Schema-2 Workstation journal is already active with different state");
            }
            return;
        }
        if (!candidate.endpointConfigurationIdentity().equals(
                configuration.stackAwareEndpointConfigurationIdentity())) {
            throw new IllegalArgumentException("Schema-2 Workstation endpoint configuration identity mismatch");
        }
        WorkstationEndpointJournalV2Storage storage = storage(server);
        storage.save(candidate);
        instanceAuthority.makeLegacyJournalReadOnly(server);
        active.set(new ActiveRuntime(server, new StackAwareWorkstationEndpointService(candidate, storage, stackCodec)));
    }

    public synchronized Optional<WorkstationEndpointJournalV2> currentJournal(MinecraftServer server) {
        return runtime(server).map(value -> value.service().journalSnapshot());
    }

    public synchronized StackAwareEndpointObservationResult observeWithdrawal(
            ServerLevel level,
            BlockPos position,
            int quantity
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) return resolved.failure().orElseThrow();
        return requireRuntime(level.getServer()).service().observeWithdrawal(
                level.registryAccess(), resolved.endpoint().orElseThrow(),
                resolved.endpoint().orElseThrow().endpointSlotIndex(
                        com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ), quantity
        );
    }

    public synchronized StackAwareEndpointObservationResult observeDeposit(
            ServerLevel level,
            BlockPos position,
            ItemStack payload
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) return resolved.failure().orElseThrow();
        return requireRuntime(level.getServer()).service().observeDeposit(
                level.registryAccess(), resolved.endpoint().orElseThrow(),
                resolved.endpoint().orElseThrow().endpointSlotIndex(
                        com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                ), payload
        );
    }

    public synchronized StackAwareEndpointObservationResult observeReturn(
            ServerLevel level,
            BlockPos position,
            ItemStack payload
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) return resolved.failure().orElseThrow();
        return requireRuntime(level.getServer()).service().observeReturn(
                level.registryAccess(), resolved.endpoint().orElseThrow(),
                resolved.endpoint().orElseThrow().endpointSlotIndex(
                        com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind.SOURCE_RETURN
                ), payload
        );
    }

    public synchronized StackAwareEndpointPreparationResult prepare(
            ServerLevel level,
            BlockPos position,
            String invocationIdentity,
            WorkstationEndpointObservationV2 observation
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) {
            return StackAwareEndpointPreparationResult.failed(
                    resolved.failure().orElseThrow().code(), resolved.failure().orElseThrow().detail()
            );
        }
        ActiveRuntime runtime = requireRuntime(level.getServer());
        if (runtime.service().journalSnapshot().records().size() >= configuration.maximumJournalRecords()
                && runtime.service().journalSnapshot().find(
                com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2.create(
                        observation.instanceId(), invocationIdentity, observation.effectKind()
        )).isEmpty()) {
            return StackAwareEndpointPreparationResult.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Schema-2 Workstation endpoint journal capacity is exhausted"
            );
        }
        return runtime.service().prepare(resolved.endpoint().orElseThrow(), invocationIdentity, observation);
    }

    public synchronized StackAwareEndpointEffectResult commit(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparationV2 preparation
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) {
            return StackAwareEndpointEffectResult.failed(
                    resolved.failure().orElseThrow().code(), resolved.failure().orElseThrow().detail()
            );
        }
        return requireRuntime(level.getServer()).service().commit(
                level.registryAccess(), resolved.endpoint().orElseThrow(), preparation
        );
    }

    public synchronized StackAwareEndpointEffectResult observeCommittedResult(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparationV2 preparation
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) {
            return StackAwareEndpointEffectResult.failed(
                    resolved.failure().orElseThrow().code(), resolved.failure().orElseThrow().detail()
            );
        }
        return requireRuntime(level.getServer()).service().observeCommittedResult(
                level.registryAccess(), resolved.endpoint().orElseThrow(), preparation
        );
    }

    public synchronized WorkstationEndpointCancellationResult cancelPrepared(
            ServerLevel level,
            BlockPos position,
            WorkstationEndpointPreparationV2 preparation
    ) {
        Resolved resolved = resolve(level, position);
        if (resolved.failure().isPresent()) {
            return WorkstationEndpointCancellationResult.failed(
                    resolved.failure().orElseThrow().code(), resolved.failure().orElseThrow().detail()
            );
        }
        return requireRuntime(level.getServer()).service().cancelPrepared(
                resolved.endpoint().orElseThrow(), preparation
        );
    }

    public synchronized void reconcileLoadedEndpoint(ServerLevel level, BlockPos position) {
        if (!StartupMutationGateService.INSTANCE.permits(
                level.getServer(), LegacySplitRecoveryParticipants.WORKSTATION)) return;
        Optional<ActiveRuntime> runtime = runtime(level.getServer());
        if (runtime.isEmpty()) return;
        Resolved resolved = resolve(level, position);
        if (resolved.endpoint().isEmpty()) return;
        StackAwareWorkstationTransferEndpoint endpoint = resolved.endpoint().orElseThrow();
        int reconciled = 0;
        for (WorkstationEndpointJournalRecordV2 record : runtime.orElseThrow().service().journalSnapshot().records()) {
            if (reconciled >= configuration.maximumReconciliationsPerAction()) break;
            if (!record.preparation().observation().instanceId().equals(endpoint.endpointInstanceId())
                    || record.state() == WorkstationEndpointJournalState.RECONCILED
                    || record.state() == WorkstationEndpointJournalState.REJECTED
                    || record.state() == WorkstationEndpointJournalState.FAILED) {
                continue;
            }
            runtime.orElseThrow().service().reconcile(level.registryAccess(), endpoint, record, false);
            reconciled++;
        }
    }

    public synchronized boolean hasUnresolvedEffects(
            MinecraftServer server,
            com.butchercraft.workstation.endpoint.WorkstationInstanceId instanceId
    ) {
        return !unresolvedEffectIdentities(server, instanceId).isEmpty();
    }

    public synchronized java.util.List<String> unresolvedEffectIdentities(
            MinecraftServer server,
            com.butchercraft.workstation.endpoint.WorkstationInstanceId instanceId
    ) {
        return runtime(server).stream()
                .flatMap(value -> value.service().journalSnapshot().records().stream())
                .filter(record -> record.preparation().observation().instanceId().equals(instanceId))
                .filter(record -> record.state() != WorkstationEndpointJournalState.RECONCILED
                        && record.state() != WorkstationEndpointJournalState.REJECTED
                        && record.state() != WorkstationEndpointJournalState.FAILED)
                .map(record -> record.effectId().value())
                .toList();
    }

    public synchronized boolean provesCommittedProjection(
            MinecraftServer server,
            com.butchercraft.workstation.endpoint.WorkstationInstanceId instanceId,
            long inventoryRevision,
            long endpointEffectRevision,
            long journalSequence,
            String ownerResultIdentity
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(ownerResultIdentity, "ownerResultIdentity");
        return runtime(server).stream()
                .flatMap(value -> value.service().journalSnapshot().records().stream())
                .anyMatch(record -> record.preparation().observation().instanceId().equals(instanceId)
                        && record.ownerResult().isPresent()
                        && record.ownerResult().orElseThrow().evidenceIdentity().equals(ownerResultIdentity)
                        && record.preparation().postInventoryRevision() == inventoryRevision
                        && record.preparation().postEndpointEffectRevision() == endpointEffectRevision
                        && record.preparation().journalSequence() == journalSequence
                        && (record.state() == WorkstationEndpointJournalState.EFFECT_COMMITTED
                        || record.state() == WorkstationEndpointJournalState.RESULT_PUBLISHED
                        || record.state() == WorkstationEndpointJournalState.RECONCILED));
    }

    private Resolved resolve(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return Resolved.failed(WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE,
                    "Workstation endpoint chunk is not loaded");
        }
        WorkstationEndpointReferenceResult referenceResult = instanceAuthority.referenceFor(level, position);
        if (!referenceResult.succeeded()) {
            return Resolved.failed(WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE, referenceResult.detail());
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof StackAwareWorkstationTransferEndpoint endpoint)
                || !(blockEntity instanceof AbstractInventoryWorkstationBlockEntity workstation)) {
            return Resolved.failed(WorkstationEndpointResultCode.UNSUPPORTED_EFFECT,
                    "Workstation does not implement the schema-2 endpoint contract");
        }
        var projection = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(level, workstation);
        if (!projection.succeeded()) {
            return Resolved.failed(
                    WorkstationEndpointResultCode.RECOVERY_REQUIRED,
                    "Workstation durable projection is unavailable: "
                            + projection.code() + ": " + projection.detail()
            );
        }
        endpoint.activateStackAwareEndpoint();
        WorkstationEndpointReference reference = referenceResult.reference().orElseThrow();
        if (!endpoint.endpointInstanceId().equals(reference.instanceId())
                || !endpoint.endpointKey().equals(reference.endpointKey())) {
            return Resolved.failed(WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Schema-2 endpoint projection does not match Workstation instance authority");
        }
        return Resolved.succeeded(endpoint);
    }

    private ActiveRuntime requireRuntime(MinecraftServer server) {
        return runtime(server).orElseThrow(() ->
                new IllegalStateException("Schema-2 Workstation endpoint runtime has not been activated"));
    }

    private Optional<ActiveRuntime> runtime(MinecraftServer server) {
        ActiveRuntime current = active.get();
        if (current != null && current.server() == server) return Optional.of(current);
        return loadExisting(server);
    }

    private Optional<ActiveRuntime> loadExisting(MinecraftServer server) {
        WorkstationEndpointJournalV2Storage storage = storage(server);
        Optional<WorkstationEndpointJournalV2Storage.LoadedJournal> loaded = storage.loadVersioned();
        if (loaded.isEmpty() || loaded.orElseThrow() instanceof WorkstationEndpointJournalV2Storage.LegacyJournal) {
            return Optional.empty();
        }
        WorkstationEndpointJournalV2 journal =
                ((WorkstationEndpointJournalV2Storage.StackAwareJournal) loaded.orElseThrow()).journal();
        if (!journal.endpointConfigurationIdentity().equals(configuration.stackAwareEndpointConfigurationIdentity())) {
            throw new IllegalStateException("Schema-2 Workstation endpoint configuration identity mismatch");
        }
        ActiveRuntime runtime = new ActiveRuntime(
                server, new StackAwareWorkstationEndpointService(journal, storage, stackCodec)
        );
        active.set(runtime);
        return Optional.of(runtime);
    }

    private static WorkstationEndpointJournalV2Storage storage(MinecraftServer server) {
        return new WorkstationEndpointJournalV2Storage(WorkstationEndpointService.journalFile(server));
    }

    private record ActiveRuntime(MinecraftServer server, StackAwareWorkstationEndpointService service) {}

    private record Resolved(
            Optional<StackAwareWorkstationTransferEndpoint> endpoint,
            Optional<StackAwareEndpointObservationResult> failure
    ) {
        private static Resolved succeeded(StackAwareWorkstationTransferEndpoint endpoint) {
            return new Resolved(Optional.of(endpoint), Optional.empty());
        }

        private static Resolved failed(WorkstationEndpointResultCode code, String detail) {
            return new Resolved(Optional.empty(), Optional.of(StackAwareEndpointObservationResult.failed(code, detail)));
        }
    }
}
