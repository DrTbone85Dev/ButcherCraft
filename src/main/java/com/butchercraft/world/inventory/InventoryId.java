package com.butchercraft.world.inventory;

import java.util.Objects;
import java.util.regex.Pattern;

public record InventoryId(String value) implements Comparable<InventoryId> {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_ID = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    public InventoryId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Inventory id must be lowercase words with an optional namespace: " + value);
        }
    }

    public static InventoryId of(String value) {
        return new InventoryId(value);
    }

    @Override
    public int compareTo(InventoryId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
