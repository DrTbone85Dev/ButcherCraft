package com.butchercraft.development.checkpoint;

import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependency;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionSnapshot;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityReport;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAnalysis;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAuthorization;
import com.butchercraft.workstation.projection.WorkstationProjectionStorage;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.CheckpointPublicationReport;
import com.butchercraft.world.checkpoint.CheckpointPublicationRequest;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.CheckpointTriggerIdentities;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.OwnerSnapshotDescriptor;
import com.butchercraft.world.checkpoint.SplitSnapshotRecoveryAnalyzer;
import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Offline, same-tick successor publication for an already proven legacy Workstation projection set. */
public final class LegacyWorkstationProjectionSuccessorService {
    public static final String TRIGGER_CAUSE =
            CheckpointTriggerIdentities.LEGACY_WORKSTATION_PROJECTION_SUCCESSOR;
    private static final long HISTORICAL_TICK = 39_872L;
    private static final List<String> WORKSTATION_NATIVE_FILES = List.of(
            "machine_operating_states.json",
            "workstation_endpoint_journal.json",
            "workstation_instances.json",
            "workstation_reservations.json"
    );

    public LegacyWorkstationProjectionSuccessorReport publish(
            Path worldRoot,
            HolderLookup.Provider registries,
            List<WorkstationCheckpointDependency> dependencies,
            LegacyWorkstationProjectionAuthorization authorization
    ) {
        long started = System.nanoTime();
        Path world = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(authorization, "authorization").operatorEvidence().requirePublicationAuthority();
        if (authorization.disposition()
                != LegacyWorkstationProjectionAuthorization.Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION) {
            throw new SecurityException("Legacy Workstation successor publication was not authorized");
        }

        LegacyWorkstationProjectionAnalysis analysis = new LegacyWorkstationProjectionAdminTool()
                .analyzeReadOnly(world, registries, dependencies);
        if (!analysis.proofComplete() || !analysis.candidates().isEmpty() || !authorization.targets(analysis)) {
            throw new SecurityException("Successor authorization is stale or durable projections are incomplete");
        }

        var recoveryPlan = new SplitSnapshotRecoveryAnalyzer().analyze(
                new ActualWorldLegacySplitRecoverySource(world).reloadReadOnly());
        Path checkpointRoot = world.resolve("butchercraft/checkpoints");
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(checkpointRoot);
        CheckpointFilesystemRecoveryRequest recoveryRequest = new CheckpointFilesystemRecoveryRequest(
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                recoveryPlan.worldIdentityRoot(),
                recoveryPlan.platformDeterminismManifest()
        );
        CheckpointRecoveredGeneration predecessor = store.loadSelectedGenerationReadOnly(recoveryRequest)
                .recoveredGeneration().orElseThrow(() ->
                        new IllegalStateException("Historical recovery generation is not available"));
        if (predecessor.manifest().generationId().committedSequence() != 1L
                || predecessor.manifest().authoritativeSimulationTick() != HISTORICAL_TICK) {
            throw new IllegalStateException("R3C successor requires immutable historical generation 1/39872");
        }
        WorkstationCheckpointRestorabilityReport historical =
                WorkstationCheckpointRestorabilityVerifier.verify(predecessor);
        if (historical.restorable()) {
            throw new IllegalStateException("Historical generation unexpectedly contains complete Workstation projections");
        }

        Path ownerRoot = world.resolve("butchercraft");
        WorkstationInstanceRegistry instances = new WorkstationInstanceStorage(
                ownerRoot.resolve("workstation_instances.json")).loadExisting().orElseThrow();
        WorkstationCheckpointProjectionSnapshot workstationSnapshot =
                WorkstationCheckpointProjectionService.captureOffline(
                        instances,
                        dependencies,
                        new WorkstationProjectionStorage(ownerRoot.resolve("workstations/projections/v1"))
                );
        if (!workstationSnapshot.restorable()) {
            throw new IllegalStateException("R3B Workstation completeness remains blocked: "
                    + workstationSnapshot.blockers());
        }

        CheckpointGenerationId successorId = CheckpointGenerationId.of(
                predecessor.manifest().generationId().committedSequence() + 1L,
                predecessor.manifest().authoritativeSimulationTick()
        );
        CheckpointOwnerSnapshotPayload workstationPayload = workstationPayload(
                ownerRoot, workstationSnapshot, predecessor, successorId);
        List<CheckpointOwnerSnapshotPayload> successorPayloads = new ArrayList<>();
        for (CheckpointOwnerSnapshotPayload payload : predecessor.ownerSnapshots()) {
            successorPayloads.add(payload.descriptor().ownerId().equals(LegacySplitRecoveryParticipants.WORKSTATION)
                    ? workstationPayload
                    : rebind(payload, successorId));
        }

        CheckpointPublicationReport publication = store.publish(new CheckpointPublicationRequest(
                successorId,
                Optional.of(predecessor.manifest().generationId()),
                Optional.of(predecessor.manifest().manifestDigest()),
                predecessor.manifest().authoritativeSimulationTick(),
                successorPayloads,
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                List.of(TRIGGER_CAUSE),
                predecessor.manifest().platformDeterminismManifest(),
                predecessor.manifest().worldIdentityRoot()
        ));
        if (!publication.successful()) {
            throw new IllegalStateException("Successor checkpoint publication failed: " + publication.diagnostics());
        }

        CheckpointRecoveredGeneration successor = store.loadSelectedGenerationReadOnly(recoveryRequest)
                .recoveredGeneration().orElseThrow(() ->
                        new IllegalStateException("Published successor generation could not be selected"));
        if (!successor.manifest().generationId().equals(successorId)
                || successor.ownerSnapshots().size() != LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.size()) {
            throw new IllegalStateException("Published successor is not the coherent 17-owner generation");
        }
        WorkstationCheckpointRestorabilityReport successorWorkstation =
                WorkstationCheckpointRestorabilityVerifier.verify(successor);
        if (!successorWorkstation.restorable()) {
            throw new IllegalStateException("Published successor failed the R3B Workstation verifier: "
                    + successorWorkstation.blockers());
        }
        return new LegacyWorkstationProjectionSuccessorReport(
                predecessor.manifest().generationId(),
                predecessor.manifest().manifestDigest(),
                successorId,
                successor.manifest().manifestDigest(),
                publication.outcome(),
                successor.ownerSnapshots().size(),
                successor.ownerSnapshots().size() - 1,
                workstationSnapshot,
                historical,
                successorWorkstation,
                directoryBytes(store.layout().finalGenerationDirectory(successorId)),
                System.nanoTime() - started
        );
    }

