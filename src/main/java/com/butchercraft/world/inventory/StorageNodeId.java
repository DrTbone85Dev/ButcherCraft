package com.butchercraft.world.inventory;

import java.util.Objects;
import java.util.regex.Pattern;

public record StorageNodeId(String value) implements Comparable<StorageNodeId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public StorageNodeId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Storage node id must be lowercase words with an optional namespace: "
                    + value);
        }
    }

    public static StorageNodeId of(String value) {
        return new StorageNodeId(value);
    }

    @Override
    public int compareTo(StorageNodeId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
