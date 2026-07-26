package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.TransactionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TransactionValidationBindingEvaluator {
    public TransactionBindingValidationResult validateBindingCandidate(
            TransactionValidationBindingCandidate candidate,
            List<ValidationInputIdentity> requiredInputs
    ) {
        Objects.requireNonNull(candidate, "candidate");
        List<TransactionBindingFailure> failures = new ArrayList<>();
        requireCurrentSchema(candidate.schemaVersion(), "binding", failures);
        requirePresent(candidate.transactionId(), "transactionId", failures);
        requirePresent(candidate.proposalIdentity(), "proposalIdentity", failures);
        requirePresent(candidate.inventoryFreshnessIdentity(), "inventoryFreshnessIdentity", failures);
        requirePresent(candidate.validationPlanIdentity(), "validationPlanIdentity", failures);
        candidate.proposalIdentity().ifPresent(proposalIdentity -> candidate.transactionId().ifPresent(transactionId -> {
            if (!proposalIdentity.transactionId().equals(transactionId)) {
                failures.add(new TransactionBindingFailure(
                        TransactionBindingFailureCode.PROPOSAL_IDENTITY_MISMATCH,
                        "proposalIdentity",
                        "Proposal Identity must authorize the same Transaction identity as the binding"
                ));
            }
        }));
        failures.addAll(validateValidationInputs(candidate.validationInputIdentities(), requiredInputs));
        return new TransactionBindingValidationResult(failures);
    }

    public TransactionBindingValidationResult validateBinding(
            TransactionValidationBinding binding,
            List<ValidationInputIdentity> requiredInputs
    ) {
        return validateBindingCandidate(TransactionValidationBindingCandidate.from(binding), requiredInputs);
    }

    public TransactionBindingValidationResult validateBindingAgainst(
            TransactionValidationBinding binding,
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            TransactionValidationPlanIdentity validationPlanIdentity
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        Objects.requireNonNull(inventoryFreshnessIdentity, "inventoryFreshnessIdentity");
        Objects.requireNonNull(validationPlanIdentity, "validationPlanIdentity");
        List<TransactionBindingFailure> failures = new ArrayList<>();
        requireCurrentSchema(binding.schemaVersion(), "binding", failures);
        if (!binding.proposalIdentity().equals(proposalIdentity)) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.PROPOSAL_IDENTITY_MISMATCH,
                    "proposalIdentity",
                    "Binding proposal identity does not match the expected proposal identity"
            ));
        }
        if (!binding.inventoryFreshnessIdentity().equals(inventoryFreshnessIdentity)) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.INVENTORY_FRESHNESS_IDENTITY_MISMATCH,
                    "inventoryFreshnessIdentity",
                    "Binding Inventory Freshness Identity does not match the expected Inventory state"
            ));
        }
        if (!binding.validationPlanIdentity().equals(validationPlanIdentity)) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.VALIDATION_PLAN_IDENTITY_MISMATCH,
                    "validationPlanIdentity",
                    "Binding Validation Plan Identity does not match the expected validation plan"
            ));
        }
        return new TransactionBindingValidationResult(failures);
    }

    public TransactionBindingValidationResult validateResultEvidence(
            AuthoritativeTransactionResultEvidence evidence,
            TransactionValidationBinding binding
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(binding, "binding");
        List<TransactionBindingFailure> failures = new ArrayList<>();
        requireCurrentSchema(evidence.schemaVersion(), "resultEvidence", failures);
        if (!evidence.digestMatches()) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.RESULT_EVIDENCE_DIGEST_MISMATCH,
                    "resultContentDigest",
                    "Authoritative Transaction result evidence digest does not match its content"
            ));
        }
        if (!evidence.transactionId().equals(binding.transactionId())) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.RESULT_EVIDENCE_MISMATCH,
                    "transactionId",
                    "Result evidence Transaction identity does not match the validation binding"
            ));
        }
        if (!evidence.proposalIdentity().equals(binding.proposalIdentity())) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.PROPOSAL_IDENTITY_MISMATCH,
                    "proposalIdentity",
                    "Result evidence Proposal Identity does not match the validation binding"
            ));
        }
        if (!evidence.startingInventoryFreshnessIdentity().equals(binding.inventoryFreshnessIdentity())) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.INVENTORY_FRESHNESS_IDENTITY_MISMATCH,
                    "startingInventoryFreshnessIdentity",
                    "Result evidence starting Inventory Freshness Identity does not match the validation binding"
            ));
        }
        if (!evidence.validationPlanIdentity().equals(binding.validationPlanIdentity())) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.VALIDATION_PLAN_IDENTITY_MISMATCH,
                    "validationPlanIdentity",
                    "Result evidence Validation Plan Identity does not match the validation binding"
            ));
        }
        if (!evidence.validationInputIdentities().equals(binding.validationInputIdentities())) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.RESULT_EVIDENCE_MISMATCH,
                    "validationInputIdentities",
                    "Result evidence validation inputs do not match the validation binding"
            ));
        }
        return new TransactionBindingValidationResult(failures);
    }

    public TransactionDuplicateDecision classifySubmission(
            TransactionId transactionId,
            TransactionProposalIdentity proposalIdentity,
            Optional<AuthoritativeTransactionResultEvidence> existingEvidence
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        Objects.requireNonNull(existingEvidence, "existingEvidence");
        if (existingEvidence.isEmpty()) {
            return TransactionDuplicateDecision.newTransaction();
        }
        AuthoritativeTransactionResultEvidence existing = existingEvidence.orElseThrow();
        if (!existing.transactionId().equals(transactionId)) {
            return TransactionDuplicateDecision.newTransaction();
        }
        if (existing.proposalIdentity().equals(proposalIdentity)) {
            return TransactionDuplicateDecision.duplicateObservation(existing);
        }
        return TransactionDuplicateDecision.conflict(
                TransactionDuplicateOutcome.TRANSACTION_IDENTITY_CONFLICT,
                existing,
                new TransactionBindingFailure(
                        TransactionBindingFailureCode.TRANSACTION_IDENTITY_CONFLICT,
                        "transactionId",
                        "Same Transaction identity cannot authorize a different canonical proposal identity"
                )
        );
    }

    public TransactionDuplicateOutcome classifyIdentityContent(
            TransactionIdentityContent submitted,
            Optional<TransactionIdentityContent> existing
    ) {
        Objects.requireNonNull(submitted, "submitted");
        Objects.requireNonNull(existing, "existing");
        if (existing.isEmpty()) {
            return TransactionDuplicateOutcome.NEW_TRANSACTION;
        }
        TransactionIdentityContent existingContent = existing.orElseThrow();
        if (!existingContent.identity().equals(submitted.identity())) {
            return TransactionDuplicateOutcome.NEW_TRANSACTION;
        }
        if (existingContent.contentDigest().equals(submitted.contentDigest())
                && existingContent.schemaVersion() == submitted.schemaVersion()) {
            return TransactionDuplicateOutcome.DUPLICATE_OBSERVATION;
        }
        return TransactionDuplicateOutcome.CONTENT_IDENTITY_CONFLICT;
    }

    private static List<TransactionBindingFailure> validateValidationInputs(
            List<ValidationInputIdentity> observedInputs,
            List<ValidationInputIdentity> requiredInputs
    ) {
        Objects.requireNonNull(observedInputs, "observedInputs");
        Objects.requireNonNull(requiredInputs, "requiredInputs");
        List<TransactionBindingFailure> failures = new ArrayList<>();
        Map<String, ValidationInputIdentity> observedByIdentity = new HashMap<>();
        for (ValidationInputIdentity observed : observedInputs) {
            ValidationInputIdentity previous = observedByIdentity.putIfAbsent(observed.identity(), observed);
            if (previous != null && !previous.equals(observed)) {
                failures.add(new TransactionBindingFailure(
                        TransactionBindingFailureCode.CONTENT_IDENTITY_CONFLICT,
                        "validationInputIdentities",
                        "Validation input identity has conflicting content: " + observed.identity()
                ));
            }
        }
        for (ValidationInputIdentity required : requiredInputs) {
            ValidationInputIdentity observed = observedByIdentity.get(required.identity());
            if (observed == null) {
                failures.add(new TransactionBindingFailure(
                        TransactionBindingFailureCode.MISSING_VALIDATION_INPUT,
                        "validationInputIdentities",
                        "Required validation input is missing: " + required.identity()
                ));
            } else if (!observed.equals(required)) {
                failures.add(new TransactionBindingFailure(
                        TransactionBindingFailureCode.CONTENT_IDENTITY_CONFLICT,
                        "validationInputIdentities",
                        "Required validation input content does not match: " + required.identity()
                ));
            }
        }
        for (ValidationInputIdentity observed : observedInputs) {
            boolean required = requiredInputs.stream()
                    .anyMatch(input -> input.identity().equals(observed.identity()));
            if (!required) {
                failures.add(new TransactionBindingFailure(
                        TransactionBindingFailureCode.HIDDEN_VALIDATION_INPUT,
                        "validationInputIdentities",
                        "Validation must not depend on hidden runtime context: " + observed.identity()
                ));
            }
        }
        return failures;
    }

    private static void requireCurrentSchema(
            int schemaVersion,
            String field,
            List<TransactionBindingFailure> failures
    ) {
        if (schemaVersion != TransactionBindingSchema.CURRENT_VERSION) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.UNSUPPORTED_SCHEMA,
                    field,
                    "Unsupported Transaction validation binding schema version: " + schemaVersion
            ));
        }
    }

    private static void requirePresent(
            Optional<?> value,
            String field,
            List<TransactionBindingFailure> failures
    ) {
        if (value.isEmpty()) {
            failures.add(new TransactionBindingFailure(
                    TransactionBindingFailureCode.MISSING_BINDING_COMPONENT,
                    field,
                    "Validation binding is missing required component: " + field
            ));
        }
    }
}
