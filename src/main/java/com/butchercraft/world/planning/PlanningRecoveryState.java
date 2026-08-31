package com.butchercraft.world.planning;

import com.butchercraft.world.checkpoint.LegacySplitRecoveryResult;
import com.butchercraft.world.checkpoint.RecoveryAuthorityBlock;
import com.butchercraft.world.checkpoint.RecoveryMutationGate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Planning-owned persisted authority disposition; it grants no execution capability. */
public record PlanningRecoveryState(
        int schemaVersion,
        String recoveryIdentity,
        String recoveryResultIdentity,
        String recoveryResultContentDigest,
        List<RecoveryAuthorityBlock> authorityBlocks,
        RecoveryMutationGate mutationGate,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final String FILE_NAME = "planning_recovery_authority.json";

    public PlanningRecoveryState {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported Planning recovery-state schema");
        }
        recoveryIdentity = PlanningValidation.id(recoveryIdentity, "Planning recovery identity");
        recoveryResultIdentity = PlanningValidation.id(
                recoveryResultIdentity, "Planning Recovery Result identity");
        recoveryResultContentDigest = requireDigest(recoveryResultContentDigest);
        authorityBlocks = Objects.requireNonNull(authorityBlocks, "authorityBlocks").stream().sorted().toList();
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        contentDigest = requireDigest(contentDigest);
        if (authorityBlocks.isEmpty()
                || !mutationGate.wholeWorldConsequentialMutationBlocked()
                || !contentDigest.equals(calculateDigest(
                recoveryIdentity,
                recoveryResultIdentity,
                recoveryResultContentDigest,
                authorityBlocks,
                mutationGate))) {
            throw new IllegalArgumentException("Planning recovery state is not canonical");
        }
    }

    public static PlanningRecoveryState fromResult(LegacySplitRecoveryResult result) {
        Objects.requireNonNull(result, "result");
        String digest = calculateDigest(
                result.recoveryIdentity().value(),
                result.resultIdentity(),
                result.contentDigest(),
                result.authorityBlocks(),
                result.mutationGate()
        );
        return new PlanningRecoveryState(
                CURRENT_SCHEMA,
                result.recoveryIdentity().value(),
                result.resultIdentity(),
                result.contentDigest(),
                result.authorityBlocks(),
                result.mutationGate(),
                digest
        );
    }

    private static String calculateDigest(
            String recoveryIdentity,
            String resultIdentity,
            String resultDigest,
            List<RecoveryAuthorityBlock> blocks,
            RecoveryMutationGate gate
    ) {
        StringBuilder canonical = new StringBuilder("butchercraft:planning_recovery_state\n");
        add(canonical, Integer.toString(CURRENT_SCHEMA));
        add(canonical, recoveryIdentity);
        add(canonical, resultIdentity);
        add(canonical, resultDigest);
        blocks.stream().sorted().forEach(block -> {
            add(canonical, block.blockIdentity());
            add(canonical, block.ownerBlockContentDigest());
            add(canonical, block.scope().name());
        });
        add(canonical, Boolean.toString(gate.wholeWorldConsequentialMutationBlocked()));
        gate.blockedAuthorityIdentities().forEach(value -> add(canonical, value));
        gate.blockIdentities().forEach(value -> add(canonical, value));
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('\n');
    }

    private static String requireDigest(String value) {
        String normalized = Objects.requireNonNull(value, "digest").strip();
        if (!normalized.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Planning recovery digest is not canonical");
        }
        return normalized;
    }
}
