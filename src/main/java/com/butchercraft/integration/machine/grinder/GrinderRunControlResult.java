package com.butchercraft.integration.machine.grinder;

import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.execution.MachineRunIdentity;

import java.util.Objects;
import java.util.Optional;

public record GrinderRunControlResult(
        GrinderRunControlCode code,
        MachineOperatingState operatingState,
        Optional<MachineRunIdentity> runIdentity,
        String detail
) {
    public GrinderRunControlResult {
        code = Objects.requireNonNull(code, "code");
        operatingState = Objects.requireNonNull(operatingState, "operatingState");
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean accepted() {
        return code == GrinderRunControlCode.STARTED
                || code == GrinderRunControlCode.STOP_REQUESTED
                || code == GrinderRunControlCode.STOPPED
                || code == GrinderRunControlCode.RESUMED
                || code == GrinderRunControlCode.EXISTING_RESULT;
    }
}
