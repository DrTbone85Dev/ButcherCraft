package com.butchercraft.world.execution;

import java.util.Objects;

public record MachineStartAuthorizationEvidence(
        int schemaVersion,
        String authorizationIdentity,
        String worldIdentity,
        String workstationInstanceIdentity,
        long expectedOperatingRevision,
        String sourceOwner,
        String sourceRequestIdentity,
        String operatingPolicyIdentity,
        String configurationIdentity,
        long issuedSimulationTick,
        String contentDigest
) {
    private static final String PREFIX = "butchercraft:machine_start_authorization/v1/";

    public MachineStartAuthorizationEvidence {
        if (schemaVersion != MachineRunSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Machine START schema version: " + schemaVersion);
        }
        authorizationIdentity = ExecutionValidation.requireId(
                authorizationIdentity,
                "Machine START authorization identity"
        );
        if (!authorizationIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Machine START authorization identity has an unsupported prefix");
        }
        worldIdentity = ExecutionValidation.requireId(worldIdentity, "Machine START World Identity");
        workstationInstanceIdentity = ExecutionValidation.requireId(
                workstationInstanceIdentity,
                "Machine START Workstation Instance Identity"
        );
        if (expectedOperatingRevision < 0L) {
            throw new IllegalArgumentException("Expected machine operating revision must not be negative");
        }
        sourceOwner = ExecutionValidation.requireId(sourceOwner, "Machine START source owner");
        sourceRequestIdentity = ExecutionValidation.requireId(
                sourceRequestIdentity,
                "Machine START source request identity"
        );
        operatingPolicyIdentity = ExecutionValidation.requireId(
                operatingPolicyIdentity,
                "Machine operating policy identity"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine START configuration identity"
        );
        issuedSimulationTick = ExecutionValidation.requireTick(
                issuedSimulationTick,
                "Machine START issue tick"
        );
        contentDigest = ExecutionValidation.requireDigest(contentDigest, "Machine START content digest");
        if (!contentDigest.equals(calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                operatingPolicyIdentity,
                configurationIdentity,
                issuedSimulationTick
        ))) {
            throw new IllegalArgumentException("Machine START content digest mismatch");
        }
        String expectedIdentity = PREFIX + ExecutionValidation.digestIdSuffix(contentDigest);
        if (!authorizationIdentity.equals(expectedIdentity)) {
            throw new IllegalArgumentException("Machine START authorization identity is not canonical");
        }
    }

    public static MachineStartAuthorizationEvidence issued(
            String worldIdentity,
            String workstationInstanceIdentity,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String operatingPolicyIdentity,
            String configurationIdentity,
            long issuedSimulationTick
    ) {
        String digest = calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                operatingPolicyIdentity,
                configurationIdentity,
                issuedSimulationTick
        );
        return new MachineStartAuthorizationEvidence(
                MachineRunSchema.CURRENT_VERSION,
                PREFIX + ExecutionValidation.digestIdSuffix(digest),
                worldIdentity,
                workstationInstanceIdentity,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                operatingPolicyIdentity,
                configurationIdentity,
                issuedSimulationTick,
                digest
        );
    }

    public String requestKey() {
        return sourceOwner + "\n" + sourceRequestIdentity;
    }

    public boolean sameIntent(MachineStartAuthorizationEvidence other) {
        Objects.requireNonNull(other, "other");
        return workstationInstanceIdentity.equals(other.workstationInstanceIdentity)
                && expectedOperatingRevision == other.expectedOperatingRevision
                && operatingPolicyIdentity.equals(other.operatingPolicyIdentity)
                && worldIdentity.equals(other.worldIdentity)
                && configurationIdentity.equals(other.configurationIdentity);
    }

    public String calculateContentDigest() {
        return calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                operatingPolicyIdentity,
                configurationIdentity,
                issuedSimulationTick
        );
    }

    private static String calculateContentDigest(
            String worldIdentity,
            String workstationInstanceIdentity,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String operatingPolicyIdentity,
            String configurationIdentity,
            long issuedSimulationTick
    ) {
        return ExecutionCanonicalDigest.create("butchercraft:machine_start_authorization")
                .add(MachineRunSchema.CURRENT_VERSION)
                .add(worldIdentity)
                .add(workstationInstanceIdentity)
                .add(expectedOperatingRevision)
                .add(sourceOwner)
                .add(sourceRequestIdentity)
                .add(operatingPolicyIdentity)
                .add(configurationIdentity)
                .add(issuedSimulationTick)
                .finish();
    }
}
