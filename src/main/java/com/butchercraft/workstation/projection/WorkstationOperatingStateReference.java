package com.butchercraft.workstation.projection;

import java.util.Objects;

public record WorkstationOperatingStateReference(
        String workstationInstanceIdentity,
        long revision,
        String state,
        String contentDigest
) {
    public WorkstationOperatingStateReference {
        workstationInstanceIdentity = requireText(workstationInstanceIdentity, "workstationInstanceIdentity");
        if (revision < 0L) throw new IllegalArgumentException("Operating-state revision must not be negative");
        state = requireText(state, "state");
        contentDigest = requireText(contentDigest, "contentDigest");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
