package com.butchercraft.world.simulation.scheduler;

import com.butchercraft.world.checkpoint.LegacySplitRecoveryIdentity;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryResult;

import java.util.List;
import java.util.Objects;

/** Scheduler-owned, nonexecuting record of an admitted legacy discontinuity. */
public record SchedulerRecoveryState(
        int schemaVersion,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String recoveryResultIdentity,
        String recoveryResultContentDigest,
        List<LegacySplitRecoveryResult.PublishedAcknowledgement> historicalAcknowledgements,
        LegacySplitRecoveryResult.PublishedDiscontinuity discontinuity,
        long admissionCursorTick,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;

    public SchedulerRecoveryState {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported Scheduler recovery-state schema");
        }
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        recoveryResultIdentity = SchedulerValidation.requireId(
                recoveryResultIdentity, "Scheduler Recovery Result identity");
        recoveryResultContentDigest = requireDigest(recoveryResultContentDigest);
        historicalAcknowledgements = Objects.requireNonNull(
                historicalAcknowledgements, "historicalAcknowledgements").stream().sorted().toList();
        discontinuity = Objects.requireNonNull(discontinuity, "discontinuity");
        admissionCursorTick = SchedulerValidation.requireTick(admissionCursorTick, "Scheduler admission cursor");
        contentDigest = requireDigest(contentDigest);
        if (admissionCursorTick != discontinuity.inclusiveEndTick()
                || discontinuity.nextAdmissionTick() != Math.addExact(admissionCursorTick, 1L)
                || !contentDigest.equals(calculateDigest(
                recoveryIdentity,
                recoveryResultIdentity,
                recoveryResultContentDigest,
                historicalAcknowledgements,
                discontinuity,
                admissionCursorTick))) {
            throw new IllegalArgumentException("Scheduler recovery state is not canonical");
        }
    }

    public static SchedulerRecoveryState fromResult(LegacySplitRecoveryResult result) {
        Objects.requireNonNull(result, "result");
        long cursor = result.publishedDiscontinuity().inclusiveEndTick();
        String digest = calculateDigest(
                result.recoveryIdentity(),
                result.resultIdentity(),
                result.contentDigest(),
                result.publishedAcknowledgements(),
                result.publishedDiscontinuity(),
                cursor
        );
        return new SchedulerRecoveryState(
                CURRENT_SCHEMA,
                result.recoveryIdentity(),
                result.resultIdentity(),
                result.contentDigest(),
                result.publishedAcknowledgements(),
                result.publishedDiscontinuity(),
                cursor,
                digest
        );
    }

    private static String calculateDigest(
            LegacySplitRecoveryIdentity recoveryIdentity,
            String resultIdentity,
            String resultDigest,
            List<LegacySplitRecoveryResult.PublishedAcknowledgement> acknowledgements,
            LegacySplitRecoveryResult.PublishedDiscontinuity discontinuity,
            long cursor
    ) {
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create(
                "butchercraft:scheduler_recovery_state").add(CURRENT_SCHEMA)
                .add(recoveryIdentity.value()).add(resultIdentity).add(resultDigest)
                .add(acknowledgements.size());
        acknowledgements.stream().sorted().forEach(value -> digest
                .add(value.identity()).add(value.contentDigest())
                .add(value.executionOperationIdentity()).add(value.terminalOutcome()));
        return digest.add(discontinuity.identity()).add(discontinuity.contentDigest())
                .add(discontinuity.inclusiveStartTick()).add(discontinuity.inclusiveEndTick())
                .add(discontinuity.nextAdmissionTick()).add(cursor).finish();
    }

    private static String requireDigest(String value) {
        return SchedulerIdentityDigest.requireDigest(value, "Scheduler recovery-state digest");
    }
}
