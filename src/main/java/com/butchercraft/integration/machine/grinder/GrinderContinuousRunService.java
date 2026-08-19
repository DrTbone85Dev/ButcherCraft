package com.butchercraft.integration.machine.grinder;

import com.butchercraft.integration.machine.MachineRunCoordinationCode;
import com.butchercraft.integration.machine.MachineRunCoordinationResult;
import com.butchercraft.integration.machine.MachineRunCoordinatorService;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderExecutionCoordinator;
import com.butchercraft.machine.grinder.execution.GrinderExecutionPreparation;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReferenceResult;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.operation.MachineOperatingMutation;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.operation.MachineWorkstationReference;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.MachineOperatingStateService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.simulation.SimulationClockService;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Grinder-only activation of the generic DG-005 Machine Run authority. */
public final class GrinderContinuousRunService {
    public static final GrinderContinuousRunService INSTANCE = new GrinderContinuousRunService(
            MachineRunCoordinatorService.INSTANCE,
            ExecutionMachineRunService.INSTANCE,
            MachineOperatingStateService.INSTANCE,
            WorkstationEndpointService.INSTANCE
    );

    private static final String CONTROL_OWNER = "butchercraft:player_grinder_control";
    private static final MachineOperatingPolicy POLICY = MachineOperatingPolicy.poweredContinuousExplicitStop();

    private final MachineRunCoordinatorService coordinator;
    private final ExecutionMachineRunService runService;
    private final MachineOperatingStateService operatingService;
    private final WorkstationEndpointService endpointService;

