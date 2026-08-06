package com.butchercraft.world.materialhandling.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparation;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointEffectResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointCancellationResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointObservationResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointPreparationResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialHandlingConfiguration;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntime;
import com.butchercraft.world.materialhandling.MaterialHandlingSchema;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecord;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class MaterialHandlingService {
    public static final MaterialHandlingService INSTANCE = new MaterialHandlingService(
            WorldIdentityService.INSTANCE,
            WorkstationEndpointService.INSTANCE,
            MaterialHandlingConfiguration.standard()
    );

    private static final String CUTTING_TABLE_TYPE = "butchercraft:cutting_table";
    private static final String GRINDER_TYPE = "butchercraft:grinder";
    private static final String BEEF_TRIM_MATERIAL = "butchercraft:beef_trim";
    private static final String NON_EMPLOYEE_ASSIGNMENT = "butchercraft:assignment/non_employee_integration";

    private final WorldIdentityService worldIdentityService;
    private final WorkstationEndpointService endpointService;
    private final MaterialHandlingConfiguration configuration;
    private final ExactItemStackCodec stackCodec = new ExactItemStackCodec();
    private final AtomicReference<ActiveMaterialHandling> active = new AtomicReference<>();

    MaterialHandlingService(
            WorldIdentityService worldIdentityService,
            WorkstationEndpointService endpointService,
            MaterialHandlingConfiguration configuration
    ) {
        this.worldIdentityService = Objects.requireNonNull(worldIdentityService, "worldIdentityService");
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public void initialize(ServerStartedEvent event) {
        ActiveMaterialHandling runtime = load(event.getServer());
        int reconciled = 0;
        for (MaterialTransferRecord transfer : runtime.runtime().transfers()) {
            if (reconciled >= configuration.maximumReconciliationsPerStartup()) break;
            if (!transfer.lifecycle().terminal()
                    || transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
                ServerLevel level = loadedLevelFor(event.getServer(), transfer);
                if (level != null && endpointsLoaded(level, transfer)) {
                    if (cancellationAccepted(transfer.lifecycle())) {
                        cancel(
                                level,
                                transfer.transferId(),
                                transfer.terminalDetail().orElse("accepted cancellation resumed during startup")
                        );
                        reconciled++;
                    } else if (reconcileProven(level, transfer)) {
                        reconciled++;
                    }
                }
            }
        }
    }

    public void stop(ServerStoppingEvent event) {
        ActiveMaterialHandling current = active.get();
        if (current != null && current.server() == event.getServer()) active.compareAndSet(current, null);
    }

    public synchronized MaterialHandlingTransferResult requestExplicitTransfer(
            ServerLevel level,
            BlockPos sourcePosition,
            BlockPos destinationPosition
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(destinationPosition, "destinationPosition");
        if (!level.hasChunkAt(sourcePosition) || !level.hasChunkAt(destinationPosition)) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Transfer endpoint chunk is not loaded");
        }
        WorkstationEndpointReferenceResult sourceResult = endpointService.referenceFor(level, sourcePosition);
        if (!sourceResult.succeeded()) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Source endpoint rejected: " + sourceResult.detail());
        }
        WorkstationEndpointReferenceResult destinationResult = endpointService.referenceFor(level, destinationPosition);
        if (!destinationResult.succeeded()) {
            return MaterialHandlingTransferResult.failed(
                    Optional.empty(),
                    "Destination endpoint rejected: " + destinationResult.detail()
            );
        }
        WorkstationEndpointReference source = sourceResult.reference().orElseThrow();
        WorkstationEndpointReference destination = destinationResult.reference().orElseThrow();
        if (!CUTTING_TABLE_TYPE.equals(source.endpointKey().workstationTypeIdentity())
                || !GRINDER_TYPE.equals(destination.endpointKey().workstationTypeIdentity())) {
            return MaterialHandlingTransferResult.failed(
                    Optional.empty(),
                    "IM-028A authorizes only Cutting Table to Grinder transfer"
            );
        }
        if (!source.endpointKey().dimensionIdentity().equals(destination.endpointKey().dimensionIdentity())) {
            return MaterialHandlingTransferResult.failed(Optional.empty(), "Transfer endpoints are in different dimensions");
        }
        ActiveMaterialHandling runtime = load(level.getServer());
        MaterialHandlingRuntime.AllocationCandidate allocation = runtime.runtime().request(
                source,
                destination,
                BEEF_TRIM_MATERIAL,
                1,
                NON_EMPLOYEE_ASSIGNMENT,
                configuration.maximumTransfers()
        );
        publish(runtime, allocation.runtime());
        return resume(level, allocation.transfer().transferId());
    }

    public synchronized MaterialHandlingTransferResult resume(ServerLevel level, MaterialTransferId transferId) {
        ActiveMaterialHandling runtime = load(level.getServer());
        MaterialTransferRecord transfer = runtime.runtime().find(transferId).orElse(null);
        if (transfer == null) return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown Material Transfer");
        if (!transfer.source().endpointKey().dimensionIdentity().equals(level.dimension().location().toString())) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer), "Material Transfer belongs to another dimension");
        }
        if (!endpointsLoaded(level, transfer)) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer), "Material Transfer endpoint chunk is not loaded");
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED) {
            return MaterialHandlingTransferResult.succeeded(transfer);
        }
        if (transfer.lifecycle().terminal()) {
            return MaterialHandlingTransferResult.failed(
                    Optional.of(transfer),
                    transfer.terminalDetail().orElse("Material Transfer is terminally blocked")
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED
                || transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_REQUESTED
                || transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                || transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
            return MaterialHandlingTransferResult.failed(
                    Optional.of(transfer),
                    "Material Transfer requires explicit cancellation or recovery handling: " + transfer.lifecycle()
            );
        }
        BlockPos sourcePosition = position(transfer.source());
        BlockPos destinationPosition = position(transfer.destination());

        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
            return finalizeCommittedCancellation(level, transfer);
        }

        if (transfer.lifecycle() == MaterialTransferLifecycle.REQUESTED) {
            WorkstationEndpointObservationResult observed = endpointService.observeWithdrawalOne(level, sourcePosition);
            if (!observed.succeeded()) {
                return failBeforeCustody(level.getServer(), transfer, observed.code(), observed.detail());
            }
            if (observed.observation().orElseThrow().exactEffectStack().decodedStack().length
                    > configuration.maximumCustodyPayloadBytes()) {
                return failBeforeCustody(
                        level.getServer(),
                        transfer,
                        WorkstationEndpointResultCode.SOURCE_MISMATCH,
                        "Exact source stack exceeds the configured Material Handling custody limit"
                );
            }
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.SOURCE_BOUND,
                    observed.observation(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_BOUND) {
            WorkstationEndpointStackPayload exact = transfer.sourceObservation().orElseThrow().exactEffectStack();
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                    transfer.sourceObservation(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(exact),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED
                && transfer.sourcePreparation().isEmpty()) {
            WorkstationEndpointPreparationResult prepared = endpointService.prepareObservedEffect(
                    level,
                    sourcePosition,
                    transfer.sourceInvocationIdentity(),
                    transfer.sourceObservation().orElseThrow()
            );
            if (!prepared.succeeded()) {
                return failBeforeCustody(level.getServer(), transfer, prepared.code(), prepared.detail());
            }
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED,
                    transfer.sourceObservation(),
                    prepared.preparation(),
                    Optional.empty(),
                    transfer.exactTransferStack(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_PREPARED) {
            WorkstationEndpointEffectResult committed = endpointService.commitPrepared(
                    level,
                    sourcePosition,
                    transfer.sourcePreparation().orElseThrow()
            );
            if (!committed.succeeded()) {
                return failBeforeCustody(level.getServer(), transfer, committed.code(), committed.detail());
            }
            WorkstationEndpointOwnerResult evidence = committed.ownerResult().orElseThrow();
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    Optional.of(evidence),
                    transfer.exactTransferStack(),
                    transfer.exactTransferStack(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED) {
            transfer = transitionRetaining(level.getServer(), transfer, MaterialTransferLifecycle.IN_TRANSIT);
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.IN_TRANSIT) {
            WorkstationEndpointObservationResult observed = endpointService.observeDepositOne(
                    level,
                    destinationPosition,
                    transfer.inTransitCustody().orElseThrow()
            );
            if (!observed.succeeded()) return requireCustodyRecovery(level.getServer(), transfer, observed.detail());
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.DESTINATION_BOUND,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    transfer.sourceResult(),
                    transfer.exactTransferStack(),
                    transfer.inTransitCustody(),
                    observed.observation(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_BOUND) {
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    transfer.sourceResult(),
                    transfer.exactTransferStack(),
                    transfer.inTransitCustody(),
                    transfer.destinationObservation(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED
                && transfer.destinationPreparation().isEmpty()) {
            WorkstationEndpointPreparationResult prepared = endpointService.prepareObservedEffect(
                    level,
                    destinationPosition,
                    transfer.destinationInvocationIdentity(),
                    transfer.destinationObservation().orElseThrow()
            );
            if (!prepared.succeeded()) return requireCustodyRecovery(level.getServer(), transfer, prepared.detail());
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    transfer.sourceResult(),
                    transfer.exactTransferStack(),
                    transfer.inTransitCustody(),
                    transfer.destinationObservation(),
                    prepared.preparation(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_PREPARED) {
            WorkstationEndpointEffectResult committed = endpointService.commitPrepared(
                    level,
                    destinationPosition,
                    transfer.destinationPreparation().orElseThrow()
            );
            if (!committed.succeeded()) {
                if (committed.code() == WorkstationEndpointResultCode.UNKNOWN_OUTCOME) {
                    return markTerminal(
                            level.getServer(),
                            transfer,
                            MaterialTransferLifecycle.UNKNOWN_OUTCOME,
                            committed.detail()
                    );
                }
                return requireCustodyRecovery(level.getServer(), transfer, committed.detail());
            }
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    transfer.sourceResult(),
                    transfer.exactTransferStack(),
                    Optional.empty(),
                    transfer.destinationObservation(),
                    transfer.destinationPreparation(),
                    committed.ownerResult(),
                    Optional.empty()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED) {
            transfer = transitionRetaining(level.getServer(), transfer, MaterialTransferLifecycle.COMPLETED);
        }
        return transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED
                ? MaterialHandlingTransferResult.succeeded(transfer)
                : MaterialHandlingTransferResult.failed(Optional.of(transfer), "Material Transfer did not complete");
    }

    public synchronized MaterialHandlingTransferResult cancel(
            ServerLevel level,
            MaterialTransferId transferId,
            String reason
    ) {
        Objects.requireNonNull(level, "level");
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("Cancellation reason must not be blank");
        MaterialTransferRecord transfer = load(level.getServer()).runtime().find(transferId).orElse(null);
        if (transfer == null) return MaterialHandlingTransferResult.failed(Optional.empty(), "Unknown Material Transfer");
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLED) {
            return MaterialHandlingTransferResult.cancelled(transfer);
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME
                || transfer.lifecycle().terminal()
                || transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED) {
            return MaterialHandlingTransferResult.failed(
                    Optional.of(transfer),
                    "Material Transfer cannot be cancelled from " + transfer.lifecycle()
            );
        }
        if (!endpointsLoaded(level, transfer)) {
            return MaterialHandlingTransferResult.failed(Optional.of(transfer), "Material Transfer endpoint chunk is not loaded");
        }
        BlockPos sourcePosition = position(transfer.source());
        BlockPos destinationPosition = position(transfer.destination());

        if (transfer.custodyLocation().filter(MaterialCustodyLocation.SOURCE_WORKSTATION::equals).isPresent()
                && transfer.sourceResult().isEmpty()) {
            if (transfer.sourcePreparation().isPresent()) {
                WorkstationEndpointEffectResult observed = endpointService.observeCommittedResult(
                        level,
                        sourcePosition,
                        transfer.sourcePreparation().orElseThrow()
                );
                if (observed.succeeded()) {
                    transfer = transition(
                            level.getServer(),
                            transferId,
                            MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                            transfer.sourceObservation(),
                            transfer.sourcePreparation(),
                            observed.ownerResult(),
                            transfer.exactTransferStack(),
                            transfer.exactTransferStack(),
                            transfer.destinationObservation(),
                            transfer.destinationPreparation(),
                            transfer.destinationResult(),
                            transfer.terminalDetail()
                    );
                } else if (observed.code() == WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE) {
                    WorkstationEndpointCancellationResult cancelled = endpointService.cancelPrepared(
                            level,
                            sourcePosition,
                            transfer.sourcePreparation().orElseThrow()
                    );
                    if (!cancelled.succeeded()) {
                        return cancellationFailure(level.getServer(), transfer, cancelled.code(), cancelled.detail());
                    }
                } else {
                    return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
                }
            }
            if (transfer.sourceResult().isEmpty()) {
                WorkstationEndpointObservationResult observed = endpointService.observeWithdrawalOne(
                        level,
                        sourcePosition
                );
                if (!observed.succeeded()) {
                    return failBeforeCustody(level.getServer(), transfer, observed.code(), observed.detail());
                }
                WorkstationEndpointObservation currentSource = observed.observation().orElseThrow();
                if (transfer.sourceObservation().isPresent()
                        && !transfer.sourceObservation().orElseThrow().equals(currentSource)) {
                    return failBeforeCustody(
                            level.getServer(),
                            transfer,
                            WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT,
                            "Source changed before cancellation could prove non-application"
                    );
                }
                MaterialTransferRecord cancelled = transition(
                        level.getServer(),
                        transferId,
                        MaterialTransferLifecycle.CANCELLED,
                        Optional.of(currentSource),
                        transfer.sourcePreparation(),
                        Optional.empty(),
                        Optional.of(currentSource.exactEffectStack()),
                        Optional.empty(),
                        transfer.destinationObservation(),
                        transfer.destinationPreparation(),
                        transfer.destinationResult(),
                        Optional.of(reason)
                );
                return MaterialHandlingTransferResult.cancelled(cancelled);
            }
        }

        if (!transfer.hasProvenMaterialHandlingCustody()) {
            return MaterialHandlingTransferResult.failed(
                    Optional.of(transfer),
                    "Cancellation requires proven Material Handling custody"
            );
        }

        if (transfer.destinationPreparation().isPresent() && transfer.destinationResult().isEmpty()) {
            WorkstationEndpointEffectResult observed = endpointService.observeCommittedResult(
                    level,
                    destinationPosition,
                    transfer.destinationPreparation().orElseThrow()
            );
            if (observed.succeeded()) {
                MaterialTransferRecord committed = transition(
                        level.getServer(),
                        transferId,
                        MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED,
                        transfer.sourceObservation(),
                        transfer.sourcePreparation(),
                        transfer.sourceResult(),
                        transfer.exactTransferStack(),
                        Optional.empty(),
                        transfer.destinationObservation(),
                        transfer.destinationPreparation(),
                        observed.ownerResult(),
                        transfer.terminalDetail()
                );
                return MaterialHandlingTransferResult.succeeded(transitionRetaining(
                        level.getServer(),
                        committed,
                        MaterialTransferLifecycle.COMPLETED
                ));
            }
            if (observed.code() != WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE) {
                return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
            }
            WorkstationEndpointCancellationResult cancelled = endpointService.cancelPrepared(
                    level,
                    destinationPosition,
                    transfer.destinationPreparation().orElseThrow()
            );
            if (!cancelled.succeeded()) {
                return cancellationFailure(level.getServer(), transfer, cancelled.code(), cancelled.detail());
            }
        }

        if (transfer.lifecycle() != MaterialTransferLifecycle.CANCELLATION_REQUESTED
                && transfer.lifecycle() != MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                && transfer.lifecycle() != MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
            transfer = transition(
                    level.getServer(),
                    transferId,
                    MaterialTransferLifecycle.CANCELLATION_REQUESTED,
                    transfer.sourceObservation(),
                    transfer.sourcePreparation(),
                    transfer.sourceResult(),
                    transfer.exactTransferStack(),
                    transfer.inTransitCustody(),
                    transfer.destinationObservation(),
                    transfer.destinationPreparation(),
                    transfer.destinationResult(),
                    Optional.of(mergeDetail(reason, transfer.terminalDetail()))
            );
        }

        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_REQUESTED
                && transfer.returnPreparation().isPresent()) {
            WorkstationEndpointEffectResult observed = endpointService.observeCommittedResult(
                    level,
                    sourcePosition,
                    transfer.returnPreparation().orElseThrow()
            );
            if (observed.succeeded()) {
                transfer = transitionWithReturn(
                        level.getServer(),
                        transfer,
                        MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                        transfer.returnObservation(),
                        transfer.returnPreparation(),
                        observed.ownerResult(),
                        Optional.empty(),
                        transfer.terminalDetail()
                );
            } else if (observed.code() == WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE) {
                transfer = transitionWithReturn(
                        level.getServer(),
                        transfer,
                        MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED,
                        transfer.returnObservation(),
                        transfer.returnPreparation(),
                        Optional.empty(),
                        transfer.inTransitCustody(),
                        transfer.terminalDetail()
                );
            } else {
                return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
            }
        }

        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_REQUESTED) {
            WorkstationEndpointObservationResult observed = endpointService.observeReturnOne(
                    level,
                    sourcePosition,
                    transfer.inTransitCustody().orElseThrow()
            );
            if (!observed.succeeded()) {
                return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
            }
            transfer = transitionWithReturn(
                    level.getServer(),
                    transfer,
                    MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED,
                    observed.observation(),
                    Optional.empty(),
                    Optional.empty(),
                    transfer.inTransitCustody(),
                    transfer.terminalDetail()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                && transfer.returnPreparation().isEmpty()) {
            WorkstationEndpointPreparationResult prepared = endpointService.prepareObservedEffect(
                    level,
                    sourcePosition,
                    transfer.returnInvocationIdentity(),
                    transfer.returnObservation().orElseThrow()
            );
            if (!prepared.succeeded()) {
                return cancellationFailure(level.getServer(), transfer, prepared.code(), prepared.detail());
            }
            transfer = transitionWithReturn(
                    level.getServer(),
                    transfer,
                    MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED,
                    transfer.returnObservation(),
                    prepared.preparation(),
                    Optional.empty(),
                    transfer.inTransitCustody(),
                    transfer.terminalDetail()
            );
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED) {
            WorkstationEndpointEffectResult observed = endpointService.observeCommittedResult(
                    level,
                    sourcePosition,
                    transfer.returnPreparation().orElseThrow()
            );
            if (observed.succeeded()) {
                transfer = transitionWithReturn(
                        level.getServer(),
                        transfer,
                        MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                        transfer.returnObservation(),
                        transfer.returnPreparation(),
                        observed.ownerResult(),
                        Optional.empty(),
                        transfer.terminalDetail()
                );
            } else if (observed.code() == WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE) {
                WorkstationEndpointEffectResult committed = endpointService.commitPrepared(
                        level,
                        sourcePosition,
                        transfer.returnPreparation().orElseThrow()
                );
                if (!committed.succeeded()) {
                    return cancellationFailure(level.getServer(), transfer, committed.code(), committed.detail());
                }
                transfer = transitionWithReturn(
                        level.getServer(),
                        transfer,
                        MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                        transfer.returnObservation(),
                        transfer.returnPreparation(),
                        committed.ownerResult(),
                        Optional.empty(),
                        transfer.terminalDetail()
                );
            } else {
                return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
            }
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
            return finalizeCommittedCancellation(level, transfer);
        }
        return MaterialHandlingTransferResult.cancelled(transfer);
    }

    private MaterialHandlingTransferResult finalizeCommittedCancellation(
            ServerLevel level,
            MaterialTransferRecord transfer
    ) {
        WorkstationEndpointEffectResult observed = endpointService.observeCommittedResult(
                level,
                position(transfer.source()),
                transfer.returnPreparation().orElseThrow()
        );
        if (!observed.succeeded()) {
            return cancellationFailure(level.getServer(), transfer, observed.code(), observed.detail());
        }
        if (transfer.returnResult().filter(observed.ownerResult().orElseThrow()::equals).isEmpty()) {
            return cancellationFailure(
                    level.getServer(),
                    transfer,
                    WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                    "Observed source-return result does not match Material Handling evidence"
            );
        }
        MaterialTransferRecord cancelled = transitionRetaining(
                level.getServer(),
                transfer,
                MaterialTransferLifecycle.CANCELLED
        );
        return MaterialHandlingTransferResult.cancelled(cancelled);
    }

    private static boolean cancellationAccepted(MaterialTransferLifecycle lifecycle) {
        return lifecycle == MaterialTransferLifecycle.CANCELLATION_REQUESTED
                || lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_PREPARED
                || lifecycle == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED;
    }

    private static String mergeDetail(String reason, Optional<String> priorDetail) {
        return priorDetail.map(value -> reason + "; prior state: " + value).orElse(reason);
    }

    public Optional<MaterialHandlingRuntime> currentRuntime() {
        return Optional.ofNullable(active.get()).map(ActiveMaterialHandling::runtime);
    }

    public static Path stateFile(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                .resolve(MaterialHandlingSchema.DIRECTORY_NAME)
                .resolve(MaterialHandlingSchema.FILE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private boolean reconcileProven(ServerLevel level, MaterialTransferRecord transfer) {
        if (transfer.lifecycle() == MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED) {
            return finalizeCommittedCancellation(level, transfer).succeeded();
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED) {
            transitionRetaining(level.getServer(), transfer, MaterialTransferLifecycle.COMPLETED);
            return true;
        }
        if (transfer.lifecycle() == MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED) {
            transitionRetaining(level.getServer(), transfer, MaterialTransferLifecycle.IN_TRANSIT);
            return true;
        }
        if (transfer.returnPreparation().isPresent() && transfer.returnResult().isEmpty()) {
            WorkstationEndpointEffectResult result = endpointService.observeCommittedResult(
                    level,
                    position(transfer.source()),
                    transfer.returnPreparation().orElseThrow()
            );
            if (result.succeeded()) {
                MaterialTransferRecord committed = transitionWithReturn(
                        level.getServer(),
                        transfer,
                        MaterialTransferLifecycle.CANCELLATION_RETURN_COMMITTED,
                        transfer.returnObservation(),
                        transfer.returnPreparation(),
                        result.ownerResult(),
                        Optional.empty(),
                        transfer.terminalDetail()
                );
                transitionRetaining(level.getServer(), committed, MaterialTransferLifecycle.CANCELLED);
                return true;
            }
            return publishObservedRecovery(level.getServer(), transfer, result);
        }
        if (transfer.destinationPreparation().isPresent() && transfer.destinationResult().isEmpty()) {
            WorkstationEndpointEffectResult result = endpointService.observeCommittedResult(
                    level,
                    position(transfer.destination()),
                    transfer.destinationPreparation().orElseThrow()
            );
            if (result.succeeded()) {
                MaterialTransferRecord committed = transition(
                        level.getServer(),
                        transfer.transferId(),
                        MaterialTransferLifecycle.DESTINATION_DEPOSIT_COMMITTED,
                        transfer.sourceObservation(),
                        transfer.sourcePreparation(),
                        transfer.sourceResult(),
                        transfer.exactTransferStack(),
                        Optional.empty(),
                        transfer.destinationObservation(),
                        transfer.destinationPreparation(),
                        result.ownerResult(),
                        transfer.terminalDetail()
                );
                transitionRetaining(level.getServer(), committed, MaterialTransferLifecycle.COMPLETED);
                return true;
            }
            return publishObservedRecovery(level.getServer(), transfer, result);
        }
        if (transfer.sourcePreparation().isPresent() && transfer.sourceResult().isEmpty()) {
            WorkstationEndpointEffectResult result = endpointService.observeCommittedResult(
                    level,
                    position(transfer.source()),
                    transfer.sourcePreparation().orElseThrow()
            );
            if (result.succeeded()) {
                MaterialTransferRecord committed = transition(
                        level.getServer(),
                        transfer.transferId(),
                        MaterialTransferLifecycle.SOURCE_WITHDRAW_COMMITTED,
                        transfer.sourceObservation(),
                        transfer.sourcePreparation(),
                        result.ownerResult(),
                        transfer.exactTransferStack(),
                        transfer.exactTransferStack(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                );
                transitionRetaining(level.getServer(), committed, MaterialTransferLifecycle.IN_TRANSIT);
                return true;
            }
            return publishObservedRecovery(level.getServer(), transfer, result);
        }
        return false;
    }

    private boolean publishObservedRecovery(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            WorkstationEndpointEffectResult result
    ) {
        if (result.code() == WorkstationEndpointResultCode.ENDPOINT_UNAVAILABLE) return false;
        if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) return false;
        MaterialTransferLifecycle lifecycle = result.code() == WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                ? MaterialTransferLifecycle.UNKNOWN_OUTCOME
                : MaterialTransferLifecycle.RECOVERY_REQUIRED;
        markTerminal(server, transfer, lifecycle, result.detail());
        return true;
    }

    private MaterialHandlingTransferResult failBeforeCustody(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            WorkstationEndpointResultCode code,
            String detail
    ) {
        MaterialTransferLifecycle state = code == WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                ? MaterialTransferLifecycle.UNKNOWN_OUTCOME
                : code == WorkstationEndpointResultCode.RECOVERY_REQUIRED
                        ? MaterialTransferLifecycle.RECOVERY_REQUIRED
                        : MaterialTransferLifecycle.FAILED;
        return markTerminal(server, transfer, state, detail);
    }

    private MaterialHandlingTransferResult requireCustodyRecovery(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            String detail
    ) {
        return markTerminal(server, transfer, MaterialTransferLifecycle.RECOVERY_REQUIRED, detail);
    }

    private MaterialHandlingTransferResult cancellationFailure(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            WorkstationEndpointResultCode code,
            String detail
    ) {
        return markTerminal(
                server,
                transfer,
                code == WorkstationEndpointResultCode.UNKNOWN_OUTCOME
                        ? MaterialTransferLifecycle.UNKNOWN_OUTCOME
                        : MaterialTransferLifecycle.RECOVERY_REQUIRED,
                detail
        );
    }

    private MaterialHandlingTransferResult markTerminal(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            MaterialTransferLifecycle state,
            String detail
    ) {
        MaterialTransferRecord terminal = transition(
                server,
                transfer.transferId(),
                state,
                transfer.sourceObservation(),
                transfer.sourcePreparation(),
                transfer.sourceResult(),
                transfer.exactTransferStack(),
                transfer.inTransitCustody(),
                transfer.destinationObservation(),
                transfer.destinationPreparation(),
                transfer.destinationResult(),
                Optional.of(detail)
        );
        return MaterialHandlingTransferResult.failed(Optional.of(terminal), detail);
    }

    private MaterialTransferRecord transitionRetaining(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            MaterialTransferLifecycle lifecycle
    ) {
        return transition(
                server,
                transfer.transferId(),
                lifecycle,
                transfer.sourceObservation(),
                transfer.sourcePreparation(),
                transfer.sourceResult(),
                transfer.exactTransferStack(),
                transfer.inTransitCustody(),
                transfer.destinationObservation(),
                transfer.destinationPreparation(),
                transfer.destinationResult(),
                transfer.terminalDetail()
        );
    }

    private MaterialTransferRecord transitionWithReturn(
            MinecraftServer server,
            MaterialTransferRecord transfer,
            MaterialTransferLifecycle lifecycle,
            Optional<WorkstationEndpointObservation> returnObservation,
            Optional<WorkstationEndpointPreparation> returnPreparation,
            Optional<WorkstationEndpointOwnerResult> returnResult,
            Optional<WorkstationEndpointStackPayload> custody,
            Optional<String> terminalDetail
    ) {
        ActiveMaterialHandling runtime = load(server);
        MaterialHandlingRuntime candidate = runtime.runtime().update(
                transfer.transferId(),
                (record, revision) -> record.transition(
                        lifecycle,
                        revision,
                        custodyLocationFor(lifecycle, record),
                        record.sourceObservation(),
                        record.sourcePreparation(),
                        record.sourceResult(),
                        record.exactTransferStack(),
                        custody,
                        record.destinationObservation(),
                        record.destinationPreparation(),
                        record.destinationResult(),
                        returnObservation,
                        returnPreparation,
                        returnResult,
                        terminalDetail
                )
        );
        publish(runtime, candidate);
        return candidate.find(transfer.transferId()).orElseThrow();
    }

    private MaterialTransferRecord transition(
            MinecraftServer server,
            MaterialTransferId transferId,
            MaterialTransferLifecycle lifecycle,
            Optional<WorkstationEndpointObservation> sourceObservation,
            Optional<WorkstationEndpointPreparation> sourcePreparation,
            Optional<WorkstationEndpointOwnerResult> sourceResult,
            Optional<WorkstationEndpointStackPayload> exactStack,
            Optional<WorkstationEndpointStackPayload> custody,
            Optional<WorkstationEndpointObservation> destinationObservation,
            Optional<WorkstationEndpointPreparation> destinationPreparation,
            Optional<WorkstationEndpointOwnerResult> destinationResult,
            Optional<String> terminalDetail
    ) {
        ActiveMaterialHandling runtime = load(server);
        MaterialHandlingRuntime candidate = runtime.runtime().update(
                transferId,
                (record, revision) -> record.transition(
                        lifecycle,
                        revision,
                        custodyLocationFor(lifecycle, record),
                        sourceObservation,
                        sourcePreparation,
                        sourceResult,
                        exactStack,
                        custody,
                        destinationObservation,
                        destinationPreparation,
                        destinationResult,
                        terminalDetail
                )
        );
        publish(runtime, candidate);
        return candidate.find(transferId).orElseThrow();
    }

    private static Optional<MaterialCustodyLocation> custodyLocationFor(
            MaterialTransferLifecycle lifecycle,
            MaterialTransferRecord current
    ) {
        return switch (lifecycle) {
            case REQUESTED, SOURCE_BOUND, SOURCE_WITHDRAW_PREPARED,
                    CANCELLATION_RETURN_COMMITTED, CANCELLED, FAILED ->
                    Optional.of(MaterialCustodyLocation.SOURCE_WORKSTATION);
            case SOURCE_WITHDRAW_COMMITTED, IN_TRANSIT, DESTINATION_BOUND, DESTINATION_DEPOSIT_PREPARED ->
                    Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME);
            case CANCELLATION_REQUESTED, CANCELLATION_RETURN_PREPARED ->
                    Optional.of(MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME);
            case DESTINATION_DEPOSIT_COMMITTED, COMPLETED ->
                    Optional.of(MaterialCustodyLocation.DESTINATION_WORKSTATION);
            case UNKNOWN_OUTCOME -> Optional.empty();
            case RECOVERY_REQUIRED -> current.custodyLocation();
        };
    }

    private synchronized ActiveMaterialHandling load(MinecraftServer server) {
        ActiveMaterialHandling current = active.get();
        if (current != null && current.server() == server) return current;
        WorldIdentityRootIdentity worldIdentity = WorldIdentityRootIdentities.from(worldIdentityService.getOrCreate(server));
        MaterialHandlingStorage storage = new MaterialHandlingStorage(stateFile(server));
        MaterialHandlingRuntime runtime = storage.loadExisting().orElseGet(() -> MaterialHandlingRuntime.empty(
                worldIdentity,
                configuration.configurationIdentity()
        ));
        if (!runtime.worldIdentity().equals(worldIdentity)) {
            throw new IllegalStateException("Material Handling persistence references another World Identity");
        }
        if (!runtime.configurationIdentity().equals(configuration.configurationIdentity())) {
            throw new IllegalStateException("Material Handling configuration identity mismatch");
        }
        if (runtime.transfers().size() > configuration.maximumTransfers()) {
            throw new IllegalStateException("Material Handling persistence exceeds configured transfer capacity");
        }
        for (MaterialTransferRecord transfer : runtime.transfers()) {
            validatePayload(server, transfer.sourceObservation().map(WorkstationEndpointObservation::exactEffectStack));
            validatePayload(server, transfer.exactTransferStack());
            validatePayload(server, transfer.inTransitCustody());
            validatePayload(server, transfer.destinationObservation().map(
                    WorkstationEndpointObservation::exactEffectStack
            ));
            validatePayload(server, transfer.returnObservation().map(
                    WorkstationEndpointObservation::exactEffectStack
            ));
        }
        ActiveMaterialHandling loaded = new ActiveMaterialHandling(server, storage, runtime);
        active.set(loaded);
        return loaded;
    }

    private void validatePayload(MinecraftServer server, Optional<WorkstationEndpointStackPayload> payload) {
        if (payload.isPresent()
                && payload.orElseThrow().decodedStack().length > configuration.maximumCustodyPayloadBytes()) {
            throw new IllegalStateException("Material Handling payload exceeds configured bounded capacity");
        }
        payload.ifPresent(value -> stackCodec.decode(server.registryAccess(), value));
    }

    private void publish(ActiveMaterialHandling runtime, MaterialHandlingRuntime candidate) {
        runtime.storage().save(candidate);
        active.set(new ActiveMaterialHandling(runtime.server(), runtime.storage(), candidate));
    }

    private static ServerLevel loadedLevelFor(MinecraftServer server, MaterialTransferRecord transfer) {
        ResourceLocation location = ResourceLocation.tryParse(transfer.source().endpointKey().dimensionIdentity());
        if (location == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    private static boolean endpointsLoaded(ServerLevel level, MaterialTransferRecord transfer) {
        return level.hasChunkAt(position(transfer.source())) && level.hasChunkAt(position(transfer.destination()));
    }

    private static BlockPos position(WorkstationEndpointReference reference) {
        return new BlockPos(reference.endpointKey().x(), reference.endpointKey().y(), reference.endpointKey().z());
    }

    private record ActiveMaterialHandling(
            MinecraftServer server,
            MaterialHandlingStorage storage,
            MaterialHandlingRuntime runtime
    ) {
    }
}
