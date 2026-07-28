package com.butchercraft.world.business.runtime;

import java.util.Objects;

public record BusinessShiftSetIdentity(String value) {
    public BusinessShiftSetIdentity {
        value = BusinessRuntimeValidation.requireExternalIdentity(value, "Business shift set identity");
    }

    static BusinessShiftSetIdentity fromCanonical(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        return new BusinessShiftSetIdentity("butchercraft:business_shift_set/v1/"
                + BusinessRuntimeDigest.sha256(canonical));
    }
}
