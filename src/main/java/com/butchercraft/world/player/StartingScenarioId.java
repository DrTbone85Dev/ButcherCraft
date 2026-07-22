package com.butchercraft.world.player;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record StartingScenarioId(String value) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public StartingScenarioId {
        value = Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Starting scenario id must use lowercase snake case: " + value);
        }
    }
}
