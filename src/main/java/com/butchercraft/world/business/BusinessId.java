package com.butchercraft.world.business;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BusinessId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public BusinessId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Business id must use lowercase snake case: " + value);
        }
    }
}
