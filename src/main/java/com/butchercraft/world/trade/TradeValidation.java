package com.butchercraft.world.trade;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class TradeValidation {
    private TradeValidation() {
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Trade network " + fieldName + " must not be blank");
        }
        return value;
    }

    static int requireScore(int value, String fieldName) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Trade network " + fieldName + " must be between 0 and 100: " + value);
        }
        return value;
    }

    static int requireYear(int value, String fieldName) {
        if (value < 1850 || value > 2026) {
            throw new IllegalArgumentException("Trade network " + fieldName + " is outside the supported range: " + value);
        }
        return value;
    }

    static <T> List<T> copyNonEmptyDistinct(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Trade network " + fieldName + " must not be empty");
        }
        Set<T> copied = new LinkedHashSet<>();
        for (T value : values) {
            copied.add(Objects.requireNonNull(value, fieldName + " value"));
        }
        if (copied.size() != values.size()) {
            throw new IllegalArgumentException("Trade network " + fieldName + " must not contain duplicates");
        }
        return List.copyOf(copied);
    }

    static <T> List<T> copyDistinct(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        Set<T> copied = new LinkedHashSet<>();
        for (T value : values) {
            copied.add(Objects.requireNonNull(value, fieldName + " value"));
        }
        if (copied.size() != values.size()) {
            throw new IllegalArgumentException("Trade network " + fieldName + " must not contain duplicates");
        }
        return List.copyOf(copied);
    }
}
