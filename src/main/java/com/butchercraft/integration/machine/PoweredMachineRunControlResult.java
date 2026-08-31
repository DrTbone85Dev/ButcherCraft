package com.butchercraft.integration.machine;

import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.execution.MachineRunIdentity;

import java.util.Objects;
import java.util.Optional;

public record PoweredMachineRunControlResult(
        PoweredMachineRunControlCode code,
        MachineOperatingState operatingState,
        Optional<MachineRunIdentity> runIdentity,
        String detail
) {
    public PoweredMachineRunControlResult {
        code = Objects.requireNonNull(code, "code");
        operatingState = Objects.requireNonNull(operatingState, "operatingState");
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean accepted() {
        return code == PoweredMachineRunControlCode.STARTED
                || code == PoweredMachineRunControlCode.STOP_REQUESTED
                || code == PoweredMachineRunControlCode.STOPPED
                || code == PoweredMachineRunControlCode.RESUMED
                || code == PoweredMachineRunControlCode.EXISTING_RESULT;
    }
}
