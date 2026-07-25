package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointCoordinatedRestorationReport;
import com.butchercraft.world.checkpoint.CheckpointFailure;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryReport;
import com.butchercraft.world.checkpoint.CheckpointHeadRecord;
import com.butchercraft.world.checkpoint.CheckpointPublicationReport;
import com.butchercraft.world.checkpoint.CheckpointStorageArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DevelopmentCheckpointFormatter {
    private DevelopmentCheckpointFormatter() {
    }

    public static List<String> lines(DevelopmentCheckpointReport report) {
        Objects.requireNonNull(report, "report");
        List<String> lines = new ArrayList<>();
        lines.add("ButcherCraft development checkpoint " + report.operation().name().toLowerCase()
                + ": " + (report.successful() ? "successful" : "blocked or failed"));
        report.checkpointRoot().ifPresent(root -> lines.add("Checkpoint root: " + root));
        report.publicationReport().ifPresent(publication -> addPublication(lines, publication));
        report.recoveryReport().ifPresent(recovery -> addRecovery(lines, recovery));
        report.restorationReport().ifPresent(restoration -> addRestoration(lines, restoration));
        report.selectedGeneration().ifPresent(generation -> addGeneration(lines, "Selected generation", generation));
        if (!report.generations().isEmpty()) {
            lines.add("Committed generations:");
            for (DevelopmentCheckpointGenerationSummary generation : report.generations()) {
                lines.add("- " + generation.generationId().canonicalValue()
                        + " tick=" + generation.authoritativeSimulationTick()
                        + " owners=" + generation.owners().size()
                        + " manifest=" + generation.manifestDigest());
            }
        }
        if (!report.heads().isEmpty()) {
            lines.add("Head records:");
            for (CheckpointHeadRecord head : report.heads()) {
                lines.add("- sequence=" + head.headSequence()
                        + " selected=" + head.selectedGenerationId().canonicalValue()
                        + " digestValid=" + head.digestMatches());
            }
        }
        if (!report.artifacts().isEmpty()) {
            lines.add("Storage artifacts:");
            for (CheckpointStorageArtifact artifact : report.artifacts()) {
                lines.add("- " + artifact.kind() + " " + artifact.path()
                        + " " + artifact.failure().code());
            }
        }
        if (!report.failures().isEmpty()) {
            lines.add("Development failures:");
            for (DevelopmentCheckpointFailure failure : report.failures()) {
                lines.add("- " + failure.code() + " " + failure.field() + ": " + failure.message());
            }
        }
        if (!report.checkpointFailures().isEmpty()) {
            lines.add("Checkpoint diagnostics:");
            for (CheckpointFailure failure : report.checkpointFailures()) {
                lines.add("- " + failure.code() + " " + failure.field() + ": " + failure.message());
            }
        }
        if (!report.warnings().isEmpty()) {
            lines.add("Warnings:");
            report.warnings().forEach(warning -> lines.add("- " + warning));
        }
        return lines;
    }

    private static void addPublication(List<String> lines, CheckpointPublicationReport publication) {
        lines.add("Publication outcome: " + publication.outcome());
        publication.generationManifest().ifPresent(manifest -> {
            lines.add("Published generation: " + manifest.generationId().canonicalValue());
            lines.add("Published tick: " + manifest.authoritativeSimulationTick());
            lines.add("Published manifest digest: " + manifest.manifestDigest());
        });
        publication.headRecord().ifPresent(head -> lines.add("Head publication sequence: " + head.headSequence()));
    }

    private static void addRecovery(List<String> lines, CheckpointFilesystemRecoveryReport recovery) {
        lines.add("Recovery selection: " + recovery.selection().outcome());
        recovery.selection().selectedGenerationId()
                .ifPresent(id -> lines.add("Recovery selected generation: " + id.canonicalValue()));
    }

    private static void addRestoration(List<String> lines, CheckpointCoordinatedRestorationReport restoration) {
        lines.add("Restoration outcome: " + restoration.outcome());
        if (!restoration.preparedOwners().isEmpty()) {
            lines.add("Prepared owners: " + restoration.preparedOwners());
        }
        if (!restoration.publishedOwners().isEmpty()) {
            lines.add("Published owners: " + restoration.publishedOwners());
        }
    }

    private static void addGeneration(
            List<String> lines,
            String label,
            DevelopmentCheckpointGenerationSummary generation
    ) {
        lines.add(label + ": " + generation.generationId().canonicalValue());
        lines.add(label + " predecessor: " + generation.predecessorGenerationId()
                .map(Object::toString)
                .orElse("none"));
        lines.add(label + " tick: " + generation.authoritativeSimulationTick());
        lines.add(label + " manifest digest: " + generation.manifestDigest());
        lines.add(label + " World Identity: " + generation.worldIdentityRootIdentity());
        lines.add(label + " Platform Determinism Manifest: "
                + generation.platformDeterminismManifestIdentity());
        lines.add(label + " owners:");
        for (DevelopmentCheckpointOwnerSummary owner : generation.owners()) {
            lines.add("- " + owner.ownerId()
                    + " snapshot=" + owner.snapshotIdentity()
                    + " config=" + owner.configurationIdentity());
        }
    }
}
