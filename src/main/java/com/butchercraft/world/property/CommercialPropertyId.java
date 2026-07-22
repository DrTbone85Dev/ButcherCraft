package com.butchercraft.world.property;

import java.util.Objects;
import java.util.regex.Pattern;

public record CommercialPropertyId(String value) {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    public CommercialPropertyId {
        value = Objects.requireNonNull(value, "value");
        if (!ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Commercial property id must be lowercase snake case: " + value);
        }
    }
}
