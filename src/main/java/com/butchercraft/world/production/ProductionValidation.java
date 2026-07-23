package com.butchercraft.world.production;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class ProductionValidation {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern IDENTIFIER = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );
    private static final Pattern TAG = Pattern.compile("[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*)?");

    private ProductionValidation() {
    }

    static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase namespaced identifier: " + value);
        }
        return normalized;
    }

    static String requireText(String value, String label, int maximumLength) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1 to " + maximumLength + " characters");
        }
        return normalized;
    }

    static Set<String> copyTags(Set<String> source) {
        Objects.requireNonNull(source, "tags");
        if (source.size() > ProductionSchema.MAXIMUM_TAGS) {
            throw new IllegalArgumentException("Production metadata has too many tags");
        }
        java.util.LinkedHashSet<String> ordered = source.stream()
                .map(tag -> {
                    String normalized = Objects.requireNonNull(tag, "tag").strip().toLowerCase(Locale.ROOT);
                    if (!TAG.matcher(normalized).matches()) {
                        throw new IllegalArgumentException("Invalid production tag: " + tag);
                    }
                    return normalized;
                })
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return java.util.Collections.unmodifiableSet(ordered);
    }

    static int requireSchema(int version, String label) {
        if (version != ProductionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + version);
        }
        return version;
    }
}
