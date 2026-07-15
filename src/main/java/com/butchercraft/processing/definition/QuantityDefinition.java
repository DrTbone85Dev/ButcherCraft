package com.butchercraft.processing.definition;

import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record QuantityDefinition(long amount, String unit) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DefinitionCodecs.nonNegativeLong("quantity amount").fieldOf("amount").forGetter(Raw::amount),
            DefinitionCodecs.nonBlankString("quantity unit").fieldOf("unit").forGetter(Raw::unit)
    ).apply(instance, Raw::new));

    public static final Codec<QuantityDefinition> CODEC = RAW_CODEC.comapFlatMap(QuantityDefinition::fromRaw, QuantityDefinition::toRaw);

    public QuantityDefinition {
        if (amount < 0) {
            throw new IllegalArgumentException("Quantity amount cannot be negative");
        }
        unit = Objects.requireNonNull(unit, "unit").strip();
        if (unit.isEmpty()) {
            throw new IllegalArgumentException("Quantity unit cannot be blank");
        }
        QuantityUnit.fromId(unit);
    }

    public ProductQuantity toEngineQuantity() {
        return new ProductQuantity(amount, QuantityUnit.fromId(unit));
    }

    private static DataResult<QuantityDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new QuantityDefinition(raw.amount, raw.unit));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(amount, unit);
    }

    private record Raw(long amount, String unit) {
    }
}
