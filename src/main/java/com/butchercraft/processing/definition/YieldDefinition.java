package com.butchercraft.processing.definition;

import com.butchercraft.engine.operation.YieldRatio;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record YieldDefinition(long numerator, long denominator) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DefinitionCodecs.nonNegativeLong("yield numerator").fieldOf("numerator").forGetter(Raw::numerator),
            DefinitionCodecs.positiveLong("yield denominator").fieldOf("denominator").forGetter(Raw::denominator)
    ).apply(instance, Raw::new));

    public static final Codec<YieldDefinition> CODEC = RAW_CODEC.comapFlatMap(YieldDefinition::fromRaw, YieldDefinition::toRaw);

    public YieldDefinition {
        if (numerator < 0) {
            throw new IllegalArgumentException("Yield numerator cannot be negative");
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("Yield denominator must be positive");
        }
    }

    public YieldRatio toEngineRatio() {
        return new YieldRatio(numerator, denominator);
    }

    private static DataResult<YieldDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new YieldDefinition(raw.numerator, raw.denominator));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(numerator, denominator);
    }

    private record Raw(long numerator, long denominator) {
    }
}
