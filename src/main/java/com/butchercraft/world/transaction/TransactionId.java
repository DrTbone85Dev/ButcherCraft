package com.butchercraft.world.transaction;

import java.util.Objects;
import java.util.regex.Pattern;

public record TransactionId(String value) implements Comparable<TransactionId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public TransactionId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Transaction id must be lowercase words with an optional namespace: " + value
            );
        }
    }

    public static TransactionId of(String value) {
        return new TransactionId(value);
    }

    @Override
    public int compareTo(TransactionId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
