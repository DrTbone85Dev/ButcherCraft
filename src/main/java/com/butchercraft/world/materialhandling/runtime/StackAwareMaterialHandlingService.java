package com.butchercraft.world.materialhandling.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
import com.butchercraft.workstation.endpoint.runtime.StackAwareEndpointEffectResult;
import com.butchercraft.workstation.endpoint.runtime.StackAwareEndpointObservationResult;
import com.butchercraft.workstation.endpoint.runtime.StackAwareEndpointPreparationResult;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationEndpointRuntimeService;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointCancellationResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.world.materialhandling.MaterialHandlingConfiguration;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntimeV2;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.materialhandling.MaterialTransferIdV2;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecordV2;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Material Handling's live schema-2 custody boundary. */
final class StackAwareMaterialHandlingService {
    private final WorkstationEndpointService instanceAuthority;
    private final StackAwareWorkstationEndpointRuntimeService endpointAuthority;
    private final MaterialHandlingConfiguration configuration;
    private final ExactItemStackCodec stackCodec;
    private final AtomicReference<ActiveRuntime> active = new AtomicReference<>();

    StackAwareMaterialHandlingService(
            WorkstationEndpointService instanceAuthority,
            StackAwareWorkstationEndpointRuntimeService endpointAuthority,
            MaterialHandlingConfiguration configuration,
            ExactItemStackCodec stackCodec
    ) {
        this.instanceAuthority = Objects.requireNonNull(instanceAuthority, "instanceAuthority");
        this.endpointAuthority = Objects.requireNonNull(endpointAuthority, "endpointAuthority");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.stackCodec = Objects.requireNonNull(stackCodec, "stackCodec");
    }

