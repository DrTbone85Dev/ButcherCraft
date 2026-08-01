package com.butchercraft.world.business.runtime;

import java.util.Objects;

public record BusinessShiftIdentity(String value) {
    public BusinessShiftIdentity {
        value = BusinessRuntimeValidation.requireExternalIdentity(value, "Business shift identity");
    }

    static BusinessShiftIdentity fromCanonical(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        return new BusinessShiftIdentity("butchercraft:business_shift/v1/"
                + BusinessRuntimeDigest.sha256(canonical));
    }
}
