package com.butchercraft.integration.machine;

import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
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
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.execution.MachineRunResultCode;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.StartupMutationGateService;
import com.butchercraft.world.simulation.SimulationClockService;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Machine-neutral DG-005 continuous-cycle coordination for the two activated processing machines. */
public final class PoweredProcessingMachineRunService<M extends AbstractProcessingWorkstationBlockEntity> {
    private static final MachineOperatingPolicy POLICY = MachineOperatingPolicy.poweredContinuousExplicitStop();

    private final PoweredProcessingMachineRunAdapter<M> adapter;
    private final MachineRunCoordinatorService coordinator;
    private final ExecutionMachineRunService runService;
    private final MachineOperatingStateService operatingService;
    private final WorkstationEndpointService endpointService;

    public PoweredProcessingMachineRunService(PoweredProcessingMachineRunAdapter<M> adapter) {
        this(
                adapter,
                MachineRunCoordinatorService.INSTANCE,
                ExecutionMachineRunService.INSTANCE,
                MachineOperatingStateService.INSTANCE,
                WorkstationEndpointService.INSTANCE
        );
    }

    PoweredProcessingMachineRunService(
            PoweredProcessingMachineRunAdapter<M> adapter,
            MachineRunCoordinatorService coordinator,
            ExecutionMachineRunService runService,
            MachineOperatingStateService operatingService,
            WorkstationEndpointService endpointService
    ) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.runService = Objects.requireNonNull(runService, "runService");
        this.operatingService = Objects.requireNonNull(operatingService, "operatingService");
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
    }

    public PoweredMachineRunControlResult start(ServerLevel level, M machine) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(machine, "machine");
        long tick = tick(level);
        WorkstationEndpointReferenceResult endpoint = endpointService.referenceFor(level, machine.getBlockPos());
        if (!endpoint.succeeded()) return rejected(PoweredMachineRunControlCode.REJECTED, machine, endpoint.detail());
        MachineWorkstationReference workstation = machineReference(endpoint.reference().orElseThrow());
        Optional<MachineRunRecord> active = runService.snapshot(level.getServer())
                .activeFor(workstation.instanceId().value());
        if (active.isPresent()) {
            return result(PoweredMachineRunControlCode.EXISTING_RESULT, machine, active,
                    adapter.displayName() + " already has an active Machine Run");
        }
        if (machine.productionSnapshot().activeExecutionOperationId().isPresent()
                || machine.workstationState() == WorkstationState.PROCESSING
                || adapter.hasConflictingReservation(level, machine)) {
            return rejected(PoweredMachineRunControlCode.BUSY, machine,
                    adapter.displayName() + " is reserved or already owns a bounded operation");
        }
        long operatingRevision = operatingService.find(level.getServer(), workstation.instanceId().value())
                .map(MachineOperatingRecord::revision)
                .orElse(0L);
        MachineRunCoordinationResult started = coordinator.start(
                level.getServer(),
                workstation,
                POLICY,
                operatingRevision,
                adapter.controlOwner(),
                requestIdentity("start", workstation.instanceId().value(), operatingRevision),
                tick
        );
        if (!started.accepted()) return coordinationFailure(machine, started);
        MachineRunRecord run = started.run().orElseThrow();
        evaluate(level, machine, run, tick);
        return result(
                started.code() == MachineRunCoordinationCode.EXISTING_RESULT
                        ? PoweredMachineRunControlCode.EXISTING_RESULT : PoweredMachineRunControlCode.STARTED,
                machine,
                Optional.of(run),
                started.detail()
        );
    }

    public PoweredMachineRunControlResult stop(ServerLevel level, M machine) {
        Optional<MachineRunRecord> active = activeRun(level, machine);
        if (active.isEmpty()) {
            return result(PoweredMachineRunControlCode.EXISTING_RESULT, machine, Optional.empty(),
                    adapter.displayName() + " is already OFF");
        }
        MachineRunRecord run = active.orElseThrow();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null) return rejected(PoweredMachineRunControlCode.RECOVERY_REQUIRED, machine,
                "Active Machine Run has no Workstation operating-state record");
        long tick = tick(level);
        MachineRunCoordinationResult stopped = coordinator.stop(
                level.getServer(),
                run.runIdentity(),
                run.revision(),
                operating.revision(),
                adapter.controlOwner(),
                requestIdentity("stop", run.runIdentity().value(), run.revision()),
                tick
        );
        if (!stopped.accepted()) return coordinationFailure(machine, stopped);
        Optional<MachineRunRecord> current = runService.find(level.getServer(), run.runIdentity());
        boolean terminal = current.map(value -> value.lifecycle() == MachineRunLifecycle.STOPPED).orElse(false);
        return result(
                terminal ? PoweredMachineRunControlCode.STOPPED : PoweredMachineRunControlCode.STOP_REQUESTED,
                machine,
                current,
                stopped.detail()
        );
    }

    public PoweredMachineRunControlResult resume(ServerLevel level, M machine) {
        Optional<MachineRunRecord> active = activeRun(level, machine);
        if (active.isEmpty()) return rejected(PoweredMachineRunControlCode.REJECTED, machine,
                adapter.displayName() + " has no restart-suspended Machine Run");
        MachineRunRecord run = active.orElseThrow();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null
                || run.lifecycle() != MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED
                || operating.state() != MachineOperatingState.RESTART_REQUIRED) {
            return rejected(PoweredMachineRunControlCode.REJECTED, machine,
                    adapter.displayName() + " is not awaiting an explicit restart decision");
        }
        MachineRunCoordinationResult resumed = coordinator.resume(
                level.getServer(),
                run.runIdentity(),
                run.revision(),
                operating.revision(),
                tick(level)
        );
        if (!resumed.accepted()) return coordinationFailure(machine, resumed);
        MachineRunRecord current = resumed.run().orElseThrow();
        evaluate(level, machine, current, tick(level));
        return result(PoweredMachineRunControlCode.RESUMED, machine, Optional.of(current), resumed.detail());
    }

    public PoweredMachineRunControlResult shiftControl(ServerLevel level, M machine) {
        PoweredMachineRunStatus status = status(level, machine);
        if (status.operatingState() == MachineOperatingState.OFF) return start(level, machine);
        return stop(level, machine);
    }

    public void tick(ServerLevel level, M machine) {
        if (!runtimeMutationPermitted(level)) return;
        Optional<MachineRunRecord> active = activeRun(level, machine);
        if (active.isEmpty()) return;
        long tick = tick(level);
        MachineRunRecord run = active.orElseThrow();
        if (run.currentChild().isPresent()) {
            var child = run.currentChild().orElseThrow();
            Optional<ExecutionOperationSnapshot> operation = ExecutionService.INSTANCE
                    .managerFor(level.getServer()).find(child.operationId());
            if (operation.isPresent() && operation.orElseThrow().status().terminal()) {
                coordinator.observeChild(level.getServer(), run.runIdentity(), child.operationId(), tick);
            }
        }
        run = runService.find(level.getServer(), run.runIdentity()).orElse(run);
        if (run.lifecycle() == MachineRunLifecycle.STOP_REQUESTED) return;
        if (run.lifecycle() != MachineRunLifecycle.AUTHORIZED || run.currentChild().isPresent()) return;
        evaluate(level, machine, run, tick);
    }

    public void endpointLoaded(ServerLevel level, M machine) {
        if (!runtimeMutationPermitted(level)) return;
        if (adapter.endpointProjection(machine).instanceId().isEmpty()) return;
        WorkstationEndpointReferenceResult reference = endpointService.referenceFor(level, machine.getBlockPos());
        if (reference.succeeded()) {
            coordinator.endpointAvailable(
                    level.getServer(),
                    machineReference(reference.reference().orElseThrow()),
                    tick(level)
            );
        }
    }

    public void replacementDetected(ServerLevel level, M machine) {
        if (!runtimeMutationPermitted(level)) return;
        adapter.endpointProjection(machine).instanceId().ifPresent(instanceId ->
                coordinator.replacementDetected(level.getServer(), instanceId.value(), tick(level)));
    }

    private static boolean runtimeMutationPermitted(ServerLevel level) {
        return StartupMutationGateService.INSTANCE.permits(
                level.getServer(), LegacySplitRecoveryParticipants.EXECUTION)
                && StartupMutationGateService.INSTANCE.permits(
                level.getServer(), LegacySplitRecoveryParticipants.WORKSTATION);
    }

    public boolean hasActiveRun(ServerLevel level, M machine) {
        return activeRun(level, machine).isPresent();
    }

    public PoweredMachineRunStatus status(ServerLevel level, M machine) {
        if (adapter.endpointProjection(machine).instanceId().isEmpty()) return PoweredMachineRunStatus.off();
        String instanceIdentity = adapter.endpointProjection(machine).instanceId().orElseThrow().value();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), instanceIdentity).orElse(null);
        Optional<MachineRunRecord> run = runService.snapshot(level.getServer()).activeFor(instanceIdentity);
        if (operating == null && run.isEmpty()) return PoweredMachineRunStatus.off();
        MachineRunRecord value = run.orElse(null);
        return new PoweredMachineRunStatus(
                operating == null ? MachineOperatingState.RECOVERY_REQUIRED : operating.state(),
                run.map(MachineRunRecord::runIdentity),
                run.map(MachineRunRecord::lifecycle),
                run.flatMap(record -> record.currentChild().map(child -> child.operationId())),
                value == null ? 0L : value.generation(),
                value == null ? 0L : value.terminalChildren().size(),
                value == null ? 1L : value.nextChildSequence(),
                value == null ? 0L : value.revision(),
                operating == null ? 0L : operating.revision(),
                operating == null
                        ? Optional.of("Machine operating-state record is unavailable")
                        : operating.recoveryDetail().or(() -> value == null ? Optional.empty() : value.recoveryDetail())
        );
    }

    private void evaluate(ServerLevel level, M machine, MachineRunRecord run, long tick) {
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null || operating.state() == MachineOperatingState.RESTART_REQUIRED
                || operating.state() == MachineOperatingState.STOPPING
                || operating.state() == MachineOperatingState.RECOVERY_REQUIRED) return;
        if (machine.workstationState() == WorkstationState.BLOCKED && machine.lastFailure().isPresent()) {
            WorkstationFailure failure = machine.lastFailure().orElseThrow();
            if (outputBlocked(failure)) {
                publish(level, run, MachineOperatingState.OUTPUT_BLOCKED,
                        Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
                return;
            }
            if (inputUnavailable(failure)) {
                publish(level, run, MachineOperatingState.RUNNING_EMPTY,
                        Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
                return;
            }
        }
        if (machine.inventory().input().isEmpty()) {
            publish(level, run, MachineOperatingState.RUNNING_EMPTY, Optional.empty(), Optional.empty(), tick);
            return;
        }
        publish(level, run, MachineOperatingState.RUNNING,
                Optional.of(eligibilityIdentity(run)), Optional.empty(), tick);
        MachineRunRecord current = runService.find(level.getServer(), run.runIdentity()).orElse(run);
        WorkstationProductionRequestResult requested = adapter.requestRunProcessing(
                machine,
                new WorkstationTickContext(level, machine.getBlockPos()),
                childBindingIdentities(current),
                authorization -> coordinator.admitChild(
                        level.getServer(),
                        current.runIdentity(),
                        current.revision(),
                        authorization,
                        tick
                )
        );
        if (requested.accepted()) return;
        WorkstationFailure failure = requested.failure().orElseThrow();
        if (outputBlocked(failure)) {
            publish(level, run, MachineOperatingState.OUTPUT_BLOCKED,
                    Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
        } else if (inputUnavailable(failure)) {
            publish(level, run, MachineOperatingState.RUNNING_EMPTY,
                    Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
        } else {
            String detail = adapter.displayName() + " Run child admission failed: " + failure.developerExplanation();
            operatingService.recoveryRequired(
                    level.getServer(),
                    run.workstationInstanceIdentity(),
                    MachineOperatingResultCode.RECOVERY_REQUIRED,
                    detail,
                    operating.endpointAvailability(),
                    tick
            );
            runService.recoveryRequired(
                    level.getServer(),
                    run.runIdentity(),
                    MachineRunResultCode.RECOVERY_REQUIRED,
                    detail,
                    tick
            );
        }
    }

    private void publish(
            ServerLevel level,
            MachineRunRecord run,
            MachineOperatingState state,
            Optional<String> eligibility,
            Optional<String> blockage,
            long tick
    ) {
        Optional<MachineOperatingRecord> current = operatingService.find(
                level.getServer(), run.workstationInstanceIdentity());
        if (current.filter(record -> record.state() == state
                && record.eligibilityIdentity().equals(eligibility)
                && record.blockageReason().equals(blockage)).isPresent()) {
            return;
        }
        MachineOperatingMutation mutation = operatingService.publishOperationalState(
                level.getServer(), run.runIdentity(), state, eligibility, blockage, tick);
        if (!mutation.accepted()) {
            operatingService.recoveryRequired(
                    level.getServer(),
                    run.workstationInstanceIdentity(),
                    MachineOperatingResultCode.RECOVERY_REQUIRED,
                    adapter.displayName() + " could not publish operating state "
                            + state.serializedName() + ": " + mutation.detail(),
                    mutation.record().map(MachineOperatingRecord::endpointAvailability)
                            .orElse(MachineEndpointAvailability.UNAVAILABLE),
                    tick
            );
        }
    }

    private Optional<MachineRunRecord> activeRun(ServerLevel level, M machine) {
        if (adapter.endpointProjection(machine).instanceId().isEmpty()) return Optional.empty();
        return runService.snapshot(level.getServer())
                .activeFor(adapter.endpointProjection(machine).instanceId().orElseThrow().value());
    }

    private PoweredMachineRunControlResult coordinationFailure(M machine, MachineRunCoordinationResult result) {
        PoweredMachineRunControlCode code = result.code() == MachineRunCoordinationCode.RECOVERY_REQUIRED
                || result.code() == MachineRunCoordinationCode.REPLACEMENT_INSTANCE
                ? PoweredMachineRunControlCode.RECOVERY_REQUIRED
                : result.code() == MachineRunCoordinationCode.CHILD_ALREADY_ACTIVE
                ? PoweredMachineRunControlCode.BUSY
                : PoweredMachineRunControlCode.REJECTED;
        return rejected(code, machine, result.detail());
    }

    private PoweredMachineRunControlResult rejected(
            PoweredMachineRunControlCode code,
            M machine,
            String detail
    ) {
        if (machine.getLevel() instanceof ServerLevel level) {
            PoweredMachineRunStatus status = status(level, machine);
            return new PoweredMachineRunControlResult(code, status.operatingState(), status.runIdentity(), detail);
        }
        return new PoweredMachineRunControlResult(code, MachineOperatingState.OFF, Optional.empty(), detail);
    }

    private PoweredMachineRunControlResult result(
            PoweredMachineRunControlCode code,
            M machine,
            Optional<MachineRunRecord> run,
            String detail
    ) {
        MachineOperatingState state = machine.getLevel() instanceof ServerLevel level
                ? status(level, machine).operatingState()
                : MachineOperatingState.OFF;
        return new PoweredMachineRunControlResult(code, state, run.map(MachineRunRecord::runIdentity), detail);
    }

    private static MachineWorkstationReference machineReference(WorkstationEndpointReference reference) {
        return new MachineWorkstationReference(
                reference.instanceId(),
                reference.endpointKey(),
                reference.generation(),
                WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity()
        );
    }

    private static List<String> childBindingIdentities(MachineRunRecord run) {
        return List.of(
                run.runIdentity().value(),
                run.workstationInstanceIdentity(),
                MachineRunRecord.childSequenceIdentity(run.nextChildSequence()),
                run.operatingPolicyIdentity()
        );
    }

    private String eligibilityIdentity(MachineRunRecord run) {
        return "butchercraft:" + adapter.identityPath() + "_eligibility/run_" + run.generation()
                + "/child_" + run.nextChildSequence();
    }

    private String requestIdentity(String action, String targetIdentity, long revision) {
        String suffix = targetIdentity.substring(targetIdentity.indexOf(':') + 1);
        return "butchercraft:" + adapter.identityPath() + "_control/" + action + "/" + suffix
                + "/revision_" + revision;
    }

    private static boolean outputBlocked(WorkstationFailure failure) {
        return failure.code() == WorkstationFailureCode.OUTPUT_OCCUPIED
                || failure.code() == WorkstationFailureCode.OUTPUT_INCOMPATIBLE;
    }

    private static boolean inputUnavailable(WorkstationFailure failure) {
        return failure.code() == WorkstationFailureCode.NO_INPUT
                || failure.code() == WorkstationFailureCode.NO_COMPATIBLE_OPERATION
                || failure.code() == WorkstationFailureCode.INPUT_NOT_PRODUCT
                || failure.code() == WorkstationFailureCode.MISSING_PRODUCT_DATA;
    }

    private static long tick(ServerLevel level) {
        return SimulationClockService.INSTANCE.clock(level.getServer()).simulationTick();
    }
}
