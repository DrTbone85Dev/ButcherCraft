package com.butchercraft.world.execution;

import java.util.Objects;

public record MachineStopAuthorizationEvidence(
        int schemaVersion,
        String authorizationIdentity,
        String worldIdentity,
        String workstationInstanceIdentity,
        MachineRunIdentity targetRunIdentity,
        long expectedRunRevision,
        long expectedOperatingRevision,
        String sourceOwner,
        String sourceRequestIdentity,
        String configurationIdentity,
        long issuedSimulationTick,
        String contentDigest
) {
    private static final String PREFIX = "butchercraft:machine_stop_authorization/v1/";

    public MachineStopAuthorizationEvidence {
        if (schemaVersion != MachineRunSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Machine STOP schema version: " + schemaVersion);
        }
        authorizationIdentity = ExecutionValidation.requireId(
                authorizationIdentity,
                "Machine STOP authorization identity"
        );
        if (!authorizationIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Machine STOP authorization identity has an unsupported prefix");
        }
        worldIdentity = ExecutionValidation.requireId(worldIdentity, "Machine STOP World Identity");
        workstationInstanceIdentity = ExecutionValidation.requireId(
                workstationInstanceIdentity,
                "Machine STOP Workstation Instance Identity"
        );
        targetRunIdentity = Objects.requireNonNull(targetRunIdentity, "targetRunIdentity");
        if (expectedRunRevision < 0L || expectedOperatingRevision < 0L) {
            throw new IllegalArgumentException("Expected Machine STOP revisions must not be negative");
        }
        sourceOwner = ExecutionValidation.requireId(sourceOwner, "Machine STOP source owner");
        sourceRequestIdentity = ExecutionValidation.requireId(
                sourceRequestIdentity,
                "Machine STOP source request identity"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine STOP configuration identity"
        );
        issuedSimulationTick = ExecutionValidation.requireTick(issuedSimulationTick, "Machine STOP issue tick");
        contentDigest = ExecutionValidation.requireDigest(contentDigest, "Machine STOP content digest");
        if (!contentDigest.equals(calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                targetRunIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                configurationIdentity,
                issuedSimulationTick
        ))) {
            throw new IllegalArgumentException("Machine STOP content digest mismatch");
        }
        if (!authorizationIdentity.equals(PREFIX + ExecutionValidation.digestIdSuffix(contentDigest))) {
            throw new IllegalArgumentException("Machine STOP authorization identity is not canonical");
        }
    }

    public static MachineStopAuthorizationEvidence issued(
            String worldIdentity,
            String workstationInstanceIdentity,
            MachineRunIdentity targetRunIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String configurationIdentity,
            long issuedSimulationTick
    ) {
        String digest = calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                targetRunIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                configurationIdentity,
                issuedSimulationTick
        );
        return new MachineStopAuthorizationEvidence(
                MachineRunSchema.CURRENT_VERSION,
                PREFIX + ExecutionValidation.digestIdSuffix(digest),
                worldIdentity,
                workstationInstanceIdentity,
                targetRunIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                configurationIdentity,
                issuedSimulationTick,
                digest
        );
    }

    public String requestKey() {
        return sourceOwner + "\n" + sourceRequestIdentity;
    }

    public boolean sameIntent(MachineStopAuthorizationEvidence other) {
        Objects.requireNonNull(other, "other");
        return workstationInstanceIdentity.equals(other.workstationInstanceIdentity)
                && targetRunIdentity.equals(other.targetRunIdentity)
                && expectedRunRevision == other.expectedRunRevision
                && expectedOperatingRevision == other.expectedOperatingRevision
                && worldIdentity.equals(other.worldIdentity)
                && configurationIdentity.equals(other.configurationIdentity);
    }

    public String calculateContentDigest() {
        return calculateContentDigest(
                worldIdentity,
                workstationInstanceIdentity,
                targetRunIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                configurationIdentity,
                issuedSimulationTick
        );
    }

    private static String calculateContentDigest(
            String worldIdentity,
            String workstationInstanceIdentity,
            MachineRunIdentity targetRunIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String configurationIdentity,
            long issuedSimulationTick
    ) {
        return ExecutionCanonicalDigest.create("butchercraft:machine_stop_authorization")
                .add(MachineRunSchema.CURRENT_VERSION)
                .add(worldIdentity)
                .add(workstationInstanceIdentity)
                .add(targetRunIdentity.value())
                .add(expectedRunRevision)
                .add(expectedOperatingRevision)
                .add(sourceOwner)
                .add(sourceRequestIdentity)
                .add(configurationIdentity)
                .add(issuedSimulationTick)
                .finish();
    }
}