    GrinderContinuousRunService(
            MachineRunCoordinatorService coordinator,
            ExecutionMachineRunService runService,
            MachineOperatingStateService operatingService,
            WorkstationEndpointService endpointService
    ) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.runService = Objects.requireNonNull(runService, "runService");
        this.operatingService = Objects.requireNonNull(operatingService, "operatingService");
        this.endpointService = Objects.requireNonNull(endpointService, "endpointService");
    }

    public GrinderRunControlResult start(ServerLevel level, GrinderBlockEntity grinder) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grinder, "grinder");
        long tick = tick(level);
        WorkstationEndpointReferenceResult endpoint = endpointService.referenceFor(level, grinder.getBlockPos());
        if (!endpoint.succeeded()) return rejected(GrinderRunControlCode.REJECTED, grinder, endpoint.detail());
        MachineWorkstationReference workstation = machineReference(endpoint.reference().orElseThrow());
        Optional<MachineRunRecord> active = runService.snapshot(level.getServer())
                .activeFor(workstation.instanceId().value());
        if (active.isPresent()) {
            return result(GrinderRunControlCode.EXISTING_RESULT, grinder, active,
                    "Grinder already has an active Machine Run");
        }
        if (grinder.productionSnapshot().activeExecutionOperationId().isPresent()
                || grinder.workstationState() == WorkstationState.PROCESSING
                || WorkstationReservationService.INSTANCE.hasActiveReservationAt(level, grinder.getBlockPos())) {
            return rejected(GrinderRunControlCode.BUSY, grinder,
                    "Grinder is reserved or already owns a bounded operation");
        }
        long operatingRevision = operatingService.find(level.getServer(), workstation.instanceId().value())
                .map(MachineOperatingRecord::revision)
                .orElse(0L);
        String requestIdentity = requestIdentity("start", workstation.instanceId().value(), operatingRevision);
        MachineRunCoordinationResult started = coordinator.start(
                level.getServer(),
                workstation,
                POLICY,
                operatingRevision,
                CONTROL_OWNER,
                requestIdentity,
                tick
        );
        if (!started.accepted()) return coordinationFailure(grinder, started);
        MachineRunRecord run = started.run().orElseThrow();
        evaluate(level, grinder, run, tick);
        return result(
                started.code() == MachineRunCoordinationCode.EXISTING_RESULT
                        ? GrinderRunControlCode.EXISTING_RESULT : GrinderRunControlCode.STARTED,
                grinder,
                Optional.of(run),
                started.detail()
        );
    }

    public GrinderRunControlResult stop(ServerLevel level, GrinderBlockEntity grinder) {
        Optional<MachineRunRecord> active = activeRun(level, grinder);
        if (active.isEmpty()) {
            return result(GrinderRunControlCode.EXISTING_RESULT, grinder, Optional.empty(),
                    "Grinder is already OFF");
        }
        MachineRunRecord run = active.orElseThrow();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null) return rejected(GrinderRunControlCode.RECOVERY_REQUIRED, grinder,
                "Active Machine Run has no Workstation operating-state record");
        long tick = tick(level);
        MachineRunCoordinationResult stopped = coordinator.stop(
                level.getServer(),
                run.runIdentity(),
                run.revision(),
                operating.revision(),
                CONTROL_OWNER,
                requestIdentity("stop", run.runIdentity().value(), run.revision()),
                tick
        );
        if (!stopped.accepted()) return coordinationFailure(grinder, stopped);
        Optional<MachineRunRecord> current = runService.find(level.getServer(), run.runIdentity());
        boolean terminal = current.map(value -> value.lifecycle() == MachineRunLifecycle.STOPPED).orElse(false);
        return result(
                terminal ? GrinderRunControlCode.STOPPED : GrinderRunControlCode.STOP_REQUESTED,
                grinder,
                current,
                stopped.detail()
        );
    }

    public GrinderRunControlResult resume(ServerLevel level, GrinderBlockEntity grinder) {
        Optional<MachineRunRecord> active = activeRun(level, grinder);
        if (active.isEmpty()) return rejected(GrinderRunControlCode.REJECTED, grinder,
                "Grinder has no restart-suspended Machine Run");
        MachineRunRecord run = active.orElseThrow();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null
                || run.lifecycle() != MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED
                || operating.state() != MachineOperatingState.RESTART_REQUIRED) {
            return rejected(GrinderRunControlCode.REJECTED, grinder,
                    "Grinder is not awaiting an explicit restart decision");
        }
        MachineRunCoordinationResult resumed = coordinator.resume(
                level.getServer(),
                run.runIdentity(),
                run.revision(),
                operating.revision(),
                tick(level)
        );
        if (!resumed.accepted()) return coordinationFailure(grinder, resumed);
        MachineRunRecord current = resumed.run().orElseThrow();
        evaluate(level, grinder, current, tick(level));
        return result(GrinderRunControlCode.RESUMED, grinder, Optional.of(current), resumed.detail());
    }

    public GrinderRunControlResult shiftControl(ServerLevel level, GrinderBlockEntity grinder) {
        GrinderRunStatus status = status(level, grinder);
        if (status.operatingState() == MachineOperatingState.OFF) return start(level, grinder);
        return stop(level, grinder);
    }

    public void tick(ServerLevel level, GrinderBlockEntity grinder) {
        Optional<MachineRunRecord> active = activeRun(level, grinder);
        if (active.isEmpty()) return;
        long tick = tick(level);
        MachineRunRecord run = active.orElseThrow();
        if (run.currentChild().isPresent()) {
            var child = run.currentChild().orElseThrow();
            Optional<ExecutionOperationSnapshot> operation = com.butchercraft.world.ExecutionService.INSTANCE
                    .managerFor(level.getServer()).find(child.operationId());
            if (operation.isPresent() && operation.orElseThrow().status().terminal()) {
                coordinator.observeChild(level.getServer(), run.runIdentity(), child.operationId(), tick);
            }
        }
        run = runService.find(level.getServer(), run.runIdentity()).orElse(run);
        if (run.lifecycle() == MachineRunLifecycle.STOP_REQUESTED) {
            return;
        }
        if (run.lifecycle() != MachineRunLifecycle.AUTHORIZED || run.currentChild().isPresent()) return;
        evaluate(level, grinder, run, tick);
    }

    public void endpointLoaded(ServerLevel level, GrinderBlockEntity grinder) {
        if (grinder.endpointProjection().instanceId().isEmpty()) return;
        WorkstationEndpointReferenceResult reference = endpointService.referenceFor(level, grinder.getBlockPos());
        if (reference.succeeded()) {
            coordinator.endpointAvailable(level.getServer(), machineReference(reference.reference().orElseThrow()), tick(level));
        }
    }

    public void replacementDetected(ServerLevel level, GrinderBlockEntity grinder) {
        grinder.endpointProjection().instanceId().ifPresent(instanceId ->
                coordinator.replacementDetected(level.getServer(), instanceId.value(), tick(level)));
    }

    public boolean hasActiveRun(ServerLevel level, GrinderBlockEntity grinder) {
        return activeRun(level, grinder).isPresent();
    }

    public GrinderRunStatus status(ServerLevel level, GrinderBlockEntity grinder) {
        if (grinder.endpointProjection().instanceId().isEmpty()) return GrinderRunStatus.off();
        String instanceIdentity = grinder.endpointProjection().instanceId().orElseThrow().value();
        MachineOperatingRecord operating = operatingService.find(level.getServer(), instanceIdentity).orElse(null);
        Optional<MachineRunRecord> run = runService.snapshot(level.getServer()).activeFor(instanceIdentity);
        if (operating == null && run.isEmpty()) return GrinderRunStatus.off();
        MachineRunRecord value = run.orElse(null);
        return new GrinderRunStatus(
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

    private void evaluate(ServerLevel level, GrinderBlockEntity grinder, MachineRunRecord run, long tick) {
        MachineOperatingRecord operating = operatingService.find(level.getServer(), run.workstationInstanceIdentity())
                .orElse(null);
        if (operating == null || operating.state() == MachineOperatingState.RESTART_REQUIRED
                || operating.state() == MachineOperatingState.STOPPING
                || operating.state() == MachineOperatingState.RECOVERY_REQUIRED) return;
        if (grinder.workstationState() == WorkstationState.BLOCKED && grinder.lastFailure().isPresent()) {
            WorkstationFailure failure = grinder.lastFailure().orElseThrow();
            if (failure.code() == WorkstationFailureCode.OUTPUT_OCCUPIED
                    || failure.code() == WorkstationFailureCode.OUTPUT_INCOMPATIBLE) {
                publish(level, run, MachineOperatingState.OUTPUT_BLOCKED,
                        Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
                return;
            }
            if (failure.code() == WorkstationFailureCode.NO_INPUT
                    || failure.code() == WorkstationFailureCode.NO_COMPATIBLE_OPERATION
                    || failure.code() == WorkstationFailureCode.INPUT_NOT_PRODUCT
                    || failure.code() == WorkstationFailureCode.MISSING_PRODUCT_DATA) {
                publish(level, run, MachineOperatingState.RUNNING_EMPTY,
                        Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
                return;
            }
        }
        if (grinder.inventory().input().isEmpty()) {
            publish(level, run, MachineOperatingState.RUNNING_EMPTY, Optional.empty(), Optional.empty(), tick);
            return;
        }
        publish(level, run, MachineOperatingState.RUNNING,
                Optional.of(eligibilityIdentity(run)), Optional.empty(), tick);
        MachineRunRecord current = runService.find(level.getServer(), run.runIdentity()).orElse(run);
        WorkstationProductionRequestResult requested = grinder.requestRunProcessing(
                new WorkstationTickContext(level, grinder.getBlockPos()),
                startRequest -> {
                    GrinderExecutionPreparation preparation = GrinderExecutionCoordinator.INSTANCE
                            .prepareAuthorization(startRequest, childBindingIdentities(current));
                    MachineRunCoordinationResult admitted = coordinator.admitChild(
                            level.getServer(),
                            current.runIdentity(),
                            current.revision(),
                            preparation.authorization(),
                            tick
                    );
                    if (!admitted.accepted() || admitted.childOperation().isEmpty()) {
                        return com.butchercraft.workstation.WorkstationExecutionStartResult.rejected(
                                WorkstationFailure.of(
                                        WorkstationFailureCode.EXECUTION_AUTHORIZATION_REJECTED,
                                        admitted.detail()
                                )
                        );
                    }
                    return GrinderExecutionCoordinator.INSTANCE.acceptedResult(
                            preparation,
                            admitted.childOperation().orElseThrow()
                    );
                }
        );
        if (requested.accepted()) return;
        WorkstationFailure failure = requested.failure().orElseThrow();
        if (failure.code() == WorkstationFailureCode.OUTPUT_OCCUPIED
                || failure.code() == WorkstationFailureCode.OUTPUT_INCOMPATIBLE) {
            publish(level, run, MachineOperatingState.OUTPUT_BLOCKED,
                    Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
        } else if (failure.code() == WorkstationFailureCode.NO_INPUT
                || failure.code() == WorkstationFailureCode.NO_COMPATIBLE_OPERATION
                || failure.code() == WorkstationFailureCode.INPUT_NOT_PRODUCT
                || failure.code() == WorkstationFailureCode.MISSING_PRODUCT_DATA) {
            publish(level, run, MachineOperatingState.RUNNING_EMPTY,
                    Optional.empty(), Optional.of(failure.code().reasonCode()), tick);
        } else {
            operatingService.recoveryRequired(
                    level.getServer(),
                    run.workstationInstanceIdentity(),
                    com.butchercraft.workstation.operation.MachineOperatingResultCode.RECOVERY_REQUIRED,
                    "Grinder Run child admission failed: " + failure.developerExplanation(),
                    operating.endpointAvailability(),
                    tick
            );
            runService.recoveryRequired(
                    level.getServer(),
                    run.runIdentity(),
                    com.butchercraft.world.execution.MachineRunResultCode.RECOVERY_REQUIRED,
                    "Grinder Run child admission failed: " + failure.developerExplanation(),
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
                    level.getServer(), run.workstationInstanceIdentity(),
                    com.butchercraft.workstation.operation.MachineOperatingResultCode.RECOVERY_REQUIRED,
                    "Grinder could not publish operating state " + state.serializedName() + ": " + mutation.detail(),
                    mutation.record().map(MachineOperatingRecord::endpointAvailability)
                            .orElse(com.butchercraft.workstation.operation.MachineEndpointAvailability.UNAVAILABLE),
                    tick
            );
        }
    }

    private Optional<MachineRunRecord> activeRun(ServerLevel level, GrinderBlockEntity grinder) {
        if (grinder.endpointProjection().instanceId().isEmpty()) return Optional.empty();
        return runService.snapshot(level.getServer())
                .activeFor(grinder.endpointProjection().instanceId().orElseThrow().value());
    }

    private GrinderRunControlResult coordinationFailure(
            GrinderBlockEntity grinder,
            MachineRunCoordinationResult result
    ) {
        GrinderRunControlCode code = result.code() == MachineRunCoordinationCode.RECOVERY_REQUIRED
                || result.code() == MachineRunCoordinationCode.REPLACEMENT_INSTANCE
                ? GrinderRunControlCode.RECOVERY_REQUIRED
                : result.code() == MachineRunCoordinationCode.CHILD_ALREADY_ACTIVE
                ? GrinderRunControlCode.BUSY
                : GrinderRunControlCode.REJECTED;
        return rejected(code, grinder, result.detail());
    }

    private GrinderRunControlResult rejected(
            GrinderRunControlCode code,
            GrinderBlockEntity grinder,
            String detail
    ) {
        if (grinder.getLevel() instanceof ServerLevel level) {
            GrinderRunStatus status = status(level, grinder);
            return new GrinderRunControlResult(code, status.operatingState(), status.runIdentity(), detail);
        }
        return new GrinderRunControlResult(code, MachineOperatingState.OFF, Optional.empty(), detail);
    }

    private GrinderRunControlResult result(
            GrinderRunControlCode code,
            GrinderBlockEntity grinder,
            Optional<MachineRunRecord> run,
            String detail
    ) {
        MachineOperatingState state = grinder.getLevel() instanceof ServerLevel level
                ? status(level, grinder).operatingState()
                : MachineOperatingState.OFF;
        return new GrinderRunControlResult(code, state, run.map(MachineRunRecord::runIdentity), detail);
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

    private static String eligibilityIdentity(MachineRunRecord run) {
        return "butchercraft:grinder_eligibility/run_" + run.generation()
                + "/child_" + run.nextChildSequence();
    }

    private static String requestIdentity(String action, String targetIdentity, long revision) {
        String suffix = targetIdentity.substring(targetIdentity.indexOf(':') + 1);
        return "butchercraft:grinder_control/" + action + "/" + suffix + "/revision_" + revision;
    }

    private static long tick(ServerLevel level) {
        return SimulationClockService.INSTANCE.clock(level.getServer()).simulationTick();
    }
}
