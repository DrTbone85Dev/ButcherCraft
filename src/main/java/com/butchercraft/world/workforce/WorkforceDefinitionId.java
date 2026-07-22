package com.butchercraft.world.workforce;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record WorkforceDefinitionId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public WorkforceDefinitionId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Workforce definition id must use lowercase snake case: " + value);
        }
    }
}
