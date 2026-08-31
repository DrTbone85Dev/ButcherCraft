package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacyWorkstationProjectionAnalysis(
        int schemaVersion,
        WorldIdentityRootIdentity worldIdentity,
        long workstationInstanceRegistryRevision,
        List<Entry> entries,
        String analysisDigest,
        long analysisNanos
) {
    public static final int SCHEMA_VERSION = 1;

    public LegacyWorkstationProjectionAnalysis {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported legacy analysis schema");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (workstationInstanceRegistryRevision <= 0L) {
            throw new IllegalArgumentException("Workstation registry revision must be positive");
        }
        entries = Objects.requireNonNull(entries, "entries").stream().sorted().toList();
        analysisDigest = requireText(analysisDigest, "analysisDigest");
        if (analysisNanos < 0L) throw new IllegalArgumentException("Analysis duration must not be negative");
    }

    public List<Candidate> candidates() {
        return entries.stream().flatMap(entry -> entry.candidate().stream()).toList();
    }

    public List<Entry> blockers() {
        return entries.stream().filter(entry -> entry.classification() != LegacyWorkstationProjectionClassification.PROOF_COMPLETE
                && entry.classification() != LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE
                && entry.classification() != LegacyWorkstationProjectionClassification.RETIRED_PROVEN).toList();
    }

    public boolean proofComplete() {
        return blockers().isEmpty();
    }

    public record Entry(
            WorkstationInstanceId instanceId,
            LegacyWorkstationProjectionClassification classification,
            String detail,
            Optional<Candidate> candidate
    ) implements Comparable<Entry> {
        public Entry {
            instanceId = Objects.requireNonNull(instanceId, "instanceId");
            classification = Objects.requireNonNull(classification, "classification");
            detail = requireText(detail, "detail");
            candidate = Objects.requireNonNull(candidate, "candidate");
            if ((classification == LegacyWorkstationProjectionClassification.PROOF_COMPLETE) != candidate.isPresent()) {
                throw new IllegalArgumentException("Only proof-complete analysis entries carry a projection candidate");
            }
        }

        @Override
        public int compareTo(Entry other) {
            return instanceId.compareTo(other.instanceId);
        }
    }

    public record Candidate(
            DurableWorkstationProjection projection,
            String physicalEvidenceDigest,
            List<String> ownerEvidenceIdentities,
            String provenance,
            String candidateDigest
    ) implements Comparable<Candidate> {
        public Candidate {
            projection = Objects.requireNonNull(projection, "projection");
            physicalEvidenceDigest = requireText(physicalEvidenceDigest, "physicalEvidenceDigest");
            ownerEvidenceIdentities = Objects.requireNonNull(ownerEvidenceIdentities, "ownerEvidenceIdentities")
                    .stream().map(value -> requireText(value, "ownerEvidenceIdentity")).distinct().sorted().toList();
            provenance = requireText(provenance, "provenance");
            candidateDigest = requireText(candidateDigest, "candidateDigest");
        }

        @Override
        public int compareTo(Candidate other) {
            return projection.instanceId().compareTo(other.projection.instanceId());
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
