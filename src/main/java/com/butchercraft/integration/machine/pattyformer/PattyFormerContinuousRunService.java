package com.butchercraft.integration.machine.pattyformer;

import com.butchercraft.integration.machine.MachineRunCoordinationResult;
import com.butchercraft.integration.machine.PoweredMachineRunControlResult;
import com.butchercraft.integration.machine.PoweredMachineRunStatus;
import com.butchercraft.integration.machine.PoweredProcessingMachineRunAdapter;
import com.butchercraft.integration.machine.PoweredProcessingMachineRunService;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerExecutionCoordinator;
import com.butchercraft.machine.pattyformer.execution.PattyFormerExecutionPreparation;
import com.butchercraft.workstation.WorkstationExecutionStartResult;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.world.execution.ExecutionAuthorization;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.Function;

/** Patty Former activation facade over the shared DG-005 powered-processing coordinator. */
public final class PattyFormerContinuousRunService {
    public static final PattyFormerContinuousRunService INSTANCE = new PattyFormerContinuousRunService();

    private final PoweredProcessingMachineRunService<PattyFormerBlockEntity> delegate =
            new PoweredProcessingMachineRunService<>(new PattyFormerAdapter());

    private PattyFormerContinuousRunService() {
    }

    public PoweredMachineRunControlResult start(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.start(level, pattyFormer);
    }

    public PoweredMachineRunControlResult stop(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.stop(level, pattyFormer);
    }

    public PoweredMachineRunControlResult resume(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.resume(level, pattyFormer);
    }

    public PoweredMachineRunControlResult shiftControl(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.shiftControl(level, pattyFormer);
    }

    public void tick(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        delegate.tick(level, pattyFormer);
    }

    public void endpointLoaded(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        delegate.endpointLoaded(level, pattyFormer);
    }

    public void replacementDetected(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        delegate.replacementDetected(level, pattyFormer);
    }

    public boolean hasActiveRun(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.hasActiveRun(level, pattyFormer);
    }

    public PoweredMachineRunStatus status(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
        return delegate.status(level, pattyFormer);
    }

    private static final class PattyFormerAdapter
            implements PoweredProcessingMachineRunAdapter<PattyFormerBlockEntity> {
        @Override
        public String displayName() {
            return "Patty Former";
        }

        @Override
        public String identityPath() {
            return "patty_former";
        }

        @Override
        public String controlOwner() {
            return "butchercraft:player_patty_former_control";
        }

        @Override
        public WorkstationEndpointProjection endpointProjection(PattyFormerBlockEntity pattyFormer) {
            return pattyFormer.endpointProjection();
        }

        @Override
        public boolean hasConflictingReservation(ServerLevel level, PattyFormerBlockEntity pattyFormer) {
            // IM-029 leaves the delivering employee reserved here; player Run authority remains explicit and separate.
            return false;
        }

        @Override
        public WorkstationProductionRequestResult requestRunProcessing(
                PattyFormerBlockEntity pattyFormer,
                WorkstationTickContext tickContext,
                List<String> childBindingIdentities,
                Function<ExecutionAuthorization, MachineRunCoordinationResult> admission
        ) {
            return pattyFormer.requestRunProcessing(tickContext, startRequest -> {
                PattyFormerExecutionPreparation preparation = PattyFormerExecutionCoordinator.INSTANCE
                        .prepareAuthorization(startRequest, childBindingIdentities);
                MachineRunCoordinationResult admitted = admission.apply(preparation.authorization());
                if (!admitted.accepted() || admitted.childOperation().isEmpty()) {
                    return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                            WorkstationFailureCode.EXECUTION_AUTHORIZATION_REJECTED,
                            admitted.detail()
                    ));
                }
                return PattyFormerExecutionCoordinator.INSTANCE.acceptedResult(
                        preparation,
                        admitted.childOperation().orElseThrow()
                );
            });
        }
    }
}
