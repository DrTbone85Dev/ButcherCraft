package com.butchercraft.world.ownership;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record PersonId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public PersonId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Person id must use lowercase snake case: " + value);
        }
    }
}