    synchronized void activate(MinecraftServer server, MaterialHandlingRuntimeV2 candidate) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(candidate, "candidate");
        Optional<ActiveRuntime> existing = runtime(server);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().runtime().equals(candidate)) {
                throw new IllegalStateException("Schema-2 Material Handling is already active with different state");
            }
            return;
        }
        if (!candidate.configurationIdentity().equals(configuration.stackAwareConfigurationIdentity())) {
            throw new IllegalArgumentException("Schema-2 Material Handling configuration identity mismatch");
        }
        MaterialHandlingStorageV2 storage = storage(server);
        storage.save(candidate);
        active.set(new ActiveRuntime(server, storage, candidate));
    }

    synchronized boolean activeFor(MinecraftServer server) {
        return runtime(server).isPresent();
    }

    synchronized void stop(MinecraftServer server) {
        ActiveRuntime current = active.get();
        if (current != null && current.server() == server) active.compareAndSet(current, null);
    }

    synchronized Optional<MaterialHandlingRuntimeV2> currentRuntime(MinecraftServer server) {
        return runtime(server).map(ActiveRuntime::runtime);
    }

    synchronized Optional<MaterialTransferRecordV2> findTransfer(
            MinecraftServer server,
            MaterialTransferId transferId
    ) {
        return requireRuntime(server).runtime().find(transferId.value());
    }

    synchronized MaterialHandlingTransferResult requestTransfer(
            ServerLevel level,
            BlockPos sourcePosition,
            BlockPos destinationPosition,
            String assignmentType,
            Optional<String> employeeReference,
            MaterialHandlingService.SupportedRoute route
    ) {
        if (!level.hasChunkAt(sourcePosition) || !level.hasChunkAt(destinationPosition)) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Transfer endpoint chunk is not loaded");
        }
        WorkstationEndpointReferenceResult sourceResult = instanceAuthority.referenceFor(level, sourcePosition);
        WorkstationEndpointReferenceResult destinationResult = instanceAuthority.referenceFor(level, destinationPosition);
        if (!sourceResult.succeeded()) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Source endpoint rejected: "
                    + sourceResult.detail());
        }
        if (!destinationResult.succeeded()) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Destination endpoint rejected: "
                    + destinationResult.detail());
        }
        WorkstationEndpointReference source = sourceResult.reference().orElseThrow();
        WorkstationEndpointReference destination = destinationResult.reference().orElseThrow();
        if (!source.endpointKey().workstationTypeIdentity().equals(route.sourceTypeIdentity())
                || !destination.endpointKey().workstationTypeIdentity().equals(route.destinationTypeIdentity())
                || !source.endpointKey().dimensionIdentity().equals(destination.endpointKey().dimensionIdentity())) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Transfer endpoints do not match route");
        }
        StackAwareEndpointObservationResult observed = endpointAuthority.observeWithdrawal(level, sourcePosition, 1);
        if (!observed.succeeded()) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), observed.detail());
        }
        WorkstationEndpointObservationV2 observation = observed.observation().orElseThrow();
        String itemIdentity = observation.transferStack().stack().orElseThrow().itemIdentity();
        if (!itemIdentity.equals(route.sourceItemIdentity())) {
            return MaterialHandlingTransferResult.failed(Optional.empty(),
                    "Source endpoint contains the wrong material for the approved route");
        }
        ActiveRuntime current = requireRuntime(level.getServer());
        MaterialHandlingRuntimeV2.AllocationCandidate allocation = current.runtime().request(
                source, destination, route.materialIdentity(), 1, assignmentType, employeeReference,
                observation.transferStack(), observation, configuration.maximumTransfers()
        );
        publish(current, allocation.runtime());
        return MaterialHandlingTransferResult.requested(allocation.transfer());
    }

    synchronized MaterialHandlingTransferResult resume(ServerLevel level, MaterialTransferId transferId) {
        MaterialTransferRecordV2 transfer = findTransfer(level.getServer(), transferId).orElse(null);
        if (transfer == null) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown schema-2 Material Transfer");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            return MaterialHandlingTransferResult.succeeded(transfer);
        }
        if (!transfer.hasProvenMaterialHandlingCustody()) {
            MaterialHandlingTransferResult withdrawn = withdrawToCustody(level, transferId);
            if (!withdrawn.succeeded()) return withdrawn;
        }
        return depositFromCustody(level, transferId);
    }

    synchronized MaterialHandlingTransferResult withdrawToCustody(
            ServerLevel level,
            MaterialTransferId transferId
    ) {
        MaterialTransferRecordV2 transfer = findTransfer(level.getServer(), transferId).orElse(null);
        if (transfer == null) return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown transfer");
        if (transfer.hasProvenMaterialHandlingCustody()) {
            return MaterialHandlingTransferResult.custodyAccepted(transfer);
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            return MaterialHandlingTransferResult.succeeded(transfer);
        }
        if (transfer.lifecycle() != MaterialTransferLifecycle.REQUESTED
                && transfer.lifecycle() != MaterialTransferLifecycle.SOURCE_BOUND
                && transfer.lifecycle() != MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer),
                    "Transfer is not eligible for source withdrawal");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.REQUESTED) {
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.SOURCE_BOUND,
                    Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                    transfer.endpointOwnerResults(), Optional.empty());
        }
        WorkstationEndpointObservationV2 observation = evidence(
                transfer.endpointObservations(), WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
        ).orElseThrow();
        WorkstationEndpointPreparationV2 preparation;
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED) {
            preparation = preparation(transfer, WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL).orElseThrow();
        } else {
            StackAwareEndpointPreparationResult prepared = endpointAuthority.prepare(
                    level, position(transfer.source()), invocation(transfer, "source"), observation
            );
            if (!prepared.succeeded()) return failBeforeCustody(level.getServer(), transfer,
                    prepared.code(), prepared.detail());
            preparation = prepared.preparation().orElseThrow();
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                    Optional.empty(), transfer.endpointObservations(), append(transfer.endpointPreparations(), preparation),
                    transfer.endpointOwnerResults(), Optional.empty());
        }
        StackAwareEndpointEffectResult committed = endpointAuthority.commit(
                level, position(transfer.source()), preparation
        );
        if (!committed.succeeded()) return failBeforeCustody(level.getServer(), transfer,
                committed.code(), committed.detail());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                Optional.of(transfer.exactTransferStack()), transfer.endpointObservations(),
                transfer.endpointPreparations(), append(transfer.endpointOwnerResults(), committed.ownerResult().orElseThrow()),
                Optional.empty());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.IN_TRANSIT,
                transfer.exactInTransitCustody(), transfer.endpointObservations(), transfer.endpointPreparations(),
                transfer.endpointOwnerResults(), Optional.empty());
        return MaterialHandlingTransferResult.custodyAccepted(transfer);
    }

    synchronized MaterialHandlingTransferResult depositFromCustody(
            ServerLevel level,
            MaterialTransferId transferId
    ) {
        MaterialTransferRecordV2 transfer = findTransfer(level.getServer(), transferId).orElse(null);
        if (transfer == null) return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown transfer");
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            return MaterialHandlingTransferResult.succeeded(transfer);
        }
        if (!transfer.hasProvenMaterialHandlingCustody()) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer),
                    "Destination deposit requires proven Material Handling custody");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.IN_TRANSIT) {
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.DESTINATION_BOUND,
                    transfer.exactInTransitCustody(), transfer.endpointObservations(), transfer.endpointPreparations(),
                    transfer.endpointOwnerResults(), Optional.empty());
        }
        ItemStack payload = stackCodec.decodeState(level.registryAccess(), transfer.exactTransferStack());
        WorkstationEndpointObservationV2 observation;
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED) {
            observation = evidence(transfer.endpointObservations(), WorkstationEndpointEffectKind.DESTINATION_DEPOSIT)
                    .orElseThrow();
        } else {
            StackAwareEndpointObservationResult observed = endpointAuthority.observeDeposit(
                    level, position(transfer.destination()), payload
            );
            if (!observed.succeeded()) return custodyFailure(level.getServer(), transfer,
                    observed.code(), observed.detail());
            observation = observed.observation().orElseThrow();
            transfer = transition(level.getServer(), transfer, transfer.lifecycle(), transfer.exactInTransitCustody(),
                    append(transfer.endpointObservations(), observation), transfer.endpointPreparations(),
                    transfer.endpointOwnerResults(), Optional.empty());
        }
        WorkstationEndpointPreparationV2 preparation;
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED) {
            preparation = preparation(transfer, WorkstationEndpointEffectKind.DESTINATION_DEPOSIT).orElseThrow();
        } else {
            StackAwareEndpointPreparationResult prepared = endpointAuthority.prepare(
                    level, position(transfer.destination()), invocation(transfer, "destination"), observation
            );
            if (!prepared.succeeded()) return custodyFailure(level.getServer(), transfer,
                    prepared.code(), prepared.detail());
            preparation = prepared.preparation().orElseThrow();
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED,
                    transfer.exactInTransitCustody(), transfer.endpointObservations(),
                    append(transfer.endpointPreparations(), preparation), transfer.endpointOwnerResults(), Optional.empty());
        }
        StackAwareEndpointEffectResult committed = endpointAuthority.commit(
                level, position(transfer.destination()), preparation
        );
        if (!committed.succeeded()) return custodyFailure(level.getServer(), transfer,
                committed.code(), committed.detail());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED,
                Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                append(transfer.endpointOwnerResults(), committed.ownerResult().orElseThrow()), Optional.empty());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.COMPLETED,
                Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                transfer.endpointOwnerResults(), Optional.empty());
        return MaterialHandlingTransferResult.succeeded(transfer);
    }

    synchronized MaterialHandlingTransferResult cancel(
            ServerLevel level,
            MaterialTransferId transferId,
            String reason
    ) {
        MaterialTransferRecordV2 transfer = findTransfer(level.getServer(), transferId).orElse(null);
        if (transfer == null) return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown transfer");
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLED) {
            return MaterialHandlingTransferResult.cancelled(transfer);
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED
                || transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer),
                    "Terminal transfer cannot be cancelled");
        }
        if (!transfer.hasProvenMaterialHandlingCustody()) {
            Optional<WorkstationEndpointPreparationV2> sourcePreparation = preparation(
                    transfer, WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
            );
            if (sourcePreparation.isPresent()) {
                WorkstationEndpointCancellationResult cancelled = endpointAuthority.cancelPrepared(
                        level, position(transfer.source()), sourcePreparation.orElseThrow()
                );
                if (!cancelled.succeeded()) {
                    StackAwareEndpointEffectResult observed = endpointAuthority.observeCommittedResult(
                            level, position(transfer.source()), sourcePreparation.orElseThrow()
                    );
                    if (observed.succeeded()) {
                        transfer = transition(level.getServer(), transfer,
                                MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                                Optional.of(transfer.exactTransferStack()), transfer.endpointObservations(),
                                transfer.endpointPreparations(), append(transfer.endpointOwnerResults(),
                                        observed.ownerResult().orElseThrow()), Optional.empty());
                        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.IN_TRANSIT,
                                transfer.exactInTransitCustody(), transfer.endpointObservations(),
                                transfer.endpointPreparations(), transfer.endpointOwnerResults(), Optional.empty());
                    } else {
                        return failBeforeCustody(level.getServer(), transfer, cancelled.code(), cancelled.detail());
                    }
                }
            }
            if (!transfer.hasProvenMaterialHandlingCustody()) {
                transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.CANCELLED,
                        Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                        transfer.endpointOwnerResults(), Optional.of(reason));
                return MaterialHandlingTransferResult.cancelled(transfer);
            }
        }
        Optional<WorkstationEndpointPreparationV2> destinationPreparation = preparation(
                transfer, WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
        );
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED
                && destinationPreparation.isPresent()) {
            WorkstationEndpointCancellationResult cancelled = endpointAuthority.cancelPrepared(
                    level, position(transfer.destination()), destinationPreparation.orElseThrow()
            );
            if (!cancelled.succeeded()) {
                return custodyFailure(level.getServer(), transfer, cancelled.code(), cancelled.detail());
            }
        }
        if (transfer.lifecycle() != MaterialTransferLifecycle.CANCELLATION_REQUESTED
                && transfer.lifecycle() != MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED) {
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.CANCELLATION_REQUESTED,
                    transfer.exactInTransitCustody(), transfer.endpointObservations(), transfer.endpointPreparations(),
                    transfer.endpointOwnerResults(), Optional.of(reason));
        }
        ItemStack payload = stackCodec.decodeState(level.registryAccess(), transfer.exactTransferStack());
        WorkstationEndpointObservationV2 observation;
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED) {
            observation = evidence(transfer.endpointObservations(), WorkstationEndpointEffectKind.SOURCE_RETURN)
                    .orElseThrow();
        } else {
            StackAwareEndpointObservationResult observed = endpointAuthority.observeReturn(
                    level, position(transfer.source()), payload
            );
            if (!observed.succeeded()) return custodyFailure(level.getServer(), transfer,
                    observed.code(), observed.detail());
            observation = observed.observation().orElseThrow();
            transfer = transition(level.getServer(), transfer, transfer.lifecycle(), transfer.exactInTransitCustody(),
                    append(transfer.endpointObservations(), observation), transfer.endpointPreparations(),
                    transfer.endpointOwnerResults(), transfer.failureDetail());
        }
        WorkstationEndpointPreparationV2 returnPreparation;
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED) {
            returnPreparation = preparation(transfer, WorkstationEndpointEffectKind.SOURCE_RETURN).orElseThrow();
        } else {
            StackAwareEndpointPreparationResult prepared = endpointAuthority.prepare(
                    level, position(transfer.source()), invocation(transfer, "return"), observation
            );
            if (!prepared.succeeded()) return custodyFailure(level.getServer(), transfer,
                    prepared.code(), prepared.detail());
            returnPreparation = prepared.preparation().orElseThrow();
            transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED,
                    transfer.exactInTransitCustody(), transfer.endpointObservations(),
                    append(transfer.endpointPreparations(), returnPreparation), transfer.endpointOwnerResults(),
                    transfer.failureDetail());
        }
        StackAwareEndpointEffectResult committed = endpointAuthority.commit(
                level, position(transfer.source()), returnPreparation
        );
        if (!committed.succeeded()) return custodyFailure(level.getServer(), transfer,
                committed.code(), committed.detail());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                append(transfer.endpointOwnerResults(), committed.ownerResult().orElseThrow()), transfer.failureDetail());
        transfer = transition(level.getServer(), transfer, MaterialTransferLifecycle.CANCELLED,
                Optional.empty(), transfer.endpointObservations(), transfer.endpointPreparations(),
                transfer.endpointOwnerResults(), transfer.failureDetail());
        return MaterialHandlingTransferResult.cancelled(transfer);
    }

    synchronized Optional<ItemStack> carryDisplayStack(MinecraftServer server, MaterialTransferId transferId) {
        MaterialTransferRecordV2 transfer = findTransfer(server, transferId).orElse(null);
        if (transfer == null || !transfer.hasProvenMaterialHandlingCustody()) return Optional.empty();
        ItemStack display = stackCodec.decodeState(server.registryAccess(), transfer.exactTransferStack()).copy();
        display.setCount(1);
        return Optional.of(display);
    }

    synchronized void reconcile(MinecraftServer server) {
        ActiveRuntime runtime = requireRuntime(server);
        int count = 0;
        for (MaterialTransferRecordV2 transfer : List.copyOf(runtime.runtime().transfers())) {
            if (count >= configuration.maximumReconciliationsPerStartup()) break;
            ServerLevel level = levelFor(server, transfer);
            if (level == null || !level.hasChunkAt(position(transfer.source()))
                    || !level.hasChunkAt(position(transfer.destination()))) continue;
            if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED) {
                transition(server, transfer, MaterialTransferLifecycle.IN_TRANSIT, transfer.exactInTransitCustody(),
                        transfer.endpointObservations(), transfer.endpointPreparations(),
                        transfer.endpointOwnerResults(), transfer.failureDetail());
                count++;
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED) {
                transition(server, transfer, MaterialTransferLifecycle.COMPLETED, Optional.empty(),
                        transfer.endpointObservations(), transfer.endpointPreparations(),
                        transfer.endpointOwnerResults(), transfer.failureDetail());
                count++;
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
                transition(server, transfer, MaterialTransferLifecycle.CANCELLED, Optional.empty(),
                        transfer.endpointObservations(), transfer.endpointPreparations(),
                        transfer.endpointOwnerResults(), transfer.failureDetail());
                count++;
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED
                    || transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED
                    || transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED) {
                reconcilePrepared(level, transfer);
                count++;
            }
        }
    }

    private void reconcilePrepared(ServerLevel level, MaterialTransferRecordV2 transfer) {
        WorkstationEndpointEffectKind kind = switch (transfer.lifecycle()) {
            case SOURCE_WITHDRAW_PREPARED -> WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL;
            case DESTINATION_DEPOSIT_PREPARED -> WorkstationEndpointEffectKind.DESTINATION_DEPOSIT;
            case CANCELLATION_RETURN_PREPARED -> WorkstationEndpointEffectKind.SOURCE_RETURN;
            default -> throw new IllegalArgumentException("Transfer is not prepared");
        };
        WorkstationEndpointPreparationV2 preparation = preparation(transfer, kind).orElseThrow();
        BlockPos endpoint = kind == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                ? position(transfer.destination()) : position(transfer.source());
        StackAwareEndpointEffectResult result = endpointAuthority.observeCommittedResult(level, endpoint, preparation);
        if (!result.succeeded()) return;
        MaterialTransferLifecycle committed = switch (kind) {
            case SOURCE_WITHDRAWAL -> MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED;
            case DESTINATION_DEPOSIT -> MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED;
            case SOURCE_RETURN -> MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED;
        };
        Optional<WorkstationEndpointStackStateV2> custody = kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? Optional.of(transfer.exactTransferStack()) : Optional.empty();
        MaterialTransferRecordV2 updated = transition(level.getServer(), transfer, committed, custody,
                transfer.endpointObservations(), transfer.endpointPreparations(),
                append(transfer.endpointOwnerResults(), result.ownerResult().orElseThrow()), transfer.failureDetail());
        MaterialTransferLifecycle terminal = switch (kind) {
            case SOURCE_WITHDRAWAL -> MaterialTransferLifecycle.IN_TRANSIT;
            case DESTINATION_DEPOSIT -> MaterialTransferLifecycle.COMPLETED;
            case SOURCE_RETURN -> MaterialTransferLifecycle.CANCELLED;
        };
        transition(level.getServer(), updated, terminal, custody, updated.endpointObservations(),
                updated.endpointPreparations(), updated.endpointOwnerResults(), updated.failureDetail());
    }

    private MaterialHandlingTransferResult failBeforeCustody(
            MinecraftServer server,
            MaterialTransferRecordV2 transfer,
            WorkstationEndpointResultCode code,
            String detail
    ) {
        MaterialTransferLifecycle target = code == WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                ? MaterialTransferLifecycle.UNKNOWN_OUTCOME
                : code == WorkstationEndpointResultCode.RECOVERY_REQUIRED
                ? MaterialTransferLifecycle.RECOVERY_REQUIRED : MaterialTransferLifecycle.FAILED;
        MaterialTransferRecordV2 failed = transition(server, transfer, target, Optional.empty(),
                transfer.endpointObservations(), transfer.endpointPreparations(), transfer.endpointOwnerResults(),
                Optional.of(detail));
        return MaterialHandlingTransferResult.failed(Optional.of(failed), detail);
    }

    private MaterialHandlingTransferResult custodyFailure(
            MinecraftServer server,
            MaterialTransferRecordV2 transfer,
            WorkstationEndpointResultCode code,
            String detail
    ) {
        MaterialTransferLifecycle target = code == WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                ? MaterialTransferLifecycle.UNKNOWN_OUTCOME : MaterialTransferLifecycle.RECOVERY_REQUIRED;
        MaterialTransferRecordV2 failed = transition(server, transfer, target, transfer.exactInTransitCustody(),
                transfer.endpointObservations(), transfer.endpointPreparations(), transfer.endpointOwnerResults(),
                Optional.of(detail));
        return MaterialHandlingTransferResult.failed(Optional.of(failed), detail);
    }

    private MaterialTransferRecordV2 transition(
            MinecraftServer server,
            MaterialTransferRecordV2 transfer,
            MaterialTransferLifecycle lifecycle,
            Optional<WorkstationEndpointStackStateV2> custody,
            List<WorkstationEndpointObservationV2> observations,
            List<WorkstationEndpointPreparationV2> preparations,
            List<WorkstationEndpointOwnerResultV2> results,
            Optional<String> detail
    ) {
        ActiveRuntime runtime = requireRuntime(server);
        MaterialHandlingRuntimeV2 candidate = runtime.runtime().update(
                transfer.transferId(),
                (current, revision) -> current.transition(
                        lifecycle, revision, custody, observations, preparations, results, detail
                )
        );
        publish(runtime, candidate);
        return candidate.find(transfer.transferId()).orElseThrow();
    }

    private static <T> List<T> append(List<T> values, T value) {
        if (values.contains(value)) return values;
        List<T> candidate = new ArrayList<>(values);
        candidate.add(value);
        return List.copyOf(candidate);
    }

    private static Optional<WorkstationEndpointObservationV2> evidence(
            List<WorkstationEndpointObservationV2> observations,
            WorkstationEndpointEffectKind kind
    ) {
        return observations.stream().filter(value -> value.effectKind() == kind).reduce((first, second) -> second);
    }

    private static Optional<WorkstationEndpointPreparationV2> preparation(
            MaterialTransferRecordV2 transfer,
            WorkstationEndpointEffectKind kind
    ) {
        return transfer.endpointPreparations().stream()
                .filter(value -> value.observation().effectKind() == kind)
                .reduce((first, second) -> second);
    }

    private static String invocation(MaterialTransferRecordV2 transfer, String phase) {
        return "butchercraft:material_handling_invocation/v2/"
                + transfer.transferId().value().substring(transfer.transferId().value().lastIndexOf('/') + 1)
                + "/" + phase;
    }

    private Optional<ActiveRuntime> runtime(MinecraftServer server) {
        ActiveRuntime current = active.get();
        if (current != null && current.server() == server) return Optional.of(current);
        MaterialHandlingStorageV2 storage = storage(server);
        Optional<MaterialHandlingStorageV2.LoadedRuntime> loaded = storage.loadVersioned();
        if (loaded.isEmpty() || loaded.orElseThrow() instanceof MaterialHandlingStorageV2.LegacyRuntime) {
            return Optional.empty();
        }
        MaterialHandlingRuntimeV2 runtime =
                ((MaterialHandlingStorageV2.StackAwareRuntime) loaded.orElseThrow()).runtime();
        if (!runtime.configurationIdentity().equals(configuration.stackAwareConfigurationIdentity())) {
            throw new IllegalStateException("Schema-2 Material Handling configuration identity mismatch");
        }
        ActiveRuntime activeRuntime = new ActiveRuntime(server, storage, runtime);
        active.set(activeRuntime);
        return Optional.of(activeRuntime);
    }

    private ActiveRuntime requireRuntime(MinecraftServer server) {
        return runtime(server).orElseThrow(() ->
                new IllegalStateException("Schema-2 Material Handling has not been activated"));
    }

    private void publish(ActiveRuntime runtime, MaterialHandlingRuntimeV2 candidate) {
        runtime.storage().save(candidate);
        active.set(new ActiveRuntime(runtime.server(), runtime.storage(), candidate));
    }

    private static MaterialHandlingStorageV2 storage(MinecraftServer server) {
        return new MaterialHandlingStorageV2(MaterialHandlingService.stateFile(server));
    }

    private static BlockPos position(WorkstationEndpointReference reference) {
        return new BlockPos(reference.endpointKey().x(), reference.endpointKey().y(), reference.endpointKey().z());
    }

    private static ServerLevel levelFor(MinecraftServer server, MaterialTransferRecordV2 transfer) {
        String dimensionIdentity = transfer.source().endpointKey().dimensionIdentity();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionIdentity)) {
                return level;
            }
        }
        return null;
    }

    private record ActiveRuntime(
            MinecraftServer server,
            MaterialHandlingStorageV2 storage,
            MaterialHandlingRuntimeV2 runtime
    ) {}
}
