package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.TransactionId;

import java.util.List;
import java.util.Objects;

public record AuthoritativeTransactionResultEvidence(
        int schemaVersion,
        TransactionId transactionId,
        TransactionProposalIdentity proposalIdentity,
        InventoryFreshnessIdentity startingInventoryFreshnessIdentity,
        TransactionValidationPlanIdentity validationPlanIdentity,
        TransactionTerminalResult terminalResult,
        List<ResultingInventoryFreshnessEvidence> resultingInventoryFreshnessEvidence,
        List<ValidationInputIdentity> validationInputIdentities,
        String resultContentDigest
) {
    public AuthoritativeTransactionResultEvidence {
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        proposalIdentity = Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        startingInventoryFreshnessIdentity = Objects.requireNonNull(
                startingInventoryFreshnessIdentity,
                "startingInventoryFreshnessIdentity"
        );
        validationPlanIdentity = Objects.requireNonNull(validationPlanIdentity, "validationPlanIdentity");
        terminalResult = Objects.requireNonNull(terminalResult, "terminalResult");
        resultingInventoryFreshnessEvidence = Objects.requireNonNull(
                resultingInventoryFreshnessEvidence,
                "resultingInventoryFreshnessEvidence"
        ).stream()
                .map(evidence -> Objects.requireNonNull(evidence, "resultingInventoryFreshnessEvidence"))
                .sorted()
                .toList();
        validationInputIdentities = Objects.requireNonNull(
                validationInputIdentities,
                "validationInputIdentities"
        ).stream()
                .map(input -> Objects.requireNonNull(input, "validationInputIdentity"))
                .sorted()
                .toList();
        resultContentDigest = TransactionBindingValidation.digest(resultContentDigest, "resultContentDigest");
    }

    public static AuthoritativeTransactionResultEvidence forBinding(
            TransactionValidationBinding binding,
            TransactionTerminalResult terminalResult,
            List<ResultingInventoryFreshnessEvidence> resultingInventoryFreshnessEvidence
    ) {
        Objects.requireNonNull(binding, "binding");
        AuthoritativeTransactionResultEvidence candidate = new AuthoritativeTransactionResultEvidence(
                TransactionBindingSchema.CURRENT_VERSION,
                binding.transactionId(),
                binding.proposalIdentity(),
                binding.inventoryFreshnessIdentity(),
                binding.validationPlanIdentity(),
                terminalResult,
                resultingInventoryFreshnessEvidence,
                binding.validationInputIdentities(),
                TransactionBindingValidation.zeroDigest()
        );
        return candidate.withCalculatedDigest();
    }

    public AuthoritativeTransactionResultEvidence withCalculatedDigest() {
        return new AuthoritativeTransactionResultEvidence(
                schemaVersion,
                transactionId,
                proposalIdentity,
                startingInventoryFreshnessIdentity,
                validationPlanIdentity,
                terminalResult,
                resultingInventoryFreshnessEvidence,
                validationInputIdentities,
                calculateDigest()
        );
    }

    public String calculateDigest() {
        TransactionBindingCanonicalDigest digest = TransactionBindingCanonicalDigest.create(
                "butchercraft:transaction_result_evidence"
        );
        digest.add(schemaVersion)
                .add(transactionId.value())
                .add(proposalIdentity.schemaVersion())
                .add(proposalIdentity.transactionId().value())
                .add(proposalIdentity.proposalDigest())
                .add(startingInventoryFreshnessIdentity.schemaVersion())
                .add(startingInventoryFreshnessIdentity.issuerIdentity())
                .add(startingInventoryFreshnessIdentity.identityDigest())
                .add(validationPlanIdentity.schemaVersion())
                .add(validationPlanIdentity.planDigest())
                .add(terminalResult.name())
                .add(resultingInventoryFreshnessEvidence.size());
        for (ResultingInventoryFreshnessEvidence evidence : resultingInventoryFreshnessEvidence) {
            digest.add(evidence.resultIdentity())
                    .add(evidence.inventoryFreshnessIdentity().schemaVersion())
                    .add(evidence.inventoryFreshnessIdentity().issuerIdentity())
                    .add(evidence.inventoryFreshnessIdentity().identityDigest());
        }
        digest.add(validationInputIdentities.size());
        for (ValidationInputIdentity input : validationInputIdentities) {
            digest.add(input.identity())
                    .add(input.schemaVersion())
                    .add(input.contentDigest());
        }
        return digest.finish();
    }

    public boolean digestMatches() {
        return resultContentDigest.equals(calculateDigest());
    }
}
