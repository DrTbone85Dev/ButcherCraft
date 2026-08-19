package com.butchercraft.integration.machine;

import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.MachineRunRecord;

import java.util.Objects;
import java.util.Optional;

public record MachineRunCoordinationResult(
        MachineRunCoordinationCode code,
        Optional<MachineRunRecord> run,
        Optional<MachineOperatingRecord> operatingState,
        Optional<ExecutionOperationSnapshot> childOperation,
        String detail
) {
    public MachineRunCoordinationResult {
        code = Objects.requireNonNull(code, "code");
        run = Objects.requireNonNull(run, "run");
        operatingState = Objects.requireNonNull(operatingState, "operatingState");
        childOperation = Objects.requireNonNull(childOperation, "childOperation");
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.isBlank()) throw new IllegalArgumentException("Machine Run coordination detail must not be blank");
    }

    public boolean accepted() {
        return code == MachineRunCoordinationCode.ACCEPTED
                || code == MachineRunCoordinationCode.EXISTING_RESULT;
    }
}
