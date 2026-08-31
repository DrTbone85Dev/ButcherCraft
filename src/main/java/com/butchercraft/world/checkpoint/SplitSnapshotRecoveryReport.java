package com.butchercraft.world.checkpoint;

import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SplitSnapshotRecoveryReport {
    private SplitSnapshotRecoveryReport() {
    }

    public static List<String> lines(SplitSnapshotRecoveryPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<String> lines = new ArrayList<>();
        lines.add("Legacy split-snapshot recovery analysis");
        lines.add("World Identity: " + plan.worldIdentityRoot().identity());
        lines.add("Clock tick: " + plan.authoritativeClockTick());
        lines.add("Scheduler last normally finalized tick: " + plan.schedulerLastNormallyFinalizedTick());
        plan.recoveryDiscontinuity().ifPresent(discontinuity -> lines.add(
                "Recovery discontinuity: " + discontinuity.inclusiveMissingStartTick()
                        + "-" + discontinuity.inclusiveMissingEndTick()
        ));
        plan.nextNormalSchedulerAdmissionTick().ifPresent(value ->
                lines.add("Next normal Scheduler admission: " + value));
        lines.add("Historical acknowledgements: " + plan.historicalAcknowledgements().size());
        plan.historicalAcknowledgements().forEach(acknowledgement -> lines.add(
                "Acknowledgement: " + acknowledgement.acknowledgementIdentity()
                        + " operation=" + acknowledgement.executionOperationIdentity()
                        + " outcome=" + acknowledgement.terminalOutcome().name()
                        + " owner-result=" + acknowledgement.ownerResultIdentity().orElse("none")
        ));
        long reconstructable = plan.ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility()
                        == OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE)
                .count();
        lines.add("Ordinary Work reconstructable: " + reconstructable);
        plan.ordinaryWorkProofs().forEach(proof -> lines.add(
                "Ordinary Work candidate: " + proof.candidateIdentity()
                        + " eligibility=" + proof.eligibility().name()
        ));
        lines.add("Authorized-unscheduled preserved: " + plan.preservedAuthorizedWork().size());
        plan.preservedAuthorizedWork().forEach(work -> lines.add(
                "Preserved authorized Work: " + work.executionOperationIdentity()
                        + " run=" + work.machineRunIdentity()
                        + " child=" + work.childSequence()
                        + " workstation=" + work.workstationInstanceIdentity()
                        + " scheduled=false terminal=false"
        ));
        lines.add("Unresolved authority blocks: " + plan.authorityBlocks().size());
        plan.authorityBlocks().forEach(block -> lines.add(
                "Blocked authority: " + block.ownerId().value() + " [" + block.scope().name().toLowerCase(Locale.ROOT)
                        + "] " + block.reasonIdentity()
        ));
        plan.materialHandlingReferences().forEach(reference -> lines.add(
                "Material Handling custody: " + reference.transferIdentity()
                        + " status=" + reference.status().name()
                        + " custody=" + reference.exactCustodyContentDigest()
        ));
        plan.legacyTempFindings().forEach(finding -> lines.add(
                "Temp artifact: " + finding.ownerId().value()
                        + " file=" + finding.targetFileName()
                        + " classification=" + finding.classification().name()
                        + " authoritative=false"
        ));
        lines.add("Coherent checkpoint available: " + plan.coherentCheckpointAvailable());
        lines.add("Recovery Identity: " + plan.recoveryIdentity().value());
        lines.add("Analysis digest: " + plan.analysisDigest());
        lines.add("Eligibility: " + plan.eligibility().name());
        lines.add("Operator authorization required: " + plan.operatorAuthorizationRequired());
        lines.add("Historical execution: none");
        lines.add("Recovery publication: not performed");
        plan.issues().forEach(issue -> lines.add(
                "Issue " + issue.code().name() + ": " + issue.referenceIdentity() + " - " + issue.detail()
        ));
        return List.copyOf(lines);
    }
}
