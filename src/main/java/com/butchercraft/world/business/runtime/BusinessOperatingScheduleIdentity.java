package com.butchercraft.world.business.runtime;

import java.util.Objects;

public record BusinessOperatingScheduleIdentity(String value) {
    public BusinessOperatingScheduleIdentity {
        value = BusinessRuntimeValidation.requireExternalIdentity(value, "Business operating schedule identity");
    }

    static BusinessOperatingScheduleIdentity fromCanonical(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        return new BusinessOperatingScheduleIdentity("butchercraft:business_schedule/v1/"
                + BusinessRuntimeDigest.sha256(canonical));
    }
}
