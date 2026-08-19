package com.butchercraft.integration.machine;

import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.operation.MachineEndpointAvailability;
import com.butchercraft.workstation.operation.MachineOperatingMutation;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingResultCode;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.operation.MachineWorkstationReference;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.MachineOperatingStateService;
import com.butchercraft.world.execution.ExecutionAuthorization;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationResult;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.MachineRunChildState;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.execution.MachineRunRegistryMutation;
import com.butchercraft.world.execution.MachineRunResultCode;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;
import com.butchercraft.world.simulation.SimulationClockService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Objects;
import java.util.Optional;

public final class MachineRunCoordinatorService {
    public static final MachineRunCoordinatorService INSTANCE = new MachineRunCoordinatorService(
            MachineOperatingStateService.INSTANCE,
            ExecutionMachineRunService.INSTANCE,
            ExecutionService.INSTANCE,
            WorkstationEndpointService.INSTANCE,
            SimulationClockService.INSTANCE
    );

    private final MachineOperatingStateService operatingService;
    private final ExecutionMachineRunService runService;
    private final ExecutionService executionService;
    private final WorkstationEndpointService endpointService;
    private final SimulationClockService clockService;

    public MachineRunCoordinatorService(
            MachineOperatingStateService operatingService,
            ExecutionMachineRunService runService,
            ExecutionService executionService,
            WorkstationEndpointService endpointService,
            SimulationClockService clockService
    ) {
        this.operatingService = Objects.requireNonNull(operatingService, "operatingService");
        this.runService = Objects.requireNonNull(runService, "runService");
        this.executionService = Objects.requireNonNull(executionService, "executionService");
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
        this.clockService = Objects.requireNonNull(clockService, "clockService");
    }

    public void initialize(ServerStartedEvent event) {
        reconcileForRestart(event.getServer(), clockService.clock(event.getServer()).simulationTick());
    }

    public synchronized MachineRunCoordinationResult start(
            MinecraftServer server,
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            long tick
    ) {
        Optional<MachineRunRecord> prior = runService.snapshot(server)
                .startForRequest(sourceOwner, sourceRequestIdentity);
        if (prior.isPresent()) {
            MachineRunRecord existing = prior.orElseThrow();
            if (existing.workstationInstanceIdentity().equals(workstation.instanceId().value())
                    && existing.startEvidence().expectedOperatingRevision() == expectedOperatingRevision
                    && existing.operatingPolicyIdentity().equals(policy.policyIdentity())) {
                return result(
                        MachineRunCoordinationCode.EXISTING_RESULT,
                        server,
                        Optional.of(existing),
                        Optional.empty(),
                        "Existing Machine START observed"
                );
            }
            return result(MachineRunCoordinationCode.EXECUTION_REJECTED, server, Optional.empty(), Optional.empty(),
                    "Machine START request identity has conflicting canonical content");
        }
        MachineRunCoordinationCode endpointCode = validateExactEndpoint(server, workstation);
        if (endpointCode != MachineRunCoordinationCode.ACCEPTED) {
            return result(endpointCode, server, Optional.empty(), Optional.empty(),
                    "Machine START exact Workstation instance is unavailable");
        }
        MachineOperatingMutation prepared = operatingService.prepareStart(
                server,
                workstation,
                policy,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                runService.configuration().configurationIdentity(),
                tick
        );
        if (!prepared.accepted()) {
            return result(
                    workstationCode(prepared),
                    server,
                    Optional.empty(),
                    Optional.empty(),
                    prepared.detail()
            );
        }
        MachineRunRegistryMutation acceptedRun = runService.acceptStart(
                server,
                prepared.startEvidence().orElseThrow(),
                tick
        );
        if (!acceptedRun.accepted()) {
            operatingService.abandonUncommittedStart(
                    server,
                    workstation.instanceId().value(),
                    prepared.startEvidence().orElseThrow().authorizationIdentity(),
                    tick
            );
            return result(
                    executionCode(acceptedRun),
                    server,
                    acceptedRun.run(),
                    Optional.empty(),
                    acceptedRun.detail()
            );
        }
        MachineRunRecord run = acceptedRun.run().orElseThrow();
        MachineOperatingMutation activated = operatingService.activateStart(
                server,
                workstation.instanceId().value(),
                run.runIdentity(),
                run.startEvidence().authorizationIdentity(),
                tick
        );
        if (!activated.accepted()) {
            requireRecovery(server, run, prepared.record().orElseThrow(),
                    "Machine START was accepted by Execution but Workstation activation was not published", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server, Optional.of(run), Optional.empty(),
                    activated.detail());
        }
        return result(
                acceptedRun.changed() ? MachineRunCoordinationCode.ACCEPTED : MachineRunCoordinationCode.EXISTING_RESULT,
                server,
                Optional.of(run),
                Optional.empty(),
                acceptedRun.detail()
        );
    }

