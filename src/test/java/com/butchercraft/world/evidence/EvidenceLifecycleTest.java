package com.butchercraft.world.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceLifecycleTest {
    private static final EvidenceLifecycleEvaluator EVALUATOR = new EvidenceLifecycleEvaluator();
    private static final EvidenceOwnerId OWNER = EvidenceOwnerId.of("butchercraft:transactions");
    private static final String CONTENT_A = "sha256:" + "a".repeat(64);
    private static final String CONTENT_B = "sha256:" + "b".repeat(64);

    @Test
    void everyCanonicalEvidenceClassIsRepresentedByOnePrimaryClassification() {
        assertEquals(List.of(
                EvidenceClass.AUTHORITATIVE_RUNTIME,
                EvidenceClass.PERMANENT_AUDIT,
                EvidenceClass.REPLAY_CRITICAL,
                EvidenceClass.DERIVED_SUMMARY_INDEX,
                EvidenceClass.DISPOSABLE_DIAGNOSTIC
        ), List.of(EvidenceClass.values()));

        for (EvidenceClass evidenceClass : EvidenceClass.values()) {
            EvidenceClassification classification = EvidenceClassification.of(evidenceClass);
            assertEquals(evidenceClass, classification.primaryClass());
            assertTrue(EVALUATOR.validateClassification(List.of(evidenceClass)).isEmpty());
        }
    }

    @Test
    void contradictoryClassificationsFailExplicitly() {
        List<EvidenceLifecycleFailure> failures = EVALUATOR.validateClassification(List.of(
                EvidenceClass.PERMANENT_AUDIT,
                EvidenceClass.DISPOSABLE_DIAGNOSTIC
        ));

        assertEquals(1, failures.size());
        assertEquals(EvidenceLifecycleFailureCode.CONTRADICTORY_CLASSIFICATION, failures.getFirst().code());
    }

    @Test
    void evidenceDescriptorPreservesOneOriginatingOwner() {
        EvidenceDescriptor descriptor = descriptor(EvidenceClass.REPLAY_CRITICAL, 50L, false);

        assertEquals(OWNER, descriptor.identity().ownerId());
        assertEquals(OWNER, descriptor.source().ownerId());
        assertEquals(descriptor.identity().ownerId(), descriptor.source().ownerId());
    }

    @Test
    void identityValidationReportsMissingOwnerAndInvalidIdentityAsTypedFailures() {
        EvidenceIdentityValidation validation = EVALUATOR.validateIdentity(
                new EvidenceIdentityCandidate("", "Bad Identity", EvidenceLifecycleSchema.CURRENT_VERSION, CONTENT_A)
        );

        assertFalse(validation.successful());
        assertTrue(validation.failures().stream()
                .anyMatch(failure -> failure.code() == EvidenceLifecycleFailureCode.MISSING_OWNER));
        assertTrue(validation.failures().stream()
                .anyMatch(failure -> failure.code() == EvidenceLifecycleFailureCode.INVALID_IDENTITY));
    }

    @Test
    void sameOwnerIssuedIdentityWithDifferentContentIsAConflict() {
        EvidenceIdentity first = identity("butchercraft:evidence/transaction_result_1", CONTENT_A);
        EvidenceIdentity second = identity("butchercraft:evidence/transaction_result_1", CONTENT_B);

        assertTrue(first.sameEntityIdentityAs(second));
        assertTrue(first.conflictsWith(second));
        assertEquals(
                EvidenceLifecycleFailureCode.IDENTITY_CONTENT_CONFLICT,
                EVALUATOR.detectIdentityConflict(first, second).orElseThrow().code()
        );
    }

    @Test
    void permanentAuditEvidenceCannotExpire() {
        EvidenceRetentionDecision decision = EVALUATOR.evaluate(request(
                descriptor(EvidenceClass.PERMANENT_AUDIT, 10L, false),
                policy(true, true, true, false, 0L, 20L),
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                true
        ));

        assertEquals(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                decision.disposition()
        );
        assertEquals(
                EvidenceLifecycleFailureCode.RETENTION_DISCARDS_PERMANENT_AUDIT_EVIDENCE,
                decision.failures().getFirst().code()
        );
    }

    @Test
    void authoritativeRuntimeEvidenceCannotSilentlyExpireOrMove() {
        EvidenceRetentionDecision decision = EVALUATOR.evaluate(request(
                descriptor(EvidenceClass.AUTHORITATIVE_RUNTIME, 10L, false),
                policy(true, true, true, false, 0L, 20L),
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_ARCHIVE,
                true
        ));

        assertEquals(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                decision.disposition()
        );
        assertEquals(
                EvidenceLifecycleFailureCode.RETENTION_DISCARDS_AUTHORITATIVE_EVIDENCE,
                decision.failures().getFirst().code()
        );
    }

    @Test
    void replayCriticalEvidenceIsProtectedInsideReplayHorizon() {
        EvidenceRetentionDecision decision = EVALUATOR.evaluate(request(
                descriptor(EvidenceClass.REPLAY_CRITICAL, 50L, false),
                policy(false, true, true, false, 40L, 60L),
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                true
        ));

        assertEquals(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                decision.disposition()
        );
        assertEquals(
                EvidenceLifecycleFailureCode.RETENTION_REQUEST_VIOLATES_REPLAY_GUARANTEE,
                decision.failures().getFirst().code()
        );
    }

    @Test
    void disposableDiagnosticsExpireOnlyWithExplicitPolicy() {
        EvidenceDescriptor diagnostic = descriptor(EvidenceClass.DISPOSABLE_DIAGNOSTIC, 10L, false);
        EvidenceLifecycleDisposition requested =
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY;

        EvidenceRetentionDecision retained = EVALUATOR.evaluate(request(
                diagnostic,
                policy(false, false, true, false, 0L, 20L),
                requested,
                true
        ));
        EvidenceRetentionDecision expired = EVALUATOR.evaluate(request(
                diagnostic,
                policy(false, true, true, false, 0L, 20L),
                requested,
                true
        ));

        assertEquals(EvidenceLifecycleDisposition.RETAIN_HOT, retained.disposition());
        assertTrue(retained.reasons().contains(EvidenceRetentionReason.DIAGNOSTIC_EXPIRY_NOT_ENABLED));
        assertEquals(
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                expired.disposition()
        );
    }

    @Test
    void derivedSummariesCanRebuildOnlyWhenSourceEvidenceRemainsAvailable() {
        EvidenceDescriptor summary = descriptor(EvidenceClass.DERIVED_SUMMARY_INDEX, 70L, false);
        EvidenceLifecycleDisposition requested =
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD;

        EvidenceRetentionDecision rebuildable = EVALUATOR.evaluate(request(
                summary,
                policy(false, false, true, true, 0L, 100L),
                requested,
                true
        ));
        EvidenceRetentionDecision blocked = EVALUATOR.evaluate(request(
                summary,
                policy(false, false, true, true, 0L, 100L),
                requested,
                false
        ));

        assertEquals(EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD, rebuildable.disposition());
        assertEquals(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                blocked.disposition()
        );
        assertEquals(EvidenceLifecycleFailureCode.REQUIRED_GUARANTEE_MISSING, blocked.failures().getFirst().code());
    }

    @Test
    void identicalInputsProduceIdenticalRetentionDecisions() {
        EvidenceRetentionRequest request = request(
                descriptor(EvidenceClass.DISPOSABLE_DIAGNOSTIC, 10L, false),
                policy(false, true, false, false, 0L, 20L),
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                true
        );

        assertEquals(EVALUATOR.evaluate(request), EVALUATOR.evaluate(request));
    }

    @Test
    void unsupportedPolicyVersionFailsExplicitly() {
        EvidenceRetentionDecision decision = EVALUATOR.evaluate(request(
                descriptor(EvidenceClass.REPLAY_CRITICAL, 10L, false),
                new EvidenceRetentionPolicy(
                        "butchercraft:evidence_policy/test",
                        99,
                        100L,
                        0L,
                        20L,
                        10,
                        10,
                        false,
                        true,
                        true,
                        false
                ),
                EvidenceLifecycleDisposition.RETAIN_HOT,
                true
        ));

        assertEquals(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                decision.disposition()
        );
        assertEquals(EvidenceLifecycleFailureCode.UNSUPPORTED_POLICY_VERSION, decision.failures().getFirst().code());
    }

    @Test
    void numericRetentionThresholdsArePolicyInputs() {
        EvidenceDescriptor diagnostic = descriptor(EvidenceClass.DISPOSABLE_DIAGNOSTIC, 50L, false);
        EvidenceLifecycleDisposition requested =
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY;

        EvidenceRetentionDecision retained = EVALUATOR.evaluate(request(
                diagnostic,
                policy(false, true, false, false, 0L, 40L),
                requested,
                true
        ));
        EvidenceRetentionDecision expired = EVALUATOR.evaluate(request(
                diagnostic,
                policy(false, true, false, false, 0L, 60L),
                requested,
                true
        ));

        assertEquals(EvidenceLifecycleDisposition.RETAIN_HOT, retained.disposition());
        assertEquals(
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                expired.disposition()
        );
    }

    private static EvidenceRetentionRequest request(
            EvidenceDescriptor descriptor,
            EvidenceRetentionPolicy policy,
            EvidenceLifecycleDisposition requestedDisposition,
            boolean sourceEvidenceAvailable
    ) {
        return new EvidenceRetentionRequest(descriptor, policy, requestedDisposition, sourceEvidenceAvailable);
    }

    private static EvidenceDescriptor descriptor(EvidenceClass evidenceClass, long tick, boolean cited) {
        return new EvidenceDescriptor(
                identity("butchercraft:evidence/" + evidenceClass.name().toLowerCase() + "_" + tick, CONTENT_A),
                new EvidenceSource(
                        OWNER,
                        "butchercraft:evidence_source/transaction",
                        "butchercraft:source/transaction_" + tick,
                        EvidenceLifecycleSchema.CURRENT_VERSION
                ),
                EvidenceClassification.of(evidenceClass),
                tick,
                tick,
                EvidenceLifecycleSchema.CURRENT_VERSION,
                cited
        );
    }

    private static EvidenceIdentity identity(String value, String contentIdentity) {
        return new EvidenceIdentity(
                OWNER,
                value,
                EvidenceLifecycleSchema.CURRENT_VERSION,
                Optional.of(contentIdentity)
        );
    }

    private static EvidenceRetentionPolicy policy(
            boolean archiveAvailable,
            boolean diagnosticExpiryEnabled,
            boolean derivedSummaryRebuildEnabled,
            boolean capacityPressure,
            long replayHorizonStartTick,
            long diagnosticExpiryBeforeTick
    ) {
        return new EvidenceRetentionPolicy(
                "butchercraft:evidence_policy/test",
                EvidenceLifecycleSchema.CURRENT_VERSION,
                100L,
                replayHorizonStartTick,
                diagnosticExpiryBeforeTick,
                10,
                10,
                archiveAvailable,
                diagnosticExpiryEnabled,
                derivedSummaryRebuildEnabled,
                capacityPressure
        );
    }
}
