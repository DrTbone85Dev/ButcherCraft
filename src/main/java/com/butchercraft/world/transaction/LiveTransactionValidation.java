package com.butchercraft.world.transaction;

import com.butchercraft.world.transaction.binding.ValidationConsumptionBoundary;
import com.butchercraft.world.transaction.binding.ValidationConsumptionGrant;

import java.util.Objects;

record LiveTransactionValidation(
        TransactionValidation validation,
        ValidationConsumptionGrant consumptionGrant
) {
    LiveTransactionValidation {
        validation = Objects.requireNonNull(validation, "validation");
        consumptionGrant = Objects.requireNonNull(consumptionGrant, "consumptionGrant");
        if (!validation.accepted() || validation.binding().isEmpty()) {
            throw new IllegalArgumentException("Live Transaction validation requires an accepted binding");
        }
    }

    static LiveTransactionValidation issue(TransactionValidation validation) {
        var binding = Objects.requireNonNull(validation, "validation").binding().orElseThrow(() ->
                new IllegalArgumentException("Accepted Transaction validation is missing its binding"));
        return new LiveTransactionValidation(
                validation,
                ValidationConsumptionBoundary.issue(
                        TransactionExecutionAuthority.instance(),
                        binding,
                        TransactionBindingFactory.authorityScopeIdentity(validation.transactionId())
                )
        );
    }
}
