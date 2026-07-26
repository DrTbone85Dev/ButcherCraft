package com.butchercraft.world.transaction.binding;

import java.util.Objects;

public record TransactionValidationPlanIdentity(
        int schemaVersion,
        String planDigest
) {
    public TransactionValidationPlanIdentity {
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        planDigest = TransactionBindingValidation.digest(planDigest, "planDigest");
    }

    public static TransactionValidationPlanIdentity of(String planDigest) {
        return new TransactionValidationPlanIdentity(
                TransactionBindingSchema.CURRENT_VERSION,
                planDigest
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TransactionValidationPlanIdentity other)) {
            return false;
        }
        return schemaVersion == other.schemaVersion && planDigest.equals(other.planDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, planDigest);
    }
}
