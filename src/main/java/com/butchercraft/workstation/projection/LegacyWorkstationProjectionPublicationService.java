package com.butchercraft.workstation.projection;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependency;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.google.gson.Gson;
import net.minecraft.core.HolderLookup;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Operator-authorized publication of immutable legacy bootstrap evidence and exact R3A projections. */
public final class LegacyWorkstationProjectionPublicationService {
    private static final String LABEL = "Legacy Workstation projection bootstrap evidence";
    private static final Gson GSON = StrictJsonPersistence.gson();
    private final LegacyWorkstationProjectionAnalyzer analyzer;

    public LegacyWorkstationProjectionPublicationService(LegacyWorkstationProjectionAnalyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
    }

    public synchronized LegacyWorkstationProjectionPublicationReport publish(
            Path worldRoot,
            HolderLookup.Provider registries,
            List<WorkstationCheckpointDependency> dependencies,
            LegacyWorkstationProjectionAuthorization authorization
    ) {
        long started = System.nanoTime();
        Objects.requireNonNull(authorization, "authorization").operatorEvidence().requirePublicationAuthority();
        if (authorization.disposition()
                != LegacyWorkstationProjectionAuthorization.Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION) {
            throw new SecurityException("Legacy Workstation projection publication was not authorized");
        }
        LegacyWorkstationProjectionAnalysis analysis = analyzer.analyze(worldRoot, registries, dependencies);
        if (!analysis.proofComplete() || !authorization.targets(analysis)) {
            throw new SecurityException("Legacy Workstation projection authorization is stale or blocked");
        }
        Path evidencePath = evidencePath(worldRoot, analysis.analysisDigest());
        byte[] evidenceBytes = evidenceBytes(analysis, authorization);
        publishImmutable(evidencePath, evidenceBytes);

        List<LegacyWorkstationProjectionPublicationReport.PublishedProjection> published = new ArrayList<>();
        long bytes = 0L;
        for (LegacyWorkstationProjectionAnalysis.Candidate candidate : analysis.candidates()) {
            FrozenWorkstationProjectionSnapshot frozen = DurableWorkstationProjectionService.INSTANCE
                    .publishProvenLegacyBootstrap(worldRoot, candidate.projection());
            DurableWorkstationProjection reread = LegacyWorkstationProjectionAnalyzer.projectionStorage(
                    worldRoot.toAbsolutePath().normalize().resolve("butchercraft"))
                    .read(Objects.requireNonNull(new com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage(
                            worldRoot.resolve("butchercraft/workstation_instances.json")).loadExisting().orElseThrow())
                            .find(candidate.projection().instanceId()).orElseThrow())
                    .projection().orElseThrow();
            if (!reread.equals(candidate.projection())
                    || !frozen.stateDigest().equals(candidate.projection().stateDigest())) {
                throw new IllegalStateException("Published legacy Workstation projection failed exact read-back");
            }
            published.add(new LegacyWorkstationProjectionPublicationReport.PublishedProjection(
                    frozen.instanceId(), frozen.projectionRevision(), frozen.stateDigest(),
                    CheckpointSnapshotDigest.sha256(frozen.frozenBytes()), frozen.frozenBytes().length));
            bytes += frozen.frozenBytes().length;
        }
        return new LegacyWorkstationProjectionPublicationReport(
                true,
                analysis.analysisDigest(),
                authorization.authorizationIdentity(),
                evidencePath,
                published,
                bytes,
                System.nanoTime() - started,
                "All proof-complete legacy Workstation projections were published and verified"
        );
    }

    public Path evidencePath(Path worldRoot, String analysisDigest) {
        String suffix = Objects.requireNonNull(analysisDigest, "analysisDigest").substring("sha256:".length());
        return Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize()
                .resolve("butchercraft/workstations/legacy_bootstrap/v1")
                .resolve(suffix + ".json");
    }

    private byte[] evidenceBytes(
            LegacyWorkstationProjectionAnalysis analysis,
            LegacyWorkstationProjectionAuthorization authorization
    ) {
        BootstrapDocument document = new BootstrapDocument(
                1,
                LegacyWorkstationProjectionAnalyzer.PROVENANCE,
                analysis.worldIdentity().identity(),
                analysis.worldIdentity().schemaVersion(),
                analysis.worldIdentity().rootDigest(),
                analysis.analysisDigest(),
                authorization.authorizationIdentity(),
                authorization.contentDigest(),
                authorization.operatorEvidence().principalIdentity(),
                authorization.operatorEvidence().authority().name(),
                authorization.operatorEvidence().evidenceIdentity(),
                authorization.operatorEvidence().evidenceContentDigest(),
                analysis.candidates().stream().map(candidate -> new CandidateDocument(
                        candidate.projection().instanceId().value(),
                        candidate.projection().projectionRevision(),
                        candidate.projection().stateDigest(),
                        candidate.physicalEvidenceDigest(),
                        candidate.ownerEvidenceIdentities(),
                        candidate.candidateDigest()
                )).toList()
        );
        return (GSON.toJson(document) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private void publishImmutable(Path target, byte[] bytes) {
        AtomicFilePublication.requireNoInterruptedPublication(target, LABEL);
        if (Files.exists(target)) {
            byte[] current = AtomicFilePublication.readBytes(target, LABEL);
            if (!java.util.Arrays.equals(current, bytes)) {
                throw new IllegalStateException("Legacy Workstation bootstrap evidence conflicts with existing evidence");
            }
            return;
        }
        AtomicFilePublication.publishBytesIfDigestMatches(target, Optional.empty(), bytes, LABEL);
        if (!java.util.Arrays.equals(AtomicFilePublication.readBytes(target, LABEL), bytes)) {
            throw new IllegalStateException("Legacy Workstation bootstrap evidence failed read-back verification");
        }
    }

    private record BootstrapDocument(
            int schemaVersion,
            String classification,
            String worldIdentity,
            int worldIdentitySchema,
            String worldIdentityRootDigest,
            String analysisDigest,
            String authorizationIdentity,
            String authorizationContentDigest,
            String operatorPrincipalIdentity,
            String operatorAuthority,
            String operatorEvidenceIdentity,
            String operatorEvidenceContentDigest,
            List<CandidateDocument> candidates
    ) {}

    private record CandidateDocument(
            String workstationInstanceIdentity,
            long projectionRevision,
            String projectionStateDigest,
            String physicalEvidenceDigest,
            List<String> ownerEvidenceIdentities,
            String candidateDigest
    ) {}
}
