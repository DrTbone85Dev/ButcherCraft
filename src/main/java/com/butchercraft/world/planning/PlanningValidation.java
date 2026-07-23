package com.butchercraft.world.planning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Collections;
import java.util.regex.Pattern;

final class PlanningValidation {
    static final int SCHEMA_VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private PlanningValidation() {
    }

    static String id(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a canonical namespaced id: " + value);
        }
        return normalized;
    }

    static String text(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > 2_048) {
            throw new IllegalArgumentException(label + " is blank or too long");
        }
        return normalized;
    }

    static long tick(long value) {
        if (value < 0L) throw new IllegalArgumentException("Planning tick must not be negative");
        return value;
    }

    static int schema(int value) {
        if (value != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Planning schema version: " + value);
        }
        return value;
    }

    static Map<String, String> metadata(Map<String, String> source) {
        TreeMap<String, String> result = new TreeMap<>();
        Objects.requireNonNull(source, "metadata").forEach((key, value) ->
                result.put(id(key, "Planning metadata key"), text(value, "Planning metadata value")));
        if (result.size() > 64) throw new IllegalArgumentException("Planning metadata has too many entries");
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    static long encodedMapSize(Map<String, String> values) {
        long size = 0L;
        for (Map.Entry<String, String> entry : Objects.requireNonNull(values, "values").entrySet()) {
            size = Math.addExact(size, entry.getKey().getBytes(StandardCharsets.UTF_8).length);
            size = Math.addExact(size, entry.getValue().getBytes(StandardCharsets.UTF_8).length);
            size = Math.addExact(size, 2L);
        }
        return size;
    }

    static String derivedId(String type, String... components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(text(type, "Planning identity type").getBytes(StandardCharsets.UTF_8));
            for (String component : components) {
                digest.update((byte) 0);
                digest.update(Objects.requireNonNull(component, "identity component")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return "butchercraft:" + type + "/id_"
                    + HexFormat.of().formatHex(digest.digest(), 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
