package com.butchercraft.world.trade;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record DistributionTerritoryId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public DistributionTerritoryId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Distribution territory id must use lowercase snake case: " + value);
        }
    }
}
