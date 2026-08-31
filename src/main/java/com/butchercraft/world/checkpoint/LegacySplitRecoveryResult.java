package com.butchercraft.world.checkpoint;

import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryResult(
        int schemaVersion,
        String resultIdentity,
        LegacySplitRecoveryIdentity recoveryIdentity,
        String analysisDigest,
        String authorizationIdentity,
        RecoveryOperatorAuthorization.Disposition authorizedDisposition,
        RecoveryOperatorEvidence operatorEvidence,
        List<RecoverySourceSnapshot> sourceSnapshots,
        List<PublishedAcknowledgement> publishedAcknowledgements,
        PublishedDiscontinuity publishedDiscontinuity,
        List<PreservedAuthorizedWork> preservedAuthorizedWork,
        List<RecoveryAuthorityBlock> authorityBlocks,
        List<LegacySplitRecoveryPublicationIntent.PreparedOwnerReference> ownerSnapshots,
        CheckpointGenerationId recoveryGenerationId,
        String generationManifestDigest,
        CommittedHead committedHead,
        RecoveryMutationGate mutationGate,
        List<PublicationDiagnostic> publicationDiagnostics,
        PublicationOutcome publicationOutcome,
        Optional<String> completionTimestampMetadata,
        String contentDigest
) {
    public static final int CURRENT_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:legacy_split_recovery_result/v1/";

    public LegacySplitRecoveryResult {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported legacy split recovery-result schema");
        }
        resultIdentity = CheckpointValidation.id(resultIdentity, "recoveryResultIdentity");
        recoveryIdentity = Objects.requireNonNull(recoveryIdentity, "recoveryIdentity");
        analysisDigest = CheckpointValidation.digest(analysisDigest, "analysisDigest");
        authorizationIdentity = CheckpointValidation.id(authorizationIdentity, "authorizationIdentity");
        authorizedDisposition = Objects.requireNonNull(authorizedDisposition, "authorizedDisposition");
        operatorEvidence = Objects.requireNonNull(operatorEvidence, "operatorEvidence");
        sourceSnapshots = Objects.requireNonNull(sourceSnapshots, "sourceSnapshots").stream().sorted().toList();
        publishedAcknowledgements = Objects.requireNonNull(
                publishedAcknowledgements,
                "publishedAcknowledgements"
        ).stream().sorted().toList();
        publishedDiscontinuity = Objects.requireNonNull(publishedDiscontinuity, "publishedDiscontinuity");
        preservedAuthorizedWork = Objects.requireNonNull(
                preservedAuthorizedWork,
                "preservedAuthorizedWork"
        ).stream().sorted().toList();
        authorityBlocks = Objects.requireNonNull(authorityBlocks, "authorityBlocks").stream().sorted().toList();
        ownerSnapshots = Objects.requireNonNull(ownerSnapshots, "ownerSnapshots").stream().sorted().toList();
        recoveryGenerationId = Objects.requireNonNull(recoveryGenerationId, "recoveryGenerationId");
        generationManifestDigest = CheckpointValidation.digest(
                generationManifestDigest,
                "generationManifestDigest"
        );
        committedHead = Objects.requireNonNull(committedHead, "committedHead");
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        publicationDiagnostics = Objects.requireNonNull(
                publicationDiagnostics,
                "publicationDiagnostics"
        ).stream().sorted().toList();
        publicationOutcome = Objects.requireNonNull(publicationOutcome, "publicationOutcome");
        completionTimestampMetadata = Objects.requireNonNull(
                completionTimestampMetadata,
                "completionTimestampMetadata"
        ).map(value -> CheckpointValidation.text(value, "completionTimestampMetadata"));
        contentDigest = CheckpointValidation.digest(contentDigest, "recoveryResultContentDigest");
        String expected = calculateDigest(
                recoveryIdentity,
                analysisDigest,
                authorizationIdentity,
                authorizedDisposition,
                operatorEvidence,
                sourceSnapshots,
                publishedAcknowledgements,
                publishedDiscontinuity,
                preservedAuthorizedWork,
                authorityBlocks,
                ownerSnapshots,
                recoveryGenerationId,
                generationManifestDigest,
                committedHead,
                mutationGate,
                publicationDiagnostics,
                publicationOutcome
        );
        if (!contentDigest.equals(expected)
                || !resultIdentity.equals(PREFIX + expected.substring("sha256:".length()))) {
            throw new IllegalArgumentException("Legacy split recovery result is not canonical");
        }
    }

    public static LegacySplitRecoveryResult committed(
            SplitSnapshotRecoveryPlan plan,
            RecoveryOperatorAuthorization authorization,
            LegacySplitRecoveryPublicationIntent intent,
            CheckpointGenerationManifest manifest,
            CheckpointHeadRecord head,
            List<CheckpointFailure> checkpointDiagnostics,
            Optional<String> completionTimestampMetadata
    ) {
        List<PublishedAcknowledgement> acknowledgements = plan.historicalAcknowledgements().stream()
                .map(PublishedAcknowledgement::from)
                .toList();
        PublishedDiscontinuity discontinuity = PublishedDiscontinuity.from(
                plan.recoveryDiscontinuity().orElseThrow()
        );
        CommittedHead committedHead = CommittedHead.from(head);
        RecoveryMutationGate gate = RecoveryMutationGate.fromPlan(plan);
        List<PublicationDiagnostic> diagnostics = checkpointDiagnostics.stream()
                .map(PublicationDiagnostic::from)
                .toList();
        String digest = calculateDigest(
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                authorization.authorizationIdentity(),
                authorization.disposition(),
                authorization.operatorEvidence(),
                plan.sourceSnapshots(),
                acknowledgements,
                discontinuity,
                plan.preservedAuthorizedWork(),
                plan.authorityBlocks(),
                intent.preparedOwners(),
                manifest.generationId(),
                manifest.manifestDigest(),
                committedHead,
                gate,
                diagnostics,
                PublicationOutcome.COMMITTED
        );
        return new LegacySplitRecoveryResult(
                CURRENT_SCHEMA,
                PREFIX + digest.substring("sha256:".length()),
                plan.recoveryIdentity(),
                plan.analysisDigest(),
                authorization.authorizationIdentity(),
                authorization.disposition(),
                authorization.operatorEvidence(),
                plan.sourceSnapshots(),
                acknowledgements,
                discontinuity,
                plan.preservedAuthorizedWork(),
                plan.authorityBlocks(),
                intent.preparedOwners(),
                manifest.generationId(),
                manifest.manifestDigest(),
                committedHead,
                gate,
                diagnostics,
                PublicationOutcome.COMMITTED,
                completionTimestampMetadata,
                digest
        );
    }

    private static String calculateDigest(
            LegacySplitRecoveryIdentity recoveryIdentity,
            String analysisDigest,
            String authorizationIdentity,
            RecoveryOperatorAuthorization.Disposition disposition,
            RecoveryOperatorEvidence operator,
            List<RecoverySourceSnapshot> sources,
            List<PublishedAcknowledgement> acknowledgements,
            PublishedDiscontinuity discontinuity,
            List<PreservedAuthorizedWork> preserved,
            List<RecoveryAuthorityBlock> blocks,
            List<LegacySplitRecoveryPublicationIntent.PreparedOwnerReference> owners,
            CheckpointGenerationId generation,
            String manifestDigest,
            CommittedHead head,
            RecoveryMutationGate gate,
            List<PublicationDiagnostic> publicationDiagnostics,
            PublicationOutcome outcome
    ) {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:legacy_split_recovery_result"
        ).add(CURRENT_SCHEMA)
                .add(recoveryIdentity.value())
                .add(analysisDigest)
                .add(authorizationIdentity)
                .add(disposition.name())
                .add(operator.principalIdentity())
                .add(operator.authority().name())
                .add(operator.evidenceIdentity())
                .add(operator.evidenceContentDigest())
                .add(sources.size());
        sources.stream().sorted().forEach(source -> digest
                .add(source.ownerId().value())
                .add(source.ownerSchemaVersion())
                .add(source.snapshotIdentity())
                .add(source.contentDigest())
                .add(source.ownerRevisionOrSequence())
                .add(source.representedSimulationTick()));
        digest.add(acknowledgements.size());
        acknowledgements.stream().sorted().forEach(value -> digest
                .add(value.identity())
                .add(value.contentDigest())
                .add(value.executionOperationIdentity())
                .add(value.terminalOutcome()));
        digest.add(discontinuity.identity())
                .add(discontinuity.contentDigest())
                .add(discontinuity.inclusiveStartTick())
                .add(discontinuity.inclusiveEndTick())
                .add(discontinuity.nextAdmissionTick())
                .add(preserved.size());
        preserved.stream().sorted().forEach(value -> digest
                .add(value.executionOperationIdentity())
                .add(value.machineRunIdentity())
                .add(value.machineRunGeneration())
                .add(value.childIdentity())
                .add(value.childSequence())
                .add(value.workstationInstanceIdentity())
                .add(value.workstationInstanceGeneration())
                .add(value.restartPolicyIdentity()));
        digest.add(blocks.size());
        blocks.stream().sorted().forEach(value -> digest
                .add(value.blockIdentity())
                .add(value.ownerBlockContentDigest())
                .add(value.scope().name()));
        digest.add(owners.size());
        owners.stream().sorted().forEach(value -> digest
                .add(value.ownerId().value())
                .add(value.snapshotIdentity())
                .add(value.contentDigest())
                .add(value.sourceSnapshotIdentity())
                .add(value.sourceSnapshotContentDigest())
                .add(value.ownerSequence()));
        digest.add(generation.canonicalValue())
                .add(manifestDigest)
                .add(head.identity())
                .add(head.contentDigest())
                .add(head.sequence())
                .add(gate.wholeWorldConsequentialMutationBlocked())
                .add(gate.blockedAuthorityIdentities().size());
        gate.blockedAuthorityIdentities().forEach(digest::add);
        digest.add(gate.blockIdentities().size());
        gate.blockIdentities().forEach(digest::add);
        digest.add(publicationDiagnostics.size());
        publicationDiagnostics.stream().sorted().forEach(value -> digest
                .add(value.code())
                .add(value.field())
                .add(value.message()));
        return digest.add(outcome.name()).finish();
    }

    public enum PublicationOutcome {
        COMMITTED
    }

    public record PublishedAcknowledgement(
            String identity,
            String contentDigest,
            String executionOperationIdentity,
            String terminalOutcome
    ) implements Comparable<PublishedAcknowledgement> {
        public PublishedAcknowledgement {
            identity = CheckpointValidation.id(identity, "publishedAcknowledgementIdentity");
            contentDigest = recoveryEvidenceDigest(contentDigest, "publishedAcknowledgementDigest");
            executionOperationIdentity = CheckpointValidation.id(
                    executionOperationIdentity,
                    "publishedExecutionOperationIdentity"
            );
            terminalOutcome = CheckpointValidation.text(terminalOutcome, "publishedTerminalOutcome");
        }

        static PublishedAcknowledgement from(HistoricalCoordinationAcknowledgement value) {
            return new PublishedAcknowledgement(
                    value.acknowledgementIdentity(),
                    value.contentDigest(),
                    value.executionOperationIdentity(),
                    value.terminalOutcome().name()
            );
        }

        @Override
        public int compareTo(PublishedAcknowledgement other) {
            return identity.compareTo(Objects.requireNonNull(other, "other").identity);
        }
    }

    public record PublishedDiscontinuity(
            String identity,
            String contentDigest,
            long inclusiveStartTick,
            long inclusiveEndTick,
            long nextAdmissionTick
    ) {
        public PublishedDiscontinuity {
            identity = CheckpointValidation.id(identity, "publishedDiscontinuityIdentity");
            contentDigest = recoveryEvidenceDigest(contentDigest, "publishedDiscontinuityDigest");
            inclusiveStartTick = CheckpointValidation.nonNegative(inclusiveStartTick, "inclusiveStartTick");
            inclusiveEndTick = CheckpointValidation.nonNegative(inclusiveEndTick, "inclusiveEndTick");
            nextAdmissionTick = CheckpointValidation.nonNegative(nextAdmissionTick, "nextAdmissionTick");
        }

        static PublishedDiscontinuity from(SchedulerRecoveryDiscontinuity value) {
            return new PublishedDiscontinuity(
                    value.discontinuityIdentity(),
                    value.contentDigest(),
                    value.inclusiveMissingStartTick(),
                    value.inclusiveMissingEndTick(),
                    value.nextNormalAdmissionTick()
            );
        }
    }

    public record CommittedHead(long sequence, String identity, String contentDigest) {
        public CommittedHead {
            sequence = CheckpointValidation.positive(sequence, "committedHeadSequence");
            identity = CheckpointValidation.id(identity, "committedHeadIdentity");
            contentDigest = CheckpointValidation.digest(contentDigest, "committedHeadContentDigest");
        }

        static CommittedHead from(CheckpointHeadRecord head) {
            return new CommittedHead(
                    head.headSequence(),
                    "butchercraft:checkpoint_head/" + head.headSequence() + "/"
                            + head.headRecordDigest().substring("sha256:".length()),
                    head.headRecordDigest()
            );
        }
    }

    public record PublicationDiagnostic(String code, String field, String message)
            implements Comparable<PublicationDiagnostic> {
        public PublicationDiagnostic {
            code = CheckpointValidation.text(code, "publicationDiagnosticCode");
            field = CheckpointValidation.text(field, "publicationDiagnosticField");
            message = CheckpointValidation.text(message, "publicationDiagnosticMessage");
        }

        static PublicationDiagnostic from(CheckpointFailure failure) {
            return new PublicationDiagnostic(failure.code().name(), failure.field(), failure.message());
        }

        @Override
        public int compareTo(PublicationDiagnostic other) {
            int codeComparison = code.compareTo(Objects.requireNonNull(other, "other").code);
            return codeComparison != 0 ? codeComparison : field.compareTo(other.field);
        }
    }

    private static String recoveryEvidenceDigest(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (!normalized.equals(value)
                || (!normalized.matches("[0-9a-f]{64}")
                && !normalized.matches("sha256:[0-9a-f]{64}"))) {
            throw new IllegalArgumentException(field + " must be canonical SHA-256 evidence");
        }
        return normalized;
    }
}
