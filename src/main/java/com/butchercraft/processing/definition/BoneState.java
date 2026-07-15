package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;

public enum BoneState {
    BONE_IN("bone_in"),
    BONELESS("boneless"),
    NOT_APPLICABLE("not_applicable");

    public static final Codec<BoneState> CODEC = Codec.STRING.comapFlatMap(BoneState::decode, BoneState::serializedName);

    private final String serializedName;

    BoneState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<BoneState> decode(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(state -> state.serializedName.equals(normalized))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown bone state: " + value));
    }
}
