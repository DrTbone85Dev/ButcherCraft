package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.InventoryChange;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TransactionValidationPlan(
        int schemaVersion,
        List<ValidationPlanStep> steps,
        List<ValidationPlanPrecondition> preconditions,
        TransactionValidationPlanIdentity identity
) {
    public TransactionValidationPlan {
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        steps = Objects.requireNonNull(steps, "steps").stream()
                .map(step -> Objects.requireNonNull(step, "step"))
                .sorted()
                .toList();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Validation plan requires at least one staged mutation");
        }
        Set<Integer> operationOrders = new HashSet<>();
        for (ValidationPlanStep step : steps) {
            if (!operationOrders.add(step.operationOrder())) {
                throw new IllegalArgumentException("Validation plan contains duplicate operation order: "
                        + step.operationOrder());
            }
        }
        preconditions = Objects.requireNonNull(preconditions, "preconditions").stream()
                .map(precondition -> Objects.requireNonNull(precondition, "precondition"))
                .sorted()
                .toList();
        identity = Objects.requireNonNull(identity, "identity");
    }

    public static TransactionValidationPlan fromInventoryChanges(
            List<InventoryChange> changes,
            List<ValidationPlanPrecondition> preconditions
    ) {
        Objects.requireNonNull(changes, "changes");
        List<ValidationPlanStep> steps = java.util.stream.IntStream.range(0, changes.size())
                .mapToObj(index -> ValidationPlanStep.from(index, changes.get(index)))
                .toList();
        TransactionValidationPlan candidate = new TransactionValidationPlan(
                TransactionBindingSchema.CURRENT_VERSION,
                steps,
                preconditions,
                TransactionValidationPlanIdentity.of(TransactionBindingValidation.zeroDigest())
        );
        return candidate.withCalculatedIdentity();
    }

    public TransactionValidationPlan withCalculatedIdentity() {
        return new TransactionValidationPlan(
                schemaVersion,
                steps,
                preconditions,
                TransactionValidationPlanIdentity.of(calculateDigest())
        );
    }

    public String calculateDigest() {
        TransactionBindingCanonicalDigest digest = TransactionBindingCanonicalDigest.create(
                "butchercraft:transaction_validation_plan_identity"
        );
        digest.add(schemaVersion)
                .add(steps.size());
        for (ValidationPlanStep step : steps) {
            digest.add(step.operationOrder())
                    .add(step.inventoryId().value())
                    .add(step.changeType().name())
                    .add(step.goodId().value())
                    .add(step.quantity())
                    .add(step.unitOfMeasure().serializedName());
            TransactionProposalIdentity.addInventoryMetadata(digest, step.metadata());
        }
        digest.add(preconditions.size());
        for (ValidationPlanPrecondition precondition : preconditions) {
            digest.add(precondition.identity())
                    .add(precondition.schemaVersion())
                    .add(precondition.contentDigest());
        }
        return digest.finish();
    }

    public boolean identityMatches() {
        return identity.planDigest().equals(calculateDigest());
    }
}