    public synchronized MachineRunCoordinationResult stop(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            long tick
    ) {
        Optional<MachineRunRecord> prior = runService.snapshot(server)
                .stopForRequest(sourceOwner, sourceRequestIdentity);
        if (prior.isPresent()) {
            MachineRunRecord existing = prior.orElseThrow();
            MachineStopAuthorizationEvidence existingStop = existing.stopEvidence().orElseThrow();
            if (existing.runIdentity().equals(runIdentity)
                    && existingStop.expectedRunRevision() == expectedRunRevision
                    && existingStop.expectedOperatingRevision() == expectedOperatingRevision) {
                return result(MachineRunCoordinationCode.EXISTING_RESULT, server, prior, Optional.empty(),
                        "Existing Machine STOP observed");
            }
            return result(MachineRunCoordinationCode.STALE_RUN, server, prior, Optional.empty(),
                    "Machine STOP request identity targets another Run");
        }
        MachineRunRecord target = runService.find(server, runIdentity).orElse(null);
        if (target == null
                || target.lifecycle().terminal()
                || runService.snapshot(server).activeFor(target.workstationInstanceIdentity())
                .filter(target::equals).isEmpty()) {
            return result(MachineRunCoordinationCode.STALE_RUN, server, Optional.ofNullable(target), Optional.empty(),
                    "Machine STOP targets a historical or inactive Run");
        }
        MachineOperatingMutation authorized = operatingService.authorizeStop(
                server,
                runIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                runService.configuration().configurationIdentity(),
                tick
        );
        if (!authorized.accepted()) {
            return result(workstationCode(authorized), server, Optional.empty(), Optional.empty(), authorized.detail());
        }
        MachineStopAuthorizationEvidence evidence = authorized.stopEvidence().orElseThrow();
        MachineRunRegistryMutation accepted = runService.acceptStop(server, evidence, tick);
        if (!accepted.accepted()) {
            return result(executionCode(accepted), server, accepted.run(), Optional.empty(), accepted.detail());
        }
        MachineOperatingMutation published = operatingService.publishStop(server, evidence, tick);
        if (!published.accepted()) {
            MachineRunRecord run = accepted.run().orElseThrow();
            requireRecovery(server, run, authorized.record().orElseThrow(),
                    "Execution accepted STOP but Workstation did not publish STOPPING", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server, Optional.of(run), Optional.empty(),
                    published.detail());
        }
        MachineRunRecord run = runService.find(server, runIdentity).orElseThrow();
        resolveChildAtStopBoundary(server, run, tick);
        completeStopIfSafe(server, runIdentity, tick);
        return result(MachineRunCoordinationCode.ACCEPTED, server, runService.find(server, runIdentity),
                Optional.empty(), "Machine STOP accepted at the deterministic safe boundary");
    }

