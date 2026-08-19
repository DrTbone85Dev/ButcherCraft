package com.butchercraft.integration.machine.grinder;

import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;

import java.util.Objects;
import java.util.Optional;

public record GrinderRunStatus(
        MachineOperatingState operatingState,
        Optional<MachineRunIdentity> runIdentity,
        Optional<MachineRunLifecycle> runLifecycle,
        Optional<ExecutionOperationId> activeChild,
        long generation,
        long completedChildren,
        long nextChildSequence,
        long runRevision,
        long operatingRevision,
        Optional<String> detail
) {
    public GrinderRunStatus {
        operatingState = Objects.requireNonNull(operatingState, "operatingState");
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        runLifecycle = Objects.requireNonNull(runLifecycle, "runLifecycle");
        activeChild = Objects.requireNonNull(activeChild, "activeChild");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static GrinderRunStatus off() {
        return new GrinderRunStatus(
                MachineOperatingState.OFF,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                0L,
                1L,
                0L,
                0L,
                Optional.empty()
        );
    }
}
