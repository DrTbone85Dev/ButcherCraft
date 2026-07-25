package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.transaction.TransactionExecutionAuthority;

import java.util.Objects;

public final class ValidationConsumptionBoundary {
    private ValidationConsumptionBoundary() {
    }

    public static ValidationConsumptionGrant issue(
            TransactionExecutionAuthority authority,
            TransactionValidationBinding binding,
            String authorityScopeIdentity
    ) {
        Objects.requireNonNull(authority, "authority");
        return new ValidationConsumptionGrant(ValidationConsumptionAuthority.issue(binding, authorityScopeIdentity));
    }

    public static TransactionBindingValidationResult consume(
            TransactionExecutionAuthority authority,
            ValidationConsumptionGrant grant,
            TransactionValidationBinding suppliedBinding
    ) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(grant, "grant");
        ValidationConsumptionResult result = grant.authority().consume(suppliedBinding);
        return result.successful()
                ? TransactionBindingValidationResult.successfulResult()
                : new TransactionBindingValidationResult(result.failures());
    }
}
