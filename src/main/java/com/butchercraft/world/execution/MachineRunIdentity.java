package com.butchercraft.world.execution;

import java.util.Objects;

public record MachineRunIdentity(String value) implements Comparable<MachineRunIdentity> {
    private static final String PREFIX = "butchercraft:machine_run/v1/";

    public MachineRunIdentity {
        value = ExecutionValidation.requireId(value, "Machine Run Identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Machine Run Identity has an unsupported prefix");
        }
    }

    public static MachineRunIdentity create(
            String worldIdentity,
            String workstationInstanceIdentity,
            long generation,
            String startAuthorizationIdentity,
            String operatingPolicyIdentity,
            String configurationIdentity
    ) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("Machine Run generation must be positive");
        }
        String digest = ExecutionCanonicalDigest.create("butchercraft:machine_run")
                .add(MachineRunSchema.CURRENT_VERSION)
                .add(ExecutionValidation.requireId(worldIdentity, "Machine Run World Identity"))
                .add(ExecutionValidation.requireId(
                        workstationInstanceIdentity,
                        "Machine Run Workstation Instance Identity"
                ))
                .add(generation)
                .add(ExecutionValidation.requireId(
                        startAuthorizationIdentity,
                        "Machine START authorization identity"
                ))
                .add(ExecutionValidation.requireId(operatingPolicyIdentity, "Machine operating policy identity"))
                .add(ExecutionValidation.requireId(configurationIdentity, "Machine Run configuration identity"))
                .finish();
        return new MachineRunIdentity(PREFIX + ExecutionValidation.digestIdSuffix(digest));
    }

    @Override
    public int compareTo(MachineRunIdentity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
