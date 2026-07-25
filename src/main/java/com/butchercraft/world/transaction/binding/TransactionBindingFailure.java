package com.butchercraft.world.transaction.binding;

import java.util.Objects;

public record TransactionBindingFailure(
        TransactionBindingFailureCode code,
        String field,
        String message
) {
    public TransactionBindingFailure {
        code = Objects.requireNonNull(code, "code");
        field = TransactionBindingValidation.text(field, "field");
        message = TransactionBindingValidation.text(message, "message");
    }
}
