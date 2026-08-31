package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointHeadRecord;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Applies the owner-neutral checkpoint checks before the Workstation-owned restorability gate. */
public final class StartupCheckpointCandidateSelector {
    public StartupCheckpointCandidateSelection select(
            CheckpointFilesystemStore store,
            CheckpointFilesystemRecoveryRequest request
    ) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(request, "request");
        List<String> rejected = new ArrayList<>();
        Set<CheckpointGenerationId> inspected = new HashSet<>();
        boolean workstationRejected = false;
        List<CheckpointHeadRecord> heads = store.inspectReadOnly(request).headRecords().stream()
                .filter(CheckpointHeadRecord::digestMatches)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (CheckpointHeadRecord head : heads) {
            if (!inspected.add(head.selectedGenerationId())) continue;
            var recovered = store.loadCommittedGenerationReadOnly(
                    request,
                    head.selectedGenerationId(),
                    head.selectedGenerationManifestDigest()
            );
            if (!recovered.successful()) {
                rejected.add(head.selectedGenerationId().canonicalValue()
                        + " rejected by checkpoint integrity/compatibility validation");
                continue;
            }
            var generation = recovered.recoveredGeneration().orElseThrow();
            var workstation = WorkstationCheckpointRestorabilityVerifier.verify(generation);
            if (!workstation.restorable()) {
                workstationRejected = true;
                rejected.add(generation.manifest().generationId().canonicalValue()
                        + " rejected by Workstation restorability: " + workstation.status()
                        + (workstation.blockers().isEmpty()
                        ? "" : " - " + String.join(", ", workstation.blockers())));
                continue;
            }
            CheckpointHeadRecord exactHead = recovered.filesystemRecoveryReport().headRecords().stream()
                    .filter(CheckpointHeadRecord::digestMatches)
                    .filter(value -> value.selectedGenerationId().equals(generation.manifest().generationId()))
                    .filter(value -> value.selectedGenerationManifestDigest()
                            .equals(generation.manifest().manifestDigest()))
                    .max(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalStateException(
                            "Restorable checkpoint generation has no authoritative head"));
            return new StartupCheckpointCandidateSelection(
                    Optional.of(generation),
                    Optional.of(exactHead),
                    Optional.of(workstation),
                    Optional.of(recovered),
                    previousValidGeneration(store, request, generation),
                    rejected,
                    StartupRecoveryFailureCode.NO_VALID_CHECKPOINT
            );
        }
        return new StartupCheckpointCandidateSelection(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), rejected,
                workstationRejected
                        ? StartupRecoveryFailureCode.WORKSTATION_PROJECTION_INCOMPLETE
                        : StartupRecoveryFailureCode.NO_VALID_CHECKPOINT
        );
    }

    private static Optional<CheckpointGenerationId> previousValidGeneration(
            CheckpointFilesystemStore store,
            CheckpointFilesystemRecoveryRequest request,
            CheckpointRecoveredGeneration selected
    ) {
        var manifest = selected.manifest();
        if (manifest.predecessorGenerationId().isEmpty() || manifest.predecessorManifestDigest().isEmpty()) {
            return Optional.empty();
        }
        var recovered = store.loadCommittedGenerationReadOnly(
                request,
                manifest.predecessorGenerationId().orElseThrow(),
                manifest.predecessorManifestDigest().orElseThrow()
        );
        if (!recovered.successful()) return Optional.empty();
        return WorkstationCheckpointRestorabilityVerifier.verify(
                recovered.recoveredGeneration().orElseThrow()).restorable()
                ? manifest.predecessorGenerationId()
                : Optional.empty();
    }
}
