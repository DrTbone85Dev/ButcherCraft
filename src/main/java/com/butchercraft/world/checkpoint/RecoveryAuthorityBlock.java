package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

/** Checkpoint view of an owner-defined mutation block; it does not resolve the owner outcome. */
public record RecoveryAuthorityBlock(
        String blockIdentity,
        CheckpointOwnerId ownerId,
        String ownerBlockIdentity,
        String ownerBlockContentDigest,
        String reasonIdentity,
        Scope scope,
        List<String> affectedReferenceIdentities,
        boolean dependencyClosureProven
) implements Comparable<RecoveryAuthorityBlock> {
    public RecoveryAuthorityBlock {
        blockIdentity = CheckpointValidation.id(blockIdentity, "recoveryAuthorityBlockIdentity");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        ownerBlockIdentity = CheckpointValidation.id(ownerBlockIdentity, "ownerBlockIdentity");
        ownerBlockContentDigest = CheckpointValidation.digest(
                ownerBlockContentDigest,
                "ownerBlockContentDigest"
        );
        reasonIdentity = CheckpointValidation.id(reasonIdentity, "authorityBlockReasonIdentity");
        scope = Objects.requireNonNull(scope, "scope");
        affectedReferenceIdentities = Objects.requireNonNull(
                affectedReferenceIdentities,
                "affectedReferenceIdentities"
        ).stream().map(value -> CheckpointValidation.id(value, "affectedReferenceIdentity"))
                .distinct().sorted().toList();
        if (!dependencyClosureProven && scope != Scope.WHOLE_WORLD_MUTATION) {
            throw new IllegalArgumentException("Unproven dependency closure requires whole-world mutation block");
        }
        String expected = identity(
                ownerId,
                ownerBlockIdentity,
                ownerBlockContentDigest,
                reasonIdentity,
                scope,
                affectedReferenceIdentities,
                dependencyClosureProven
        );
        if (!blockIdentity.equals(expected)) {
            throw new IllegalArgumentException("Recovery authority block identity is not canonical");
        }
    }

    public static RecoveryAuthorityBlock create(
            CheckpointOwnerId ownerId,
            String ownerBlockIdentity,
            String ownerBlockContentDigest,
            String reasonIdentity,
            Scope scope,
            List<String> affectedReferences,
            boolean dependencyClosureProven
    ) {
        Scope effectiveScope = dependencyClosureProven
                ? Objects.requireNonNull(scope, "scope")
                : Scope.WHOLE_WORLD_MUTATION;
        List<String> references = affectedReferences.stream().distinct().sorted().toList();
        return new RecoveryAuthorityBlock(
                identity(
                        ownerId,
                        ownerBlockIdentity,
                        ownerBlockContentDigest,
                        reasonIdentity,
                        effectiveScope,
                        references,
                        dependencyClosureProven
                ),
                ownerId,
                ownerBlockIdentity,
                ownerBlockContentDigest,
                reasonIdentity,
                effectiveScope,
                references,
                dependencyClosureProven
        );
    }

    private static String identity(
            CheckpointOwnerId owner,
            String ownerBlock,
            String ownerDigest,
            String reason,
            Scope scope,
            List<String> references,
            boolean closureProven
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:recovery_authority_block"
        ).add(LegacySplitRecoverySchema.CURRENT_VERSION)
                .add(owner.value())
                .add(ownerBlock)
                .add(ownerDigest)
                .add(reason)
                .add(scope.name())
                .add(closureProven)
                .add(references.size());
        references.stream().sorted().forEach(digest::add);
        return "butchercraft:recovery_authority_block/v1/"
                + digest.finish().substring("sha256:".length());
    }

    @Override
    public int compareTo(RecoveryAuthorityBlock other) {
        return blockIdentity.compareTo(Objects.requireNonNull(other, "other").blockIdentity);
    }

    public enum Scope {
        SPECIFIC_DEPENDENT_WORK,
        BOUNDED_AUTHORITY_SCOPE,
        WHOLE_WORLD_MUTATION
    }
}
