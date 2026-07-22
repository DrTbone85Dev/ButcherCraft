package com.butchercraft.world.economy.actor;

import java.util.Objects;
import java.util.regex.Pattern;

public record ActorId(String value) implements Comparable<ActorId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public ActorId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Actor id must be lowercase words with an optional namespace: " + value);
        }
    }

    public static ActorId of(String value) {
        return new ActorId(value);
    }

    @Override
    public int compareTo(ActorId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
