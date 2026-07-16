package com.butchercraft.processing.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ProcessingOutputDefinition(
        ResourceLocation product,
        ResourceLocation state,
        YieldDefinition yield,
        int qualityAdjustment,
        String quantityUnit,
        boolean allowZero
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("product").forGetter(Raw::product),
            ResourceLocation.CODEC.fieldOf("state").forGetter(Raw::state),
            YieldDefinition.CODEC.fieldOf("yield").forGetter(Raw::yield),
            DefinitionCodecs.intRange("quality_adjustment", -1000, 1000).fieldOf("quality_adjustment").forGetter(Raw::qualityAdjustment),
            DefinitionCodecs.nonBlankString("quantity_unit").fieldOf("quantity_unit").forGetter(Raw::quantityUnit),
            Codec.BOOL.fieldOf("allow_zero").forGetter(Raw::allowZero)
    ).apply(instance, Raw::new));

    public static final Codec<ProcessingOutputDefinition> CODEC =
            RAW_CODEC.comapFlatMap(ProcessingOutputDefinition::fromRaw, ProcessingOutputDefinition::toRaw);

    public ProcessingOutputDefinition {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(yield, "yield");
        if (qualityAdjustment < -1000 || qualityAdjustment > 1000) {
            throw new IllegalArgumentException("Output quality adjustment must be between -1000 and 1000");
        }
        quantityUnit = Objects.requireNonNull(quantityUnit, "quantityUnit").strip();
        if (quantityUnit.isEmpty()) {
            throw new IllegalArgumentException("Output quantity unit cannot be blank");
        }
        QuantityUnit.fromId(quantityUnit);
    }

    public com.butchercraft.engine.operation.ProcessingOutputDefinition toEngineOutput() {
        return new com.butchercraft.engine.operation.ProcessingOutputDefinition(
                EngineId.of(product.toString()),
                ProcessingState.fromId(EngineId.of(state.toString())),
                yield.toEngineRatio(),
                qualityAdjustment,
                QuantityUnit.fromId(quantityUnit),
                allowZero
        );
    }

    private static DataResult<ProcessingOutputDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProcessingOutputDefinition(
                    raw.product,
                    raw.state,
                    raw.yield,
                    raw.qualityAdjustment,
                    raw.quantityUnit,
                    raw.allowZero
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(product, state, yield, qualityAdjustment, quantityUnit, allowZero);
    }

    private record Raw(
            ResourceLocation product,
            ResourceLocation state,
            YieldDefinition yield,
            int qualityAdjustment,
            String quantityUnit,
            boolean allowZero
    ) {
    }
}
