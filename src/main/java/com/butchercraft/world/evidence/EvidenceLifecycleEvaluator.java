package com.butchercraft.world.evidence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EvidenceLifecycleEvaluator {
    public static final int SUPPORTED_POLICY_SCHEMA_VERSION = EvidenceLifecycleSchema.CURRENT_VERSION;

    public EvidenceLifecycleEvaluator() {
    }

    public EvidenceIdentityValidation validateIdentity(EvidenceIdentityCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        List<EvidenceLifecycleFailure> failures = new ArrayList<>();

        if (candidate.ownerId() == null || candidate.ownerId().isEmpty()) {
            failures.add(failure(
                    EvidenceLifecycleFailureCode.MISSING_OWNER,
                    "ownerId",
                    "Evidence identity must name one originating owner"
            ));
        } else if (!EvidenceValidation.isCanonicalId(candidate.ownerId())) {
            failures.add(failure(
                    EvidenceLifecycleFailureCode.INVALID_IDENTITY,
                    "ownerId",
                    "Evidence owner id must be canonical: " + candidate.ownerId()
            ));
        }

        if (candidate.evidenceId() == null || !EvidenceValidation.isCanonicalId(candidate.evidenceId())) {
            failures.add(failure(
                    EvidenceLifecycleFailureCode.INVALID_IDENTITY,
                    "evidenceId",
                    "Evidence id must be canonical: " + candidate.evidenceId()
            ));
        }
        if (candidate.schemaVersion() <= 0) {
            failures.add(failure(
                    EvidenceLifecycleFailureCode.INVALID_IDENTITY,
                    "schemaVersion",
                    "Evidence identity schema version must be positive"
            ));
        }
        candidate.contentIdentity().ifPresent(contentIdentity -> {
            if (!EvidenceValidation.isCanonicalId(contentIdentity)) {
                failures.add(failure(
                        EvidenceLifecycleFailureCode.INVALID_IDENTITY,
                        "contentIdentity",
                        "Content identity must be canonical when supplied: " + contentIdentity
                ));
            }
        });

        if (!failures.isEmpty()) {
            return EvidenceIdentityValidation.failed(failures);
        }
        return EvidenceIdentityValidation.successful(new EvidenceIdentity(
                EvidenceOwnerId.of(candidate.ownerId()),
                candidate.evidenceId(),
                candidate.schemaVersion(),
                candidate.contentIdentity()
        ));
    }

    public List<EvidenceLifecycleFailure> validateClassification(List<EvidenceClass> evidenceClasses) {
        if (evidenceClasses == null || evidenceClasses.isEmpty()) {
            return List.of(failure(
                    EvidenceLifecycleFailureCode.CONTRADICTORY_CLASSIFICATION,
                    "classification",
                    "Evidence must have exactly one canonical lifecycle classification"
            ));
        }

        Set<EvidenceClass> uniqueClasses = new HashSet<>(evidenceClasses);
        if (uniqueClasses.size() != 1 || uniqueClasses.contains(null)) {
            return List.of(failure(
                    EvidenceLifecycleFailureCode.CONTRADICTORY_CLASSIFICATION,
                    "classification",
                    "Evidence Lifecycle foundation accepts one non-overlapping classification"
            ));
        }
        return List.of();
    }

    public Optional<EvidenceLifecycleFailure> detectIdentityConflict(
            EvidenceIdentity first,
            EvidenceIdentity second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.conflictsWith(second)) {
            return Optional.empty();
        }
        return Optional.of(failure(
                EvidenceLifecycleFailureCode.IDENTITY_CONTENT_CONFLICT,
                "contentIdentity",
                "The same owner-issued evidence identity names different content identities"
        ));
    }

    public EvidenceRetentionDecision evaluate(EvidenceRetentionRequest request) {
        Objects.requireNonNull(request, "request");
        EvidenceRetentionPolicy policy = request.policy();
        if (policy.schemaVersion() != SUPPORTED_POLICY_SCHEMA_VERSION) {
            return blocked(
                    EvidenceRetentionReason.UNSUPPORTED_POLICY_VERSION,
                    failure(
                            EvidenceLifecycleFailureCode.UNSUPPORTED_POLICY_VERSION,
                            "policy.schemaVersion",
                            "Unsupported Evidence Lifecycle policy schema version: " + policy.schemaVersion()
                    )
            );
        }

        return switch (request.evidence().classification().primaryClass()) {
            case AUTHORITATIVE_RUNTIME -> evaluateAuthoritativeRuntime(request);
            case PERMANENT_AUDIT -> evaluatePermanentAudit(request);
            case REPLAY_CRITICAL -> evaluateReplayCritical(request);
            case DERIVED_SUMMARY_INDEX -> evaluateDerivedSummary(request);
            case DISPOSABLE_DIAGNOSTIC -> evaluateDisposableDiagnostic(request);
        };
    }

    private EvidenceRetentionDecision evaluateAuthoritativeRuntime(EvidenceRetentionRequest request) {
        if (discardsEvidence(request.requestedDisposition()) || archivesEvidence(request.requestedDisposition())) {
            return blocked(
                    EvidenceRetentionReason.AUTHORITATIVE_FACT_PROTECTED,
                    failure(
                            EvidenceLifecycleFailureCode.RETENTION_DISCARDS_AUTHORITATIVE_EVIDENCE,
                            "requestedDisposition",
                            "Authoritative runtime evidence cannot be expired, rebuilt, or moved by the foundation"
                    )
            );
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.PROTECTED_FROM_DELETION,
                List.of(EvidenceRetentionReason.AUTHORITATIVE_FACT_PROTECTED)
        );
    }

    private EvidenceRetentionDecision evaluatePermanentAudit(EvidenceRetentionRequest request) {
        if (discardsEvidence(request.requestedDisposition())) {
            return blocked(
                    EvidenceRetentionReason.PERMANENT_AUDIT_PROTECTED,
                    failure(
                            EvidenceLifecycleFailureCode.RETENTION_DISCARDS_PERMANENT_AUDIT_EVIDENCE,
                            "requestedDisposition",
                            "Permanent audit evidence cannot expire or be replaced by derived data"
                    )
            );
        }
        if (archivesEvidence(request.requestedDisposition())) {
            return archiveDecision(request, EvidenceRetentionReason.PERMANENT_AUDIT_PROTECTED);
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.PROTECTED_FROM_DELETION,
                List.of(EvidenceRetentionReason.PERMANENT_AUDIT_PROTECTED)
        );
    }

    private EvidenceRetentionDecision evaluateReplayCritical(EvidenceRetentionRequest request) {
        boolean insideReplayHorizon = request.evidence().simulationTick()
                >= request.policy().replayHorizonStartTick();
        if (insideReplayHorizon && discardsEvidence(request.requestedDisposition())) {
            return blocked(
                    EvidenceRetentionReason.WITHIN_REPLAY_HORIZON,
                    failure(
                            EvidenceLifecycleFailureCode.RETENTION_REQUEST_VIOLATES_REPLAY_GUARANTEE,
                            "requestedDisposition",
                            "Replay-critical evidence inside the replay horizon cannot be discarded"
                    )
            );
        }
        if (archivesEvidence(request.requestedDisposition())) {
            return archiveDecision(request, insideReplayHorizon
                    ? EvidenceRetentionReason.WITHIN_REPLAY_HORIZON
                    : EvidenceRetentionReason.OUTSIDE_REPLAY_HORIZON);
        }
        if (insideReplayHorizon) {
            return EvidenceRetentionDecision.allowed(
                    EvidenceLifecycleDisposition.PROTECTED_FROM_DELETION,
                    List.of(EvidenceRetentionReason.WITHIN_REPLAY_HORIZON)
            );
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.RETAIN_HOT,
                List.of(EvidenceRetentionReason.OUTSIDE_REPLAY_HORIZON)
        );
    }

    private EvidenceRetentionDecision evaluateDerivedSummary(EvidenceRetentionRequest request) {
        if (request.requestedDisposition()
                == EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD) {
            if (request.policy().derivedSummaryRebuildEnabled() && request.sourceEvidenceAvailable()) {
                return EvidenceRetentionDecision.allowed(
                        EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD,
                        List.of(
                                EvidenceRetentionReason.DERIVED_SUMMARY_REBUILDABLE,
                                EvidenceRetentionReason.SOURCE_EVIDENCE_RETAINED
                        )
                );
            }
            return blocked(
                    EvidenceRetentionReason.MISSING_REQUIRED_GUARANTEE,
                    failure(
                            EvidenceLifecycleFailureCode.REQUIRED_GUARANTEE_MISSING,
                            "sourceEvidenceAvailable",
                            "Derived summaries can rebuild only while source evidence remains available"
                    )
            );
        }
        if (archivesEvidence(request.requestedDisposition())) {
            return archiveDecision(request, EvidenceRetentionReason.DERIVED_SUMMARY_REBUILDABLE);
        }
        if (request.policy().capacityPressure()
                && request.policy().derivedSummaryRebuildEnabled()
                && request.sourceEvidenceAvailable()) {
            return EvidenceRetentionDecision.allowed(
                    EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD,
                    List.of(
                            EvidenceRetentionReason.CAPACITY_PRESSURE_PRESENT,
                            EvidenceRetentionReason.DERIVED_SUMMARY_REBUILDABLE,
                            EvidenceRetentionReason.SOURCE_EVIDENCE_RETAINED
                    )
            );
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.RETAIN_HOT,
                List.of(EvidenceRetentionReason.POLICY_RETAINS_HOT)
        );
    }

    private EvidenceRetentionDecision evaluateDisposableDiagnostic(EvidenceRetentionRequest request) {
        if (request.requestedDisposition()
                != EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY) {
            return EvidenceRetentionDecision.allowed(
                    EvidenceLifecycleDisposition.RETAIN_HOT,
                    List.of(EvidenceRetentionReason.POLICY_RETAINS_HOT)
            );
        }
        if (request.evidence().citedByAuthoritativeEvidence()) {
            return blocked(
                    EvidenceRetentionReason.AUTHORITATIVE_FACT_PROTECTED,
                    failure(
                            EvidenceLifecycleFailureCode.RETENTION_REQUEST_VIOLATES_REPLAY_GUARANTEE,
                            "citedByAuthoritativeEvidence",
                            "Diagnostic evidence cited by authoritative evidence is not disposable"
                    )
            );
        }
        if (!request.policy().diagnosticExpiryEnabled()) {
            return EvidenceRetentionDecision.allowed(
                    EvidenceLifecycleDisposition.RETAIN_HOT,
                    List.of(EvidenceRetentionReason.DIAGNOSTIC_EXPIRY_NOT_ENABLED)
            );
        }
        if (request.evidence().simulationTick() < request.policy().diagnosticExpiryBeforeTick()) {
            return EvidenceRetentionDecision.allowed(
                    EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY,
                    List.of(EvidenceRetentionReason.DIAGNOSTIC_EXPIRY_EXPLICITLY_ENABLED)
            );
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.RETAIN_HOT,
                List.of(EvidenceRetentionReason.DIAGNOSTIC_STILL_INSIDE_POLICY_WINDOW)
        );
    }

    private EvidenceRetentionDecision archiveDecision(
            EvidenceRetentionRequest request,
            EvidenceRetentionReason protectionReason
    ) {
        if (!request.policy().archivePublicationAvailable()) {
            return blocked(
                    EvidenceRetentionReason.MISSING_REQUIRED_GUARANTEE,
                    failure(
                            EvidenceLifecycleFailureCode.REQUIRED_GUARANTEE_MISSING,
                            "archivePublicationAvailable",
                            "Archive eligibility requires an explicit archive publication guarantee"
                    )
            );
        }
        return EvidenceRetentionDecision.allowed(
                EvidenceLifecycleDisposition.ELIGIBLE_FOR_ARCHIVE,
                List.of(protectionReason, EvidenceRetentionReason.ARCHIVE_PUBLICATION_AVAILABLE)
        );
    }

    private EvidenceRetentionDecision blocked(
            EvidenceRetentionReason reason,
            EvidenceLifecycleFailure failure
    ) {
        return EvidenceRetentionDecision.blocked(List.of(reason), List.of(failure));
    }

    private EvidenceLifecycleFailure failure(
            EvidenceLifecycleFailureCode code,
            String field,
            String message
    ) {
        return new EvidenceLifecycleFailure(code, field, message);
    }

    private static boolean discardsEvidence(EvidenceLifecycleDisposition disposition) {
        return disposition == EvidenceLifecycleDisposition.ELIGIBLE_FOR_DETERMINISTIC_DIAGNOSTIC_EXPIRY
                || disposition == EvidenceLifecycleDisposition.ELIGIBLE_FOR_DERIVED_DATA_REBUILD;
    }

    private static boolean archivesEvidence(EvidenceLifecycleDisposition disposition) {
        return disposition == EvidenceLifecycleDisposition.ELIGIBLE_FOR_ARCHIVE
                || disposition == EvidenceLifecycleDisposition.RETAINED_IN_COLD_ARCHIVE;
    }
}
