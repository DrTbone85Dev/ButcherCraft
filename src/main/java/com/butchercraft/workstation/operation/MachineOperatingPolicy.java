package com.butchercraft.workstation.operation;

import java.util.Objects;

public record MachineOperatingPolicy(
        int schemaVersion,
        MachineOperatingPolicyKind kind,
        String configurationIdentity,
        String policyIdentity
) {
    private static final String PREFIX = "butchercraft:machine_operating_policy/v1/";

    public MachineOperatingPolicy {
        if (schemaVersion != MachineOperatingSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported machine operating policy schema: " + schemaVersion);
        }
        kind = Objects.requireNonNull(kind, "kind");
        configurationIdentity = MachineOperatingValidation.id(
                configurationIdentity,
                "Machine operating policy configuration identity"
        );
        policyIdentity = MachineOperatingValidation.id(policyIdentity, "Machine operating policy identity");
        String expected = PREFIX + MachineOperatingDigest.suffix(calculateDigest(kind, configurationIdentity));
        if (!policyIdentity.equals(expected)) {
            throw new IllegalArgumentException("Machine operating policy identity is not canonical");
        }
    }

    public static MachineOperatingPolicy of(MachineOperatingPolicyKind kind, String configurationIdentity) {
        String digest = calculateDigest(kind, configurationIdentity);
        return new MachineOperatingPolicy(
                MachineOperatingSchema.CURRENT_VERSION,
                kind,
                configurationIdentity,
                PREFIX + MachineOperatingDigest.suffix(digest)
        );
    }

    public static MachineOperatingPolicy manualDiscrete() {
        return of(MachineOperatingPolicyKind.MANUAL_DISCRETE,
                "butchercraft:machine_operating_policy_configuration/schema_1");
    }

    public static MachineOperatingPolicy poweredOneCycle() {
        return of(MachineOperatingPolicyKind.POWERED_ONE_CYCLE,
                "butchercraft:machine_operating_policy_configuration/schema_1");
    }

    public static MachineOperatingPolicy poweredContinuousExplicitStop() {
        return of(MachineOperatingPolicyKind.POWERED_CONTINUOUS_EXPLICIT_STOP,
                "butchercraft:machine_operating_policy_configuration/schema_1");
    }

    private static String calculateDigest(MachineOperatingPolicyKind kind, String configurationIdentity) {
        return MachineOperatingDigest.create("butchercraft:machine_operating_policy")
                .add(MachineOperatingSchema.CURRENT_VERSION)
                .add(Objects.requireNonNull(kind, "kind").serializedName())
                .add(configurationIdentity)
                .add(kind.supportsPersistentRun())
                .add(kind == MachineOperatingPolicyKind.POWERED_CONTINUOUS_EXPLICIT_STOP)
                .finish();
    }
}
