package com.butchercraft.machine.pattyformer;

import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Objects;

public record PattyFormerOperationDiagnostics(
        boolean inputPresent,
        boolean recipeValid,
        boolean ready,
        boolean operationRequested,
        String activeExecutionState,
        String schedulerWorkState,
        boolean processing,
        int elapsedTicks,
        int totalTicks,
        boolean outputBlocked,
        boolean completed,
        String workstationState,
        String failureOrRecoveryState
) {
    public PattyFormerOperationDiagnostics {
        activeExecutionState = requireText(activeExecutionState, "active execution state");
        schedulerWorkState = requireText(schedulerWorkState, "scheduler work state");
        workstationState = requireText(workstationState, "workstation state");
        failureOrRecoveryState = requireText(failureOrRecoveryState, "failure or recovery state");
    }

    public static PattyFormerOperationDiagnostics observe(
            ServerLevel level,
            PattyFormerBlockEntity pattyFormer
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pattyFormer, "pattyFormer");
        var snapshot = pattyFormer.productionSnapshot();
        boolean inputPresent = !pattyFormer.inventory().input().isEmpty();
        boolean recipeValid = inputPresent && new WorkstationOperationResolver().resolve(
                level.registryAccess(),
                PattyFormerWorkstation.capability(),
                pattyFormer.inventory().input()
        ).succeeded();
        String executionState = snapshot.activeExecutionOperationId()
                .flatMap(operationId -> ExecutionService.INSTANCE.managerFor(level.getServer()).find(operationId))
                .map(operation -> operation.status().serializedName())
                .orElse("none");
        String schedulerState = snapshot.activeExecutionOperationId()
                .flatMap(operationId -> SimulationSchedulerService.INSTANCE.managerFor(level.getServer())
                        .runtimeFor(SimulationWorkId.of(operationId.value() + "/work")))
                .map(runtime -> runtime.status().serializedName())
                .orElse("none");
        boolean outputBlocked = pattyFormer.lastFailure()
                .map(failure -> failure.code() == WorkstationFailureCode.OUTPUT_OCCUPIED)
                .orElse(false);
        WorkstationState state = pattyFormer.workstationState();
        return new PattyFormerOperationDiagnostics(
                inputPresent,
                recipeValid,
                state == WorkstationState.READY,
                snapshot.activeExecutionOperationId().isPresent(),
                executionState,
                schedulerState,
                state == WorkstationState.PROCESSING,
                pattyFormer.menuData().get(1),
                pattyFormer.menuData().get(2),
                outputBlocked,
                state == WorkstationState.COMPLETE,
                state.name().toLowerCase(Locale.ROOT),
                pattyFormer.lastFailure()
                        .map(failure -> failure.code().reasonCode())
                        .orElse(state == WorkstationState.ERROR ? "error" : "none")
        );
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