    private CheckpointOwnerSnapshotPayload workstationPayload(
            Path ownerRoot,
            WorkstationCheckpointProjectionSnapshot projectionSnapshot,
            CheckpointRecoveredGeneration predecessor,
            CheckpointGenerationId successorId
    ) {
        OwnerSnapshotDescriptor prior = predecessor.ownerSnapshots().stream()
                .map(CheckpointOwnerSnapshotPayload::descriptor)
                .filter(descriptor -> descriptor.ownerId().equals(LegacySplitRecoveryParticipants.WORKSTATION))
                .findFirst().orElseThrow();
        List<CheckpointOwnerFileSnapshot.FilePayload> files = new ArrayList<>();
        for (String name : WORKSTATION_NATIVE_FILES) {
            files.add(new CheckpointOwnerFileSnapshot.FilePayload(name, read(ownerRoot.resolve(name))));
        }
        files.add(new CheckpointOwnerFileSnapshot.FilePayload(
                "workstation_projections.json",
                projectionSnapshot.json().getBytes(StandardCharsets.UTF_8)
        ));
        CheckpointOwnerFileSnapshot snapshot = new CheckpointOwnerFileSnapshot(
                LegacySplitRecoveryParticipants.WORKSTATION,
                3,
                prior.ownerSequence(),
                false,
                files
        );
        byte[] bytes = CheckpointOwnerFileBundleCodec.encode(snapshot);
        String digest = CheckpointSnapshotDigest.sha256(bytes);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                prior.ownerId(),
                CheckpointOwnerFileBundleCodec.SCHEMA_VERSION,
                "butchercraft:live_owner_snapshot/workstation/" + digest.substring("sha256:".length()),
                digest,
                prior.participation(),
                prior.configurationIdentity(),
                prior.worldIdentityRoot(),
                successorId,
                prior.representedSimulationTick(),
                prior.ownerSequence()
        );
        return CheckpointOwnerSnapshotPayload.of(descriptor, bytes);
    }

    private CheckpointOwnerSnapshotPayload rebind(
            CheckpointOwnerSnapshotPayload payload,
            CheckpointGenerationId successorId
    ) {
        OwnerSnapshotDescriptor prior = payload.descriptor();
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                prior.ownerId(),
                prior.snapshotSchemaVersion(),
                prior.snapshotIdentity(),
                prior.contentDigest(),
                prior.participation(),
                prior.configurationIdentity(),
                prior.worldIdentityRoot(),
                successorId,
                prior.representedSimulationTick(),
                prior.ownerSequence()
        );
        return CheckpointOwnerSnapshotPayload.of(descriptor, payload.payloadBytes());
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Workstation native owner file could not be read: " + path, exception);
        }
    }

    private static long directoryBytes(Path directory) {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).sum();
        } catch (IOException exception) {
            throw new IllegalStateException("Successor checkpoint size could not be measured", exception);
        }
    }
}
