package com.butchercraft.engine;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable Minecraft-independent identifier used by engine value objects.
 *
 * <p>Identifiers are lowercase slash-separated tokens with an optional lowercase namespace. They
 * intentionally do not use Minecraft registry classes so domain logic can be unit tested without
 * loading Minecraft or NeoForge. Future integration code may map these values to registry names at
 * the boundary.</p>
 */
public record EngineId(String value) {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern VALID_VALUE = Pattern.compile(TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*");

    public EngineId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Engine id must be lowercase words with optional namespace: " + value);
        }
    }

    /**
     * Creates a validated engine id.
     *
     * @param value lowercase id value, optionally namespaced
     * @return immutable id
     * @throws IllegalArgumentException when the id is blank or not normalized
     */
    public static EngineId of(String value) {
        return new EngineId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
