package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

final class DefinitionCodecs {
    static final int MAX_MARKERS = 16;

    private DefinitionCodecs() {
    }

    static Codec<String> nonBlankString(String fieldName) {
        return Codec.STRING.comapFlatMap(value -> {
            String checked = value.strip();
            if (checked.isEmpty()) {
                return DataResult.error(() -> fieldName + " cannot be blank");
            }
            return DataResult.success(checked);
        }, Function.identity());
    }

    static Codec<Integer> intRange(String fieldName, int min, int max) {
        return Codec.INT.comapFlatMap(value -> {
            if (value < min || value > max) {
                return DataResult.error(() -> fieldName + " must be between " + min + " and " + max + ": " + value);
            }
            return DataResult.success(value);
        }, Function.identity());
    }

    static Codec<Long> positiveLong(String fieldName) {
        return Codec.LONG.comapFlatMap(value -> {
            if (value <= 0) {
                return DataResult.error(() -> fieldName + " must be positive: " + value);
            }
            return DataResult.success(value);
        }, Function.identity());
    }

    static Codec<Long> nonNegativeLong(String fieldName) {
        return Codec.LONG.comapFlatMap(value -> {
            if (value < 0) {
                return DataResult.error(() -> fieldName + " cannot be negative: " + value);
            }
            return DataResult.success(value);
        }, Function.identity());
    }

    static Codec<List<ResourceLocation>> markerList(String fieldName) {
        return ResourceLocation.CODEC.listOf().comapFlatMap(values -> {
            if (values.size() > MAX_MARKERS) {
                return DataResult.error(() -> fieldName + " cannot contain more than " + MAX_MARKERS + " entries");
            }
            return DataResult.success(List.copyOf(values));
        }, Function.identity());
    }

    static String normalizedEnumToken(String value, String fieldName) {
        String checked = value.strip().toLowerCase(Locale.ROOT);
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return checked;
    }
}
