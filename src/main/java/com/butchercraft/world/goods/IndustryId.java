package com.butchercraft.world.goods;

import java.util.Objects;
import java.util.regex.Pattern;

public record IndustryId(String value) implements Comparable<IndustryId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public IndustryId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Industry id must be lowercase words with an optional namespace: " + value);
        }
    }

    public static IndustryId of(String value) {
        return new IndustryId(value);
    }

    @Override
    public int compareTo(IndustryId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
