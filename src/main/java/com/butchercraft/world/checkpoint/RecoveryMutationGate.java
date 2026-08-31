package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record RecoveryMutationGate(
        int schemaVersion,
        boolean wholeWorldConsequentialMutationBlocked,
        List<String> blockedAuthorityIdentities,
        List<String> blockIdentities
) {
    public static final int CURRENT_SCHEMA = 1;

    public RecoveryMutationGate {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported recovery mutation-gate schema");
        }
        blockedAuthorityIdentities = Objects.requireNonNull(
                blockedAuthorityIdentities,
                "blockedAuthorityIdentities"
        ).stream().map(value -> CheckpointValidation.id(value, "blockedAuthorityIdentity"))
                .distinct().sorted().toList();
        blockIdentities = Objects.requireNonNull(blockIdentities, "blockIdentities").stream()
                .map(value -> CheckpointValidation.id(value, "mutationGateBlockIdentity"))
                .distinct().sorted().toList();
        if (wholeWorldConsequentialMutationBlocked && blockIdentities.isEmpty()) {
            throw new IllegalArgumentException("Whole-world mutation block requires exact block evidence");
        }
    }

    public static RecoveryMutationGate fromPlan(SplitSnapshotRecoveryPlan plan) {
        Objects.requireNonNull(plan, "plan");
        boolean wholeWorld = plan.authorityBlocks().stream()
                .anyMatch(block -> block.scope() == RecoveryAuthorityBlock.Scope.WHOLE_WORLD_MUTATION);
        List<String> blockedAuthorities = plan.authorityBlocks().stream()
                .flatMap(block -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(block.ownerId().value()),
                        block.affectedReferenceIdentities().stream()
                ))
                .distinct()
                .sorted()
                .toList();
        return new RecoveryMutationGate(
                CURRENT_SCHEMA,
                wholeWorld,
                blockedAuthorities,
                plan.authorityBlocks().stream().map(RecoveryAuthorityBlock::blockIdentity).toList()
        );
    }

    public boolean permitsConsequentialMutation(CheckpointOwnerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return !wholeWorldConsequentialMutationBlocked && !blockedAuthorityIdentities.contains(ownerId.value());
    }

    public boolean readOnlyDiagnosticsPermitted() {
        return true;
    }
}
