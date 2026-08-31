package com.butchercraft.integration.machine.grinder;

import com.butchercraft.integration.machine.MachineRunCoordinationResult;
import com.butchercraft.integration.machine.PoweredMachineRunControlResult;
import com.butchercraft.integration.machine.PoweredMachineRunStatus;
import com.butchercraft.integration.machine.PoweredProcessingMachineRunAdapter;
import com.butchercraft.integration.machine.PoweredProcessingMachineRunService;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderExecutionCoordinator;
import com.butchercraft.machine.grinder.execution.GrinderExecutionPreparation;
import com.butchercraft.workstation.WorkstationExecutionStartResult;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionAuthorization;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.Function;

/** Grinder activation facade over the shared DG-005 powered-processing coordinator. */
public final class GrinderContinuousRunService {
    public static final GrinderContinuousRunService INSTANCE = new GrinderContinuousRunService();

    private final PoweredProcessingMachineRunService<GrinderBlockEntity> delegate =
            new PoweredProcessingMachineRunService<>(new GrinderAdapter());

    private GrinderContinuousRunService() {
    }

    public GrinderRunControlResult start(ServerLevel level, GrinderBlockEntity grinder) {
        return control(delegate.start(level, grinder));
    }

    public GrinderRunControlResult stop(ServerLevel level, GrinderBlockEntity grinder) {
        return control(delegate.stop(level, grinder));
    }

    public GrinderRunControlResult resume(ServerLevel level, GrinderBlockEntity grinder) {
        return control(delegate.resume(level, grinder));
    }

    public GrinderRunControlResult shiftControl(ServerLevel level, GrinderBlockEntity grinder) {
        return control(delegate.shiftControl(level, grinder));
    }

    public void tick(ServerLevel level, GrinderBlockEntity grinder) {
        delegate.tick(level, grinder);
    }

    public void endpointLoaded(ServerLevel level, GrinderBlockEntity grinder) {
        delegate.endpointLoaded(level, grinder);
    }

    public void replacementDetected(ServerLevel level, GrinderBlockEntity grinder) {
        delegate.replacementDetected(level, grinder);
    }

    public boolean hasActiveRun(ServerLevel level, GrinderBlockEntity grinder) {
        return delegate.hasActiveRun(level, grinder);
    }

    public GrinderRunStatus status(ServerLevel level, GrinderBlockEntity grinder) {
        PoweredMachineRunStatus status = delegate.status(level, grinder);
        return new GrinderRunStatus(
                status.operatingState(),
                status.runIdentity(),
                status.runLifecycle(),
                status.activeChild(),
                status.generation(),
                status.completedChildren(),
                status.nextChildSequence(),
                status.runRevision(),
                status.operatingRevision(),
                status.detail()
        );
    }

    private static GrinderRunControlResult control(PoweredMachineRunControlResult result) {
        return new GrinderRunControlResult(
                GrinderRunControlCode.valueOf(result.code().name()),
                result.operatingState(),
                result.runIdentity(),
                result.detail()
        );
    }

    private static final class GrinderAdapter implements PoweredProcessingMachineRunAdapter<GrinderBlockEntity> {
        @Override
        public String displayName() {
            return "Grinder";
        }

        @Override
        public String identityPath() {
            return "grinder";
        }

        @Override
        public String controlOwner() {
            return "butchercraft:player_grinder_control";
        }

        @Override
        public WorkstationEndpointProjection endpointProjection(GrinderBlockEntity grinder) {
            return grinder.endpointProjection();
        }

        @Override
        public boolean hasConflictingReservation(ServerLevel level, GrinderBlockEntity grinder) {
            return WorkstationReservationService.INSTANCE.hasActiveReservationAt(level, grinder.getBlockPos());
        }

        @Override
        public WorkstationProductionRequestResult requestRunProcessing(
                GrinderBlockEntity grinder,
                WorkstationTickContext tickContext,
                List<String> childBindingIdentities,
                Function<ExecutionAuthorization, MachineRunCoordinationResult> admission
        ) {
            return grinder.requestRunProcessing(tickContext, startRequest -> {
                GrinderExecutionPreparation preparation = GrinderExecutionCoordinator.INSTANCE
                        .prepareAuthorization(startRequest, childBindingIdentities);
                MachineRunCoordinationResult admitted = admission.apply(preparation.authorization());
                if (!admitted.accepted() || admitted.childOperation().isEmpty()) {
                    return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                            WorkstationFailureCode.EXECUTION_AUTHORIZATION_REJECTED,
                            admitted.detail()
                    ));
                }
                return GrinderExecutionCoordinator.INSTANCE.acceptedResult(
                        preparation,
                        admitted.childOperation().orElseThrow()
                );
            });
        }
    }
}
