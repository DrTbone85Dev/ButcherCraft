package com.butchercraft.world.transaction.binding;

import java.util.Objects;

public final class ValidationConsumptionGrant {
    private final ValidationConsumptionAuthority authority;

    ValidationConsumptionGrant(ValidationConsumptionAuthority authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    ValidationConsumptionAuthority authority() {
        return authority;
    }
}
