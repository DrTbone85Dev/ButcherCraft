package com.butchercraft.workstation.projection;

import com.butchercraft.world.checkpoint.RecoveryOperatorEvidence;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record LegacyWorkstationProjectionAuthorization(
        int schemaVersion,
        String authorizationIdentity,
        WorldIdentityRootIdentity worldIdentity,
        String analysisDigest,
        List<String> candidateDigests,
        List<String> physicalEvidenceDigests,
        List<String> blockerIdentities,
        Disposition disposition,
        RecoveryOperatorEvidence operatorEvidence,
        String contentDigest
) {
    public static final int SCHEMA_VERSION = 1;
    private static final String PREFIX = "butchercraft:legacy_workstation_projection_authorization/v1/";

    public LegacyWorkstationProjectionAuthorization {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported bootstrap authorization schema");
        authorizationIdentity = requireText(authorizationIdentity, "authorizationIdentity");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        analysisDigest = requireText(analysisDigest, "analysisDigest");
        candidateDigests = ordered(candidateDigests, "candidateDigest");
        physicalEvidenceDigests = ordered(physicalEvidenceDigests, "physicalEvidenceDigest");
        blockerIdentities = ordered(blockerIdentities, "blockerIdentity");
        disposition = Objects.requireNonNull(disposition, "disposition");
        operatorEvidence = Objects.requireNonNull(operatorEvidence, "operatorEvidence");
        operatorEvidence.requirePublicationAuthority();
        contentDigest = requireText(contentDigest, "contentDigest");
        String expected = digest(worldIdentity, analysisDigest, candidateDigests, physicalEvidenceDigests,
                blockerIdentities, disposition, operatorEvidence);
        if (!contentDigest.equals(expected)
                || !authorizationIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Legacy Workstation projection authorization is not canonical");
        }
    }

    public static LegacyWorkstationProjectionAuthorization authorize(
            LegacyWorkstationProjectionAnalysis analysis,
            Disposition disposition,
            RecoveryOperatorEvidence operatorEvidence
    ) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(disposition, "disposition");
        if (disposition == Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION && !analysis.proofComplete()) {
            throw new IllegalArgumentException("Blocked legacy projection analysis cannot be authorized for publication");
        }
        List<String> candidates = analysis.candidates().stream()
                .map(LegacyWorkstationProjectionAnalysis.Candidate::candidateDigest).sorted().toList();
        List<String> physical = analysis.candidates().stream()
                .map(LegacyWorkstationProjectionAnalysis.Candidate::physicalEvidenceDigest).sorted().toList();
        List<String> blockers = analysis.blockers().stream().map(entry -> entry.instanceId().value()).sorted().toList();
        String digest = digest(analysis.worldIdentity(), analysis.analysisDigest(), candidates, physical,
                blockers, disposition, operatorEvidence);
        return new LegacyWorkstationProjectionAuthorization(
                SCHEMA_VERSION,
                PREFIX + digest.substring("sha256:".length()),
                analysis.worldIdentity(),
                analysis.analysisDigest(),
                candidates,
                physical,
                blockers,
                disposition,
                operatorEvidence,
                digest
        );
    }

    public boolean targets(LegacyWorkstationProjectionAnalysis analysis) {
        return worldIdentity.equals(analysis.worldIdentity())
                && analysisDigest.equals(analysis.analysisDigest())
                && candidateDigests.equals(analysis.candidates().stream()
                .map(LegacyWorkstationProjectionAnalysis.Candidate::candidateDigest).sorted().toList())
                && physicalEvidenceDigests.equals(analysis.candidates().stream()
                .map(LegacyWorkstationProjectionAnalysis.Candidate::physicalEvidenceDigest).sorted().toList())
                && blockerIdentities.equals(analysis.blockers().stream()
                .map(entry -> entry.instanceId().value()).sorted().toList());
    }

    public enum Disposition {
        AUTHORIZE_PROOF_COMPLETE_PUBLICATION,
        ABORT
    }

    private static String digest(
            WorldIdentityRootIdentity world,
            String analysisDigest,
            List<String> candidates,
            List<String> physical,
            List<String> blockers,
            Disposition disposition,
            RecoveryOperatorEvidence operator
    ) {
        Digest digest = new Digest("butchercraft:legacy_workstation_projection_authorization/v1")
                .add(SCHEMA_VERSION).add(world.identity()).add(world.schemaVersion()).add(world.rootDigest())
                .add(analysisDigest).add(disposition.name())
                .add(operator.principalIdentity()).add(operator.authority().name())
                .add(operator.evidenceIdentity()).add(operator.evidenceContentDigest());
        digest.add(candidates.size());
        candidates.forEach(digest::add);
        digest.add(physical.size());
        physical.forEach(digest::add);
        digest.add(blockers.size());
        blockers.forEach(digest::add);
        return digest.finish();
    }

    private static List<String> ordered(List<String> values, String field) {
        return Objects.requireNonNull(values, field).stream().map(value -> requireText(value, field))
                .distinct().sorted().toList();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static final class Digest {
        private final MessageDigest digest;

        private Digest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        private Digest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 0);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(bytes);
            return this;
        }

        private Digest add(long value) {
            return add(Long.toString(value));
        }

        private String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
