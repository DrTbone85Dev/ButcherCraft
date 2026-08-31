package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

/** Exact authorized-but-unscheduled owner reference preserved without admission or replacement. */
public record PreservedAuthorizedWork(
        String executionOperationIdentity,
        String machineRunIdentity,
        long machineRunGeneration,
        String childIdentity,
        long childSequence,
        String workstationInstanceIdentity,
        long workstationInstanceGeneration,
        String authorizationContentDigest,
        List<String> sourceEvidenceIdentities,
        String restartPolicyIdentity
) implements Comparable<PreservedAuthorizedWork> {
    public static final String POLICY_B = "butchercraft:machine_restart_policy/b";

    public PreservedAuthorizedWork {
        executionOperationIdentity = CheckpointValidation.id(
                executionOperationIdentity,
                "preservedExecutionOperationIdentity"
        );
        machineRunIdentity = CheckpointValidation.id(machineRunIdentity, "preservedMachineRunIdentity");
        machineRunGeneration = CheckpointValidation.positive(machineRunGeneration, "machineRunGeneration");
        childIdentity = CheckpointValidation.id(childIdentity, "preservedChildIdentity");
        childSequence = CheckpointValidation.positive(childSequence, "childSequence");
        workstationInstanceIdentity = CheckpointValidation.id(
                workstationInstanceIdentity,
                "preservedWorkstationInstanceIdentity"
        );
        workstationInstanceGeneration = CheckpointValidation.positive(
                workstationInstanceGeneration,
                "workstationInstanceGeneration"
        );
        authorizationContentDigest = CheckpointValidation.digest(
                authorizationContentDigest,
                "preservedAuthorizationContentDigest"
        );
        sourceEvidenceIdentities = Objects.requireNonNull(
                sourceEvidenceIdentities,
                "sourceEvidenceIdentities"
        ).stream().map(value -> CheckpointValidation.id(value, "sourceEvidenceIdentity"))
                .distinct().sorted().toList();
        if (sourceEvidenceIdentities.isEmpty()) {
            throw new IllegalArgumentException("Preserved authorized Work requires source evidence");
        }
        restartPolicyIdentity = CheckpointValidation.id(restartPolicyIdentity, "restartPolicyIdentity");
        if (!POLICY_B.equals(restartPolicyIdentity)) {
            throw new IllegalArgumentException("Authorized-unscheduled Work requires restart Policy B");
        }
    }

    public boolean terminal() {
        return false;
    }

    public boolean schedulerInvocationStarted() {
        return false;
    }

    public boolean automaticallyScheduled() {
        return false;
    }

    @Override
    public int compareTo(PreservedAuthorizedWork other) {
        Objects.requireNonNull(other, "other");
        int runComparison = machineRunIdentity.compareTo(other.machineRunIdentity);
        return runComparison != 0 ? runComparison : Long.compare(childSequence, other.childSequence);
    }
}
