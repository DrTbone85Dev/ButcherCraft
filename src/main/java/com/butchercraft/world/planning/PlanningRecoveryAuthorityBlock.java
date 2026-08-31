package com.butchercraft.world.planning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Planning-owned disposition for an unproven non-repeatable outcome. */
public record PlanningRecoveryAuthorityBlock(
        int schemaVersion,
        String blockIdentity,
        String planningWorkIdentity,
        long eligibilityStartTick,
        long eligibilityEndTick,
        List<EvidenceReference> sourceEvidence,
        String reasonIdentity,
        Repeatability repeatability,
        DependencyScope dependencyScope,
        List<String> dependentAuthorityIdentities,
        boolean dependencyClosureProven,
        String contentDigest
) implements Comparable<PlanningRecoveryAuthorityBlock> {
    private static final String PREFIX = "butchercraft:planning_recovery_authority_block/v1/";

    public PlanningRecoveryAuthorityBlock {
        schemaVersion = PlanningValidation.schema(schemaVersion);
        blockIdentity = PlanningValidation.id(blockIdentity, "Planning recovery authority block identity");
        if (!blockIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Planning recovery authority block identity has unsupported prefix");
        }
        planningWorkIdentity = PlanningValidation.id(planningWorkIdentity, "Planning recovery Work identity");
        eligibilityStartTick = PlanningValidation.tick(eligibilityStartTick);
        eligibilityEndTick = PlanningValidation.tick(eligibilityEndTick);
        if (eligibilityEndTick < eligibilityStartTick) {
            throw new IllegalArgumentException("Planning recovery eligibility range is inverted");
        }
        sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence").stream().sorted().toList();
        if (sourceEvidence.isEmpty()) throw new IllegalArgumentException("Planning recovery block needs source evidence");
        reasonIdentity = PlanningValidation.id(reasonIdentity, "Planning recovery reason identity");
        repeatability = Objects.requireNonNull(repeatability, "repeatability");
        dependencyScope = Objects.requireNonNull(dependencyScope, "dependencyScope");
        dependentAuthorityIdentities = Objects.requireNonNull(
                dependentAuthorityIdentities,
                "dependentAuthorityIdentities"
        ).stream().map(value -> PlanningValidation.id(value, "Dependent authority identity"))
                .distinct().sorted().toList();
        contentDigest = requireDigest(contentDigest, "Planning recovery authority block digest");
        if (!dependencyClosureProven && dependencyScope != DependencyScope.WHOLE_WORLD_MUTATION) {
            throw new IllegalArgumentException("Unproven Planning dependency closure must block whole-world mutation");
        }
        if (dependencyScope != DependencyScope.WHOLE_WORLD_MUTATION && dependentAuthorityIdentities.isEmpty()) {
            throw new IllegalArgumentException("Scoped Planning authority block requires dependent authorities");
        }
        String expected = calculateDigest(
                planningWorkIdentity,
                eligibilityStartTick,
                eligibilityEndTick,
                sourceEvidence,
                reasonIdentity,
                repeatability,
                dependencyScope,
                dependentAuthorityIdentities,
                dependencyClosureProven
        );
        if (!contentDigest.equals(expected) || !blockIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Planning recovery authority block is not canonical");
        }
    }

    public static PlanningRecoveryAuthorityBlock unresolvedNonRepeatable(
            String planningWorkIdentity,
            long eligibilityStartTick,
            long eligibilityEndTick,
            List<EvidenceReference> sourceEvidence,
            DependencyScope requestedScope,
            List<String> dependentAuthorityIdentities,
            boolean dependencyClosureProven
    ) {
        DependencyScope effectiveScope = dependencyClosureProven
                ? Objects.requireNonNull(requestedScope, "requestedScope")
                : DependencyScope.WHOLE_WORLD_MUTATION;
        String reason = "butchercraft:recovery_reason/legacy_split_planning_outcome_unproven";
        List<EvidenceReference> evidence = sourceEvidence.stream().sorted().toList();
        List<String> dependencies = dependentAuthorityIdentities.stream().distinct().sorted().toList();
        String digest = calculateDigest(
                planningWorkIdentity,
                eligibilityStartTick,
                eligibilityEndTick,
                evidence,
                reason,
                Repeatability.NON_REPEATABLE,
                effectiveScope,
                dependencies,
                dependencyClosureProven
        );
        return new PlanningRecoveryAuthorityBlock(
                PlanningValidation.SCHEMA_VERSION,
                PREFIX + digest.substring("sha256:".length()),
                planningWorkIdentity,
                eligibilityStartTick,
                eligibilityEndTick,
                evidence,
                reason,
                Repeatability.NON_REPEATABLE,
                effectiveScope,
                dependencies,
                dependencyClosureProven,
                digest
        );
    }

    public boolean wholeWorldMutationBlocked() {
        return dependencyScope == DependencyScope.WHOLE_WORLD_MUTATION;
    }

    @Override
    public int compareTo(PlanningRecoveryAuthorityBlock other) {
        return blockIdentity.compareTo(Objects.requireNonNull(other, "other").blockIdentity);
    }

    private static String calculateDigest(
            String workIdentity,
            long start,
            long end,
            List<EvidenceReference> evidence,
            String reason,
            Repeatability repeatability,
            DependencyScope scope,
            List<String> dependencies,
            boolean closureProven
    ) {
        StringBuilder canonical = new StringBuilder("butchercraft:planning_recovery_authority_block\n");
        append(canonical, Integer.toString(PlanningValidation.SCHEMA_VERSION));
        append(canonical, workIdentity);
        append(canonical, Long.toString(start));
        append(canonical, Long.toString(end));
        append(canonical, reason);
        append(canonical, repeatability.name());
        append(canonical, scope.name());
        append(canonical, Boolean.toString(closureProven));
        evidence.stream().sorted().forEach(reference -> {
            append(canonical, reference.ownerSubsystemId());
            append(canonical, reference.evidenceIdentity());
            append(canonical, reference.contentDigest());
        });
        dependencies.stream().sorted().forEach(value -> append(canonical, value));
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder builder, String value) {
        String required = Objects.requireNonNull(value, "canonical value");
        builder.append(required.length()).append(':').append(required).append('\n');
    }

    private static String requireDigest(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!normalized.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    public enum Repeatability {
        NON_REPEATABLE
    }

    public enum DependencyScope {
        SPECIFIC_DEPENDENT_WORK,
        BOUNDED_AUTHORITY_SCOPE,
        WHOLE_WORLD_MUTATION
    }

    public record EvidenceReference(
            String ownerSubsystemId,
            String evidenceIdentity,
            String contentDigest
    ) implements Comparable<EvidenceReference> {
        public EvidenceReference {
            ownerSubsystemId = PlanningValidation.id(ownerSubsystemId, "Planning evidence owner id");
            evidenceIdentity = PlanningValidation.id(evidenceIdentity, "Planning evidence identity");
            contentDigest = requireDigest(contentDigest, "Planning evidence digest");
        }

        @Override
        public int compareTo(EvidenceReference other) {
            int ownerComparison = ownerSubsystemId.compareTo(other.ownerSubsystemId);
            return ownerComparison != 0 ? ownerComparison : evidenceIdentity.compareTo(other.evidenceIdentity);
        }
    }
}
