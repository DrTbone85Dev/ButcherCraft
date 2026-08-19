package com.butchercraft.integration.machine;

import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunLifecycle;

import java.util.Objects;
import java.util.Optional;

public record MachineRunDiagnostics(
        String workstationInstanceIdentity,
        Optional<MachineRunIdentity> runIdentity,
        Optional<MachineRunLifecycle> runLifecycle,
        Optional<MachineOperatingState> operatingState,
        Optional<ExecutionOperationId> activeChildOperation,
        long nextChildSequence,
        long runRevision,
        long operatingRevision,
        String endpointAvailability,
        Optional<String> recoveryDetail
) {
    public MachineRunDiagnostics {
        workstationInstanceIdentity = Objects.requireNonNull(
                workstationInstanceIdentity,
                "workstationInstanceIdentity"
        );
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        runLifecycle = Objects.requireNonNull(runLifecycle, "runLifecycle");
        operatingState = Objects.requireNonNull(operatingState, "operatingState");
        activeChildOperation = Objects.requireNonNull(activeChildOperation, "activeChildOperation");
        endpointAvailability = Objects.requireNonNull(endpointAvailability, "endpointAvailability");
        recoveryDetail = Objects.requireNonNull(recoveryDetail, "recoveryDetail");
    }
}
