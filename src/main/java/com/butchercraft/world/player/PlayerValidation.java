package com.butchercraft.world.player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class PlayerValidation {
    private PlayerValidation() {
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Player legacy " + fieldName + " must not be blank");
        }
        return value;
    }

    static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException("Player legacy " + fieldName + " must not be negative: " + value);
        }
        return value;
    }

    static <T> List<T> copyNonEmptyDistinct(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Player legacy " + fieldName + " must not be empty");
        }
        Set<T> copied = new LinkedHashSet<>();
        for (T value : values) {
            copied.add(Objects.requireNonNull(value, fieldName + " value"));
        }
        if (copied.size() != values.size()) {
            throw new IllegalArgumentException("Player legacy " + fieldName + " must not contain duplicates");
        }
        return List.copyOf(copied);
    }
}
