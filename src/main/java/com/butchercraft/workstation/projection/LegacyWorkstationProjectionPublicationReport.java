package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record LegacyWorkstationProjectionPublicationReport(
        boolean successful,
        String analysisDigest,
        String authorizationIdentity,
        Path bootstrapEvidencePath,
        List<PublishedProjection> projections,
        long totalProjectionBytes,
        long publicationNanos,
        String detail
) {
    public LegacyWorkstationProjectionPublicationReport {
        analysisDigest = requireText(analysisDigest, "analysisDigest");
        authorizationIdentity = requireText(authorizationIdentity, "authorizationIdentity");
        bootstrapEvidencePath = Objects.requireNonNull(bootstrapEvidencePath, "bootstrapEvidencePath")
                .toAbsolutePath().normalize();
        projections = Objects.requireNonNull(projections, "projections").stream().sorted().toList();
        if (totalProjectionBytes < 0L || publicationNanos < 0L) {
            throw new IllegalArgumentException("Publication measurements must not be negative");
        }
        detail = requireText(detail, "detail");
    }

    public record PublishedProjection(
            WorkstationInstanceId instanceId,
            long projectionRevision,
            String stateDigest,
            String payloadDigest,
            int payloadBytes
    ) implements Comparable<PublishedProjection> {
        public PublishedProjection {
            instanceId = Objects.requireNonNull(instanceId, "instanceId");
            if (projectionRevision <= 0L || payloadBytes <= 0) {
                throw new IllegalArgumentException("Published projection metadata must be positive");
            }
            stateDigest = requireText(stateDigest, "stateDigest");
            payloadDigest = requireText(payloadDigest, "payloadDigest");
        }

        @Override
        public int compareTo(PublishedProjection other) {
            return instanceId.compareTo(other.instanceId);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
