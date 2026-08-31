package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;

/** Nonexecuting historical range marker. It provides no tick iteration or Work invocation API. */
public record SchedulerRecoveryDiscontinuity(
        int schemaVersion,
        String discontinuityIdentity,
        long sourceSchedulerTick,
        long authoritativeClockTick,
        long inclusiveMissingStartTick,
        long inclusiveMissingEndTick,
        long nextNormalAdmissionTick,
        String sourceSchedulerSnapshotIdentity,
        String sourceSchedulerSnapshotDigest,
        String clockSnapshotIdentity,
        String clockSnapshotDigest,
        String recoveryIdentityReference,
        String reasonIdentity,
        List<String> historicalAcknowledgementIdentities,
        List<String> unresolvedWorkReferences,
        String contentDigest
) {
    private static final int RECOVERY_EVIDENCE_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:scheduler_recovery_discontinuity/v1/";

    public SchedulerRecoveryDiscontinuity {
        if (schemaVersion != RECOVERY_EVIDENCE_SCHEMA) {
            throw new IllegalArgumentException("Unsupported Scheduler recovery discontinuity schema");
        }
        discontinuityIdentity = SchedulerValidation.requireId(
                discontinuityIdentity,
                "Scheduler recovery discontinuity identity"
        );
        if (!discontinuityIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Scheduler recovery discontinuity identity has unsupported prefix");
        }
        sourceSchedulerTick = SchedulerValidation.requireTick(sourceSchedulerTick, "Source Scheduler tick");
        authoritativeClockTick = SchedulerValidation.requireTick(authoritativeClockTick, "Authoritative Clock tick");
        inclusiveMissingStartTick = SchedulerValidation.requireTick(
                inclusiveMissingStartTick,
                "Discontinuity start tick"
        );
        inclusiveMissingEndTick = SchedulerValidation.requireTick(inclusiveMissingEndTick, "Discontinuity end tick");
        nextNormalAdmissionTick = SchedulerValidation.requireTick(nextNormalAdmissionTick, "Next admission tick");
        sourceSchedulerSnapshotIdentity = SchedulerValidation.requireId(
                sourceSchedulerSnapshotIdentity,
                "Source Scheduler snapshot identity"
        );
        sourceSchedulerSnapshotDigest = SchedulerIdentityDigest.requireDigest(
                sourceSchedulerSnapshotDigest,
                "Source Scheduler snapshot digest"
        );
        clockSnapshotIdentity = SchedulerValidation.requireId(clockSnapshotIdentity, "Clock snapshot identity");
        clockSnapshotDigest = SchedulerIdentityDigest.requireDigest(clockSnapshotDigest, "Clock snapshot digest");
        recoveryIdentityReference = SchedulerValidation.requireId(
                recoveryIdentityReference,
                "Recovery Identity reference"
        );
        reasonIdentity = SchedulerValidation.requireId(reasonIdentity, "Discontinuity reason identity");
        historicalAcknowledgementIdentities = canonicalIds(
                historicalAcknowledgementIdentities,
                "Historical acknowledgement identity"
        );
        unresolvedWorkReferences = canonicalIds(unresolvedWorkReferences, "Unresolved Work reference");
        contentDigest = SchedulerIdentityDigest.requireDigest(contentDigest, "Discontinuity content digest");
        if (authoritativeClockTick <= sourceSchedulerTick
                || inclusiveMissingStartTick != Math.addExact(sourceSchedulerTick, 1L)
                || inclusiveMissingEndTick != authoritativeClockTick
                || nextNormalAdmissionTick != Math.addExact(authoritativeClockTick, 1L)) {
            throw new IllegalArgumentException("Scheduler recovery discontinuity does not exactly cover the split");
        }
        String expected = calculateDigest(
                sourceSchedulerTick,
                authoritativeClockTick,
                sourceSchedulerSnapshotIdentity,
                sourceSchedulerSnapshotDigest,
                clockSnapshotIdentity,
                clockSnapshotDigest,
                recoveryIdentityReference,
                reasonIdentity,
                historicalAcknowledgementIdentities,
                unresolvedWorkReferences
        );
        if (!contentDigest.equals(expected)
                || !discontinuityIdentity.equals(PREFIX + SchedulerIdentityDigest.digestIdSuffix(
                expected,
                "Discontinuity digest"
        ))) {
            throw new IllegalArgumentException("Scheduler recovery discontinuity is not canonical");
        }
    }

    public static SchedulerRecoveryDiscontinuity create(
            long sourceSchedulerTick,
            long authoritativeClockTick,
            String sourceSchedulerSnapshotIdentity,
            String sourceSchedulerSnapshotDigest,
            String clockSnapshotIdentity,
            String clockSnapshotDigest,
            String recoveryIdentityReference,
            String reasonIdentity,
            List<String> acknowledgementIdentities,
            List<String> unresolvedWorkReferences
    ) {
        List<String> acknowledgements = canonicalIds(
                acknowledgementIdentities,
                "Historical acknowledgement identity"
        );
        List<String> unresolved = canonicalIds(unresolvedWorkReferences, "Unresolved Work reference");
        String digest = calculateDigest(
                sourceSchedulerTick,
                authoritativeClockTick,
                sourceSchedulerSnapshotIdentity,
                sourceSchedulerSnapshotDigest,
                clockSnapshotIdentity,
                clockSnapshotDigest,
                recoveryIdentityReference,
                reasonIdentity,
                acknowledgements,
                unresolved
        );
        return new SchedulerRecoveryDiscontinuity(
                RECOVERY_EVIDENCE_SCHEMA,
                PREFIX + SchedulerIdentityDigest.digestIdSuffix(digest, "Discontinuity digest"),
                sourceSchedulerTick,
                authoritativeClockTick,
                Math.addExact(sourceSchedulerTick, 1L),
                authoritativeClockTick,
                Math.addExact(authoritativeClockTick, 1L),
                sourceSchedulerSnapshotIdentity,
                sourceSchedulerSnapshotDigest,
                clockSnapshotIdentity,
                clockSnapshotDigest,
                recoveryIdentityReference,
                reasonIdentity,
                acknowledgements,
                unresolved,
                digest
        );
    }

    public long missingTickCount() {
        return Math.addExact(Math.subtractExact(inclusiveMissingEndTick, inclusiveMissingStartTick), 1L);
    }

    private static String calculateDigest(
            long schedulerTick,
            long clockTick,
            String schedulerSnapshotIdentity,
            String schedulerSnapshotDigest,
            String clockSnapshotIdentity,
            String clockSnapshotDigest,
            String recoveryIdentity,
            String reason,
            List<String> acknowledgements,
            List<String> unresolved
    ) {
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create(
                "butchercraft:scheduler_recovery_discontinuity"
        ).add(RECOVERY_EVIDENCE_SCHEMA)
                .add(schedulerTick)
                .add(clockTick)
                .add(Math.addExact(schedulerTick, 1L))
                .add(clockTick)
                .add(Math.addExact(clockTick, 1L))
                .add(schedulerSnapshotIdentity)
                .add(sourceDigest(schedulerSnapshotDigest))
                .add(clockSnapshotIdentity)
                .add(sourceDigest(clockSnapshotDigest))
                .add(recoveryIdentity)
                .add(reason)
                .add(acknowledgements.size());
        acknowledgements.forEach(digest::add);
        digest.add(unresolved.size());
        unresolved.forEach(digest::add);
        return digest.finish();
    }

    private static String sourceDigest(String value) {
        return SchedulerIdentityDigest.requireDigest(value, "Scheduler recovery source digest");
    }

    private static List<String> canonicalIds(List<String> values, String label) {
        return Objects.requireNonNull(values, label).stream()
                .map(value -> SchedulerValidation.requireId(value, label))
                .distinct()
                .sorted()
                .toList();
    }
}
