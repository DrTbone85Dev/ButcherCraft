package com.butchercraft.world.execution;

import java.util.Objects;
import java.util.Optional;

public record MachineRunRegistryMutation(
        MachineRunResultCode code,
        MachineRunRegistry registry,
        Optional<MachineRunRecord> run,
        boolean changed,
        String detail
) {
    public MachineRunRegistryMutation {
        code = Objects.requireNonNull(code, "code");
        registry = Objects.requireNonNull(registry, "registry");
        run = Objects.requireNonNull(run, "run");
        detail = ExecutionValidation.requireText(detail, "Machine Run mutation detail", 2_048);
    }

    public boolean accepted() {
        return code == MachineRunResultCode.ACCEPTED || code == MachineRunResultCode.EXISTING_RUN;
    }
}
