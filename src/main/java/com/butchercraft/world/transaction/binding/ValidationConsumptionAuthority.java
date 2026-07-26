package com.butchercraft.world.transaction.binding;

import java.util.List;
import java.util.Objects;

final class ValidationConsumptionAuthority {
    private final TransactionValidationBinding binding;
    private final String authorityScopeIdentity;
    private boolean consumed;

    private ValidationConsumptionAuthority(TransactionValidationBinding binding, String authorityScopeIdentity) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.authorityScopeIdentity = TransactionBindingValidation.id(authorityScopeIdentity, "authorityScopeIdentity");
    }

    static ValidationConsumptionAuthority issue(
            TransactionValidationBinding binding,
            String authorityScopeIdentity
    ) {
        return new ValidationConsumptionAuthority(binding, authorityScopeIdentity);
    }

    TransactionValidationBinding binding() {
        return binding;
    }

    String authorityScopeIdentity() {
        return authorityScopeIdentity;
    }

    ValidationConsumptionResult consume(TransactionValidationBinding suppliedBinding) {
        Objects.requireNonNull(suppliedBinding, "suppliedBinding");
        if (consumed) {
            return ValidationConsumptionResult.failure(new TransactionBindingFailure(
                    TransactionBindingFailureCode.VALIDATION_CONSUMPTION_AUTHORITY_CONSUMED,
                    "validationConsumptionAuthority",
                    "Validation Consumption Authority has already been consumed"
            ));
        }
        if (!binding.equals(suppliedBinding)) {
            return ValidationConsumptionResult.failure(new TransactionBindingFailure(
                    TransactionBindingFailureCode.VALIDATION_CONSUMPTION_AUTHORITY_INVALID,
                    "validationConsumptionAuthority",
                    "Validation Consumption Authority does not authorize the supplied binding"
            ));
        }
        consumed = true;
        return new ValidationConsumptionResult(true, List.of());
    }
}
