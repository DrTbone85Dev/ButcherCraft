package com.butchercraft.world.goods;

import java.util.Objects;
import java.util.regex.Pattern;

public record GoodId(String value) implements Comparable<GoodId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public GoodId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Good id must be lowercase words with an optional namespace: " + value);
        }
    }

    public static GoodId of(String value) {
        return new GoodId(value);
    }

    @Override
    public int compareTo(GoodId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
