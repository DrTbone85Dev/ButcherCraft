package com.butchercraft.world.trade;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record DistributionRouteId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public DistributionRouteId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Distribution route id must use lowercase snake case: " + value);
        }
    }
}
