package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;

public enum ZeroOutputPolicy {
    FORBID("forbid"),
    ALLOW("allow");

    public static final Codec<ZeroOutputPolicy> CODEC = Codec.STRING.comapFlatMap(
            ZeroOutputPolicy::decode,
            ZeroOutputPolicy::serializedName
    );

    private final String serializedName;

    ZeroOutputPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean permitsZeroOutput() {
        return this == ALLOW;
    }

    private static DataResult<ZeroOutputPolicy> decode(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(normalized))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown zero-output policy: " + value));
    }
}
