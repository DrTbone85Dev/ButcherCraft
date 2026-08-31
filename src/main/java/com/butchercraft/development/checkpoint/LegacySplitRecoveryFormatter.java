package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.LegacySplitRecoveryDryRunPreview;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationReport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryStatusSnapshot;
import com.butchercraft.world.checkpoint.PreservedAuthorizedWork;
import com.butchercraft.world.checkpoint.RecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LegacySplitRecoveryFormatter {
    private LegacySplitRecoveryFormatter() {
    }

    public static List<String> previewLines(LegacySplitRecoveryDryRunPreview preview) {
        Objects.requireNonNull(preview, "preview");
        List<String> lines = new ArrayList<>();
        lines.add("Recovery dry run: no mutation performed");
        lines.add("Recovery Identity: " + preview.plan().recoveryIdentity().value());
        lines.add("Analysis digest: " + preview.plan().analysisDigest());
        lines.add("Clock tick: " + preview.plan().authoritativeClockTick());
        lines.add("Scheduler last normally finalized tick: "
                + preview.plan().schedulerLastNormallyFinalizedTick());
        preview.plan().recoveryDiscontinuity().ifPresent(value -> lines.add(
                "Scheduler discontinuity: " + value.inclusiveMissingStartTick()
                        + "-" + value.inclusiveMissingEndTick()
        ));
        preview.plan().nextNormalSchedulerAdmissionTick().ifPresent(value ->
                lines.add("Next Scheduler admission: " + value));
        lines.add("Historical acknowledgements: " + preview.plan().historicalAcknowledgements().size());
        lines.add("Ordinary Work reconstructions: " + preview.plan().ordinaryWorkProofs().stream()
                .filter(value -> value.eligibility()
                        == OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE)
                .count());
        lines.add("Preserved authorized-unscheduled Work: " + preview.plan().preservedAuthorizedWork().size());
        for (PreservedAuthorizedWork work : preview.plan().preservedAuthorizedWork()) {
            lines.add("Preserved child: " + work.childIdentity() + " state=AUTHORIZED scheduled=false");
            lines.add("Policy B Run: " + work.machineRunIdentity() + " state=SUSPENDED_RESTART_REQUIRED");
        }
        lines.add("Planning authority blocks: " + preview.plan().planningAuthorityBlocks().size());
        lines.add("Whole-world mutation blocked: "
                + preview.mutationGate().wholeWorldConsequentialMutationBlocked());
        lines.add("Participant owners: " + preview.participantOwners().size());
        preview.participantOwners().forEach(owner -> lines.add("Participant: " + owner.value()));
        lines.add("Intended generation: "
                + preview.intendedGenerationId().map(Object::toString).orElse("unavailable"));
        lines.add("Publication eligible: " + preview.publicationEligible());
        preview.failures().forEach(failure ->
                lines.add("Blocked " + failure.code() + ": " + failure.detail()));
        return List.copyOf(lines);
    }

    public static List<String> publicationLines(LegacySplitRecoveryPublicationReport report) {
        Objects.requireNonNull(report, "report");
        List<String> lines = new ArrayList<>();
        lines.add("Recovery publication: " + report.outcome());
        report.recoveryResult().ifPresent(result -> addResult(lines, result));
        report.failures().forEach(failure ->
                lines.add("Failure " + failure.code() + ": " + failure.detail()));
        return List.copyOf(lines);
    }

    public static List<String> statusLines(LegacySplitRecoveryStatusSnapshot status) {
        Objects.requireNonNull(status, "status");
        List<String> lines = new ArrayList<>();
        lines.add("Recovery status: " + status.state());
        lines.add("Last Recovery Identity: "
                + status.lastRecoveryIdentity().map(value -> value.value()).orElse("none"));
        lines.add("Committed recovery generation: "
                + status.committedRecoveryGeneration().map(Object::toString).orElse("none"));
        lines.add("World fully mutation-unblocked: " + status.worldFullyMutationUnblocked());
        lines.add("Remaining authority blocks: " + status.remainingAuthorityBlocks().size());
        for (RecoveryAuthorityBlock block : status.remainingAuthorityBlocks()) {
            lines.add("Authority block: " + block.blockIdentity() + " scope=" + block.scope());
        }
        lines.add(status.detail());
        return List.copyOf(lines);
    }

    private static void addResult(List<String> lines, LegacySplitRecoveryResult result) {
        lines.add("Recovery Result: " + result.resultIdentity());
        lines.add("Recovery generation committed: " + result.recoveryGenerationId());
        lines.add("Committed head: " + result.committedHead().identity());
        lines.add("Published acknowledgements: " + result.publishedAcknowledgements().size());
        lines.add("Scheduler discontinuity: " + result.publishedDiscontinuity().inclusiveStartTick()
                + "-" + result.publishedDiscontinuity().inclusiveEndTick());
        lines.add("Remaining authority blocks: " + result.authorityBlocks().size());
        lines.add("World fully mutation-unblocked: "
                + (!result.mutationGate().wholeWorldConsequentialMutationBlocked()
                && result.mutationGate().blockedAuthorityIdentities().isEmpty()));
    }
}
