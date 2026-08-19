package com.butchercraft.workstation.operation;

import com.butchercraft.world.execution.MachineStartAuthorizationEvidence;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;

import java.util.Objects;
import java.util.Optional;

public record MachineOperatingMutation(
        MachineOperatingResultCode code,
        MachineOperatingRegistry registry,
        Optional<MachineOperatingRecord> record,
        Optional<MachineStartAuthorizationEvidence> startEvidence,
        Optional<MachineStopAuthorizationEvidence> stopEvidence,
        boolean changed,
        String detail
) {
    public MachineOperatingMutation {
        code = Objects.requireNonNull(code, "code");
        registry = Objects.requireNonNull(registry, "registry");
        record = Objects.requireNonNull(record, "record");
        startEvidence = Objects.requireNonNull(startEvidence, "startEvidence");
        stopEvidence = Objects.requireNonNull(stopEvidence, "stopEvidence");
        detail = MachineOperatingValidation.text(detail, "Machine operating mutation detail");
    }

    public boolean accepted() {
        return code == MachineOperatingResultCode.ACCEPTED
                || code == MachineOperatingResultCode.EXISTING_STATE;
    }
}