    public synchronized MachineRunCoordinationResult admitChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            ExecutionAuthorization authorization,
            long tick
    ) {
        Objects.requireNonNull(authorization, "authorization");
        MachineRunRecord run = runService.find(server, runIdentity).orElse(null);
        if (run == null) return result(MachineRunCoordinationCode.STALE_RUN, server, Optional.empty(), Optional.empty(),
                "Unknown Machine Run");
        MachineOperatingRecord operating = operatingService.find(server, run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null || operating.currentRunIdentity().filter(runIdentity::equals).isEmpty()) {
            return result(MachineRunCoordinationCode.WORKSTATION_REJECTED, server, Optional.of(run), Optional.empty(),
                    "Workstation does not publish the exact Machine Run");
        }
        MachineRunCoordinationCode endpointCode = validateExactEndpoint(server, operating.workstation());
        if (endpointCode != MachineRunCoordinationCode.ACCEPTED
                || operating.endpointAvailability() != MachineEndpointAvailability.AVAILABLE) {
            return result(endpointCode == MachineRunCoordinationCode.ACCEPTED
                            ? MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE : endpointCode,
                    server, Optional.of(run), Optional.empty(), "Exact Workstation endpoint is unavailable");
        }
        if (!run.lifecycle().authorizesChildren() || !operating.state().canAdmitChild()) {
            return result(MachineRunCoordinationCode.WORKSTATION_REJECTED, server, Optional.of(run), Optional.empty(),
                    "Machine Run or operating state blocks child admission");
        }
        MachineRunRegistryMutation prepared = runService.prepareChild(
                server,
                runIdentity,
                expectedRunRevision,
                authorization.evidence(),
                tick
        );
        if (!prepared.accepted()) {
            return result(executionCode(prepared), server, prepared.run(), Optional.empty(), prepared.detail());
        }
        ExecutionOperationId operationId = authorization.operationId();
        Optional<ExecutionOperationSnapshot> existingOperation = executionService.managerFor(server).find(operationId);
        if (!prepared.changed() && existingOperation.isPresent()) {
            return result(MachineRunCoordinationCode.EXISTING_RESULT, server, prepared.run(), existingOperation,
                    "Existing Machine Run child observed");
        }
        ExecutionOperationResult<ExecutionOperationSnapshot> accepted = executionService.acceptAuthorizationDurably(
                server,
                authorization,
                tick
        );
        if (!accepted.accepted()) {
            runService.cancelPreparedChild(
                    server,
                    runIdentity,
                    operationId,
                    MachineRunResultCode.CHILD_RESULT_CONFLICT,
                    tick
            );
            return result(MachineRunCoordinationCode.EXECUTION_REJECTED, server,
                    runService.find(server, runIdentity), Optional.empty(), accepted.messages().getFirst());
        }
        ExecutionOperationSnapshot operation = accepted.value().orElseThrow();
        MachineRunRegistryMutation admitted = runService.admitPreparedChild(server, runIdentity, operation, tick);
        if (!admitted.accepted()) {
            executionService.cancelBeforeStartDurably(server, operation.operationId(), tick,
                    "Machine Run child admission failed");
            requireRecovery(server, run, operating, "Execution child admission could not be published", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server,
                    runService.find(server, runIdentity), Optional.of(operation), admitted.detail());
        }
        MachineOperatingMutation bound = operatingService.bindChild(server, runIdentity, operation.operationId(), tick);
        if (!bound.accepted()) {
            ExecutionOperationResult<ExecutionOperationSnapshot> cancelled = executionService.cancelBeforeStartDurably(
                    server,
                    operation.operationId(),
                    tick,
                    "Workstation child binding failed"
            );
            cancelled.value().ifPresent(snapshot -> runService.observeChild(server, runIdentity, snapshot, tick));
            requireRecovery(server, admitted.run().orElseThrow(), operating,
                    "Execution child was admitted but Workstation binding failed", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server,
                    runService.find(server, runIdentity), Optional.of(operation), bound.detail());
        }
        return result(MachineRunCoordinationCode.ACCEPTED, server, admitted.run(), Optional.of(operation),
                "One bounded child Execution operation admitted; no follow-on work was scheduled");
    }

    public synchronized MachineRunCoordinationResult observeChild(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        ExecutionOperationSnapshot operation = executionService.managerFor(server).find(operationId).orElse(null);
        if (operation == null) {
            MachineRunRecord run = runService.find(server, runIdentity).orElse(null);
            if (run != null) requireRecovery(server, run,
                    operatingService.find(server, run.workstationInstanceIdentity()).orElse(null),
                    "Machine Run child references a missing Execution operation", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server, Optional.ofNullable(run),
                    Optional.empty(), "Machine Run child Execution operation is missing");
        }
        if (!operation.status().terminal()) {
            return result(MachineRunCoordinationCode.EXISTING_RESULT, server, runService.find(server, runIdentity),
                    Optional.of(operation), "Machine Run child remains nonterminal");
        }
        executionService.persistNow(server);
        MachineRunRegistryMutation observed = runService.observeChild(server, runIdentity, operation, tick);
        if (!observed.accepted()) {
            return result(executionCode(observed), server, observed.run(), Optional.of(operation), observed.detail());
        }
        operatingService.clearChild(server, runIdentity, operationId, tick);
        MachineRunRecord run = runService.find(server, runIdentity).orElseThrow();
        if (operation.status() == com.butchercraft.world.execution.ExecutionStatus.UNKNOWN_OUTCOME) {
            MachineOperatingRecord operating = operatingService.find(server, run.workstationInstanceIdentity())
                    .orElse(null);
            requireRecovery(server, run, operating, "Machine Run child has Unknown Outcome", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server, Optional.of(run),
                    Optional.of(operation), "Machine Run child requires recovery");
        }
        completeStopIfSafe(server, runIdentity, tick);
        return result(MachineRunCoordinationCode.ACCEPTED, server, runService.find(server, runIdentity),
                Optional.of(operation), "Machine Run child terminal result observed");
    }

    public synchronized MachineRunCoordinationResult resume(
            MinecraftServer server,
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            long tick
    ) {
        MachineRunRecord run = runService.find(server, runIdentity).orElse(null);
        if (run == null) return result(MachineRunCoordinationCode.STALE_RUN, server, Optional.empty(), Optional.empty(),
                "Unknown Machine Run");
        MachineOperatingRecord operating = operatingService.find(server, run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null || validateExactEndpoint(server, operating.workstation())
                != MachineRunCoordinationCode.ACCEPTED) {
            return result(MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE, server, Optional.of(run), Optional.empty(),
                    "Exact Workstation instance is unavailable for resume");
        }
        MachineRunRegistryMutation resumedRun = runService.resume(server, runIdentity, expectedRunRevision, tick);
        if (!resumedRun.accepted()) {
            return result(executionCode(resumedRun), server, resumedRun.run(), Optional.empty(), resumedRun.detail());
        }
        MachineOperatingMutation resumedOperating = operatingService.resume(
                server,
                runIdentity,
                expectedOperatingRevision,
                tick
        );
        if (!resumedOperating.accepted()) {
            requireRecovery(server, resumedRun.run().orElseThrow(), operating,
                    "Execution resumed Run but Workstation resume failed", tick);
            return result(MachineRunCoordinationCode.RECOVERY_REQUIRED, server, resumedRun.run(), Optional.empty(),
                    resumedOperating.detail());
        }
        return result(MachineRunCoordinationCode.ACCEPTED, server, resumedRun.run(), Optional.empty(),
                "Exact persisted Machine Run resumed without allocating a new generation");
    }

    public synchronized MachineRunCoordinationResult endpointUnavailable(
            MinecraftServer server,
            String workstationInstanceIdentity,
            long tick
    ) {
        MachineOperatingMutation mutation = operatingService.availability(
                server,
                workstationInstanceIdentity,
                MachineEndpointAvailability.UNAVAILABLE,
                tick
        );
        return result(workstationCode(mutation), server,
                runService.snapshot(server).activeFor(workstationInstanceIdentity), Optional.empty(), mutation.detail());
    }

    public synchronized MachineRunCoordinationResult endpointAvailable(
            MinecraftServer server,
            MachineWorkstationReference workstation,
            long tick
    ) {
        MachineRunCoordinationCode code = validateExactEndpoint(server, workstation);
        if (code != MachineRunCoordinationCode.ACCEPTED) {
            return result(code, server, runService.snapshot(server).activeFor(workstation.instanceId().value()),
                    Optional.empty(), "Loaded endpoint does not match the persisted Workstation instance");
        }
        MachineOperatingMutation mutation = operatingService.availability(
                server,
                workstation.instanceId().value(),
                MachineEndpointAvailability.AVAILABLE,
                tick
        );
        return result(workstationCode(mutation), server,
                runService.snapshot(server).activeFor(workstation.instanceId().value()), Optional.empty(), mutation.detail());
    }

    public synchronized MachineRunCoordinationResult replacementDetected(
            MinecraftServer server,
            String workstationInstanceIdentity,
            long tick
    ) {
        MachineRunRecord run = runService.snapshot(server).activeFor(workstationInstanceIdentity).orElse(null);
        MachineOperatingMutation operating = operatingService.recoveryRequired(
                server,
                workstationInstanceIdentity,
                MachineOperatingResultCode.REPLACEMENT_INSTANCE,
                "Persisted Machine Run Workstation instance was replaced",
                MachineEndpointAvailability.REPLACED,
                tick
        );
        if (run != null) runService.recoveryRequired(
                server,
                run.runIdentity(),
                MachineRunResultCode.REPLACEMENT_INSTANCE,
                "Persisted Machine Run cannot transfer to a replacement Workstation instance",
                tick
        );
        return result(MachineRunCoordinationCode.REPLACEMENT_INSTANCE, server, Optional.ofNullable(run),
                Optional.empty(), operating.detail());
    }

    public synchronized MachineRunDiagnostics diagnostics(MinecraftServer server, String workstationInstanceIdentity) {
        MachineOperatingRecord operating = operatingService.find(server, workstationInstanceIdentity).orElse(null);
        MachineRunRecord run = runService.snapshot(server).activeFor(workstationInstanceIdentity).orElse(null);
        return new MachineRunDiagnostics(
                workstationInstanceIdentity,
                Optional.ofNullable(run).map(MachineRunRecord::runIdentity),
                Optional.ofNullable(run).map(MachineRunRecord::lifecycle),
                Optional.ofNullable(operating).map(MachineOperatingRecord::state),
                Optional.ofNullable(operating).flatMap(MachineOperatingRecord::activeChildOperationId),
                run == null ? 0L : run.nextChildSequence(),
                run == null ? 0L : run.revision(),
                operating == null ? 0L : operating.revision(),
                operating == null ? "unknown" : operating.endpointAvailability().serializedName(),
                Optional.ofNullable(operating).flatMap(MachineOperatingRecord::recoveryDetail)
                        .or(() -> Optional.ofNullable(run).flatMap(MachineRunRecord::recoveryDetail))
        );
    }

    private void reconcileForRestart(MinecraftServer server, long tick) {
        for (MachineOperatingRecord operating : operatingService.snapshot(server).records()) {
            if (operating.state() == MachineOperatingState.STARTING) {
                Optional<MachineRunRecord> accepted = runService.snapshot(server).runs().stream()
                        .filter(run -> run.workstationInstanceIdentity().equals(operating.workstation().instanceId().value()))
                        .filter(run -> operating.startAuthorizationIdentity()
                                .filter(run.startEvidence().authorizationIdentity()::equals).isPresent())
                        .filter(run -> !run.lifecycle().terminal())
                        .findFirst();
                if (accepted.isPresent()) {
                    MachineRunRecord run = accepted.orElseThrow();
                    operatingService.activateStart(
                            server,
                            run.workstationInstanceIdentity(),
                            run.runIdentity(),
                            run.startEvidence().authorizationIdentity(),
                            tick
                    );
                } else {
                    operatingService.abandonUncommittedStart(
                            server,
                            operating.workstation().instanceId().value(),
                            operating.startAuthorizationIdentity().orElseThrow(),
                            tick
                    );
                }
            }
        }
        for (MachineRunRecord loadedRun : runService.snapshot(server).runs()) {
            if (loadedRun.lifecycle().terminal()) continue;
            MachineRunRecord run = runService.find(server, loadedRun.runIdentity()).orElseThrow();
            MachineOperatingRecord operating = operatingService.find(server, run.workstationInstanceIdentity())
                    .orElse(null);
            if (operating == null || operating.currentRunIdentity().filter(run.runIdentity()::equals).isEmpty()) {
                requireRecovery(server, run, operating,
                        "Execution Machine Run has no matching Workstation operating-state publication", tick);
                continue;
            }
            MachineRunCoordinationCode endpointCode = validateExactEndpoint(server, operating.workstation());
            if (endpointCode != MachineRunCoordinationCode.ACCEPTED) {
                requireRecovery(server, run, operating,
                        "Persisted Machine Run endpoint is unavailable, retired, or replaced", tick);
                continue;
            }
            if (run.currentChild().isPresent()) {
                var child = run.currentChild().orElseThrow();
                Optional<ExecutionOperationSnapshot> operation = executionService.managerFor(server)
                        .find(child.operationId());
                if (operation.isEmpty() && child.state() == MachineRunChildState.PREPARED) {
                    runService.cancelPreparedChild(
                            server,
                            run.runIdentity(),
                            child.operationId(),
                            MachineRunResultCode.CHILD_NOT_PREPARED,
                            tick
                    );
                } else if (operation.isEmpty()) {
                    requireRecovery(server, run, operating,
                            "Admitted Machine Run child has no persisted Execution operation", tick);
                    continue;
                } else if (operation.orElseThrow().status().terminal()) {
                    observeChild(server, run.runIdentity(), child.operationId(), tick);
                }
            }
            run = runService.find(server, loadedRun.runIdentity()).orElseThrow();
            if (run.lifecycle() == MachineRunLifecycle.STOP_REQUESTED && run.currentChild().isEmpty()) {
                completeStopIfSafe(server, run.runIdentity(), tick);
                continue;
            }
            if (run.lifecycle() != MachineRunLifecycle.RECOVERY_REQUIRED) {
                runService.suspendForRestart(server, run.runIdentity(), tick);
                operatingService.suspendForRestart(server, run.runIdentity(), tick);
            }
        }
        for (MachineOperatingRecord operating : operatingService.snapshot(server).records()) {
            if (operating.currentRunIdentity().isPresent()
                    && runService.find(server, operating.currentRunIdentity().orElseThrow()).isEmpty()) {
                operatingService.recoveryRequired(
                        server,
                        operating.workstation().instanceId().value(),
                        MachineOperatingResultCode.RECOVERY_REQUIRED,
                        "Workstation operating state references a missing Execution Machine Run",
                        operating.endpointAvailability(),
                        tick
                );
            }
        }
    }

    private void resolveChildAtStopBoundary(MinecraftServer server, MachineRunRecord run, long tick) {
        if (run.currentChild().isEmpty()) return;
        var child = run.currentChild().orElseThrow();
        Optional<ExecutionOperationSnapshot> operation = executionService.managerFor(server).find(child.operationId());
        if (operation.isEmpty() && child.state() == MachineRunChildState.PREPARED) {
            runService.cancelPreparedChild(
                    server,
                    run.runIdentity(),
                    child.operationId(),
                    MachineRunResultCode.CHILD_NOT_PREPARED,
                    tick
            );
            return;
        }
        if (operation.isEmpty()) {
            requireRecovery(server, run, operatingService.find(server, run.workstationInstanceIdentity()).orElse(null),
                    "STOP cannot prove the active child Execution operation", tick);
            return;
        }
        ExecutionOperationSnapshot snapshot = operation.orElseThrow();
        if (snapshot.status().terminal()) {
            observeChild(server, run.runIdentity(), snapshot.operationId(), tick);
        } else if (!snapshot.schedulerInvocationStarted()) {
            ExecutionOperationResult<ExecutionOperationSnapshot> cancelled = executionService.cancelBeforeStartDurably(
                    server,
                    snapshot.operationId(),
                    tick,
                    "Exact Machine Run STOP requested before child invocation"
            );
            cancelled.value().ifPresent(value -> observeChild(server, run.runIdentity(), value.operationId(), tick));
        }
    }

    private void completeStopIfSafe(MinecraftServer server, MachineRunIdentity runIdentity, long tick) {
        MachineRunRecord run = runService.find(server, runIdentity).orElse(null);
        if (run == null || run.lifecycle() != MachineRunLifecycle.STOP_REQUESTED || run.currentChild().isPresent()) return;
        MachineOperatingRecord operating = operatingService.find(server, run.workstationInstanceIdentity()).orElse(null);
        if (operating == null || operating.activeChildOperationId().isPresent()) return;
        if (operating.state() != MachineOperatingState.STOPPING) {
            MachineStopAuthorizationEvidence stopEvidence = run.stopEvidence().orElse(null);
            if (stopEvidence == null) return;
            MachineOperatingMutation stopping = operatingService.publishStop(server, stopEvidence, tick);
            if (!stopping.accepted()) return;
        }
        MachineOperatingMutation off = operatingService.completeStop(server, runIdentity, tick);
        if (off.accepted()) runService.completeStop(server, runIdentity, tick);
    }

    private MachineRunCoordinationCode validateExactEndpoint(
            MinecraftServer server,
            MachineWorkstationReference workstation
    ) {
        WorkstationInstanceRecord instance = endpointService.instanceRecord(server, workstation.instanceId())
                .orElse(null);
        if (instance == null) return MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE;
        if (instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE) {
            return instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED
                    || instance.lifecycle() == WorkstationInstanceLifecycle.IDENTITY_CONFLICT
                    ? MachineRunCoordinationCode.REPLACEMENT_INSTANCE
                    : MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE;
        }
        if (!instance.endpointKey().equals(workstation.endpointKey())
                || instance.generation() != workstation.generation()
                || !instance.allocationConfigurationIdentity()
                .equals(workstation.allocationConfigurationIdentity())) {
            return MachineRunCoordinationCode.REPLACEMENT_INSTANCE;
        }
        return MachineRunCoordinationCode.ACCEPTED;
    }

    private void requireRecovery(
            MinecraftServer server,
            MachineRunRecord run,
            MachineOperatingRecord operating,
            String detail,
            long tick
    ) {
        runService.recoveryRequired(server, run.runIdentity(), MachineRunResultCode.RECOVERY_REQUIRED, detail, tick);
        if (operating != null) {
            operatingService.recoveryRequired(
                    server,
                    operating.workstation().instanceId().value(),
                    MachineOperatingResultCode.RECOVERY_REQUIRED,
                    detail,
                    operating.endpointAvailability(),
                    tick
            );
        }
    }

    private MachineRunCoordinationResult result(
            MachineRunCoordinationCode code,
            MinecraftServer server,
            Optional<MachineRunRecord> run,
            Optional<ExecutionOperationSnapshot> child,
            String detail
    ) {
        Optional<MachineOperatingRecord> operating = run.flatMap(value ->
                operatingService.find(server, value.workstationInstanceIdentity()));
        return new MachineRunCoordinationResult(code, run, operating, child, detail);
    }

    private static MachineRunCoordinationCode workstationCode(MachineOperatingMutation mutation) {
        return switch (mutation.code()) {
            case ACCEPTED -> MachineRunCoordinationCode.ACCEPTED;
            case EXISTING_STATE -> MachineRunCoordinationCode.EXISTING_RESULT;
            case ENDPOINT_UNAVAILABLE -> MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE;
            case REPLACEMENT_INSTANCE -> MachineRunCoordinationCode.REPLACEMENT_INSTANCE;
            case RECOVERY_REQUIRED -> MachineRunCoordinationCode.RECOVERY_REQUIRED;
            default -> MachineRunCoordinationCode.WORKSTATION_REJECTED;
        };
    }

    private static MachineRunCoordinationCode executionCode(MachineRunRegistryMutation mutation) {
        return switch (mutation.code()) {
            case ACCEPTED -> MachineRunCoordinationCode.ACCEPTED;
            case EXISTING_RUN -> MachineRunCoordinationCode.EXISTING_RESULT;
            case STALE_RUN, UNKNOWN_RUN -> MachineRunCoordinationCode.STALE_RUN;
            case ENDPOINT_UNAVAILABLE -> MachineRunCoordinationCode.ENDPOINT_UNAVAILABLE;
            case REPLACEMENT_INSTANCE, INSTANCE_MISMATCH -> MachineRunCoordinationCode.REPLACEMENT_INSTANCE;
            case CHILD_ALREADY_ACTIVE -> MachineRunCoordinationCode.CHILD_ALREADY_ACTIVE;
            case RECOVERY_REQUIRED -> MachineRunCoordinationCode.RECOVERY_REQUIRED;
            default -> MachineRunCoordinationCode.EXECUTION_REJECTED;
        };
    }
}
