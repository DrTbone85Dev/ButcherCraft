package com.butchercraft.product.component;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/**
 * Immutable Minecraft-facing product snapshot stored as an ItemStack data component.
 *
 * <p>The component stores only the fields needed to reconstruct the current engine product. Invalid
 * decoded data is rejected rather than sanitized so corrupted item data cannot become unrelated
 * valid product data.</p>
 */
public record ProductStackData(
        String productTypeId,
        String sourceCategoryId,
        String processingStateId,
        long quantityValue,
        String quantityUnitId,
        int qualityScore
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("product_type_id").forGetter(Raw::productTypeId),
            Codec.STRING.fieldOf("source_category_id").forGetter(Raw::sourceCategoryId),
            Codec.STRING.fieldOf("processing_state_id").forGetter(Raw::processingStateId),
            Codec.LONG.fieldOf("quantity_value").forGetter(Raw::quantityValue),
            Codec.STRING.fieldOf("quantity_unit_id").forGetter(Raw::quantityUnitId),
            Codec.INT.fieldOf("quality_score").forGetter(Raw::qualityScore)
    ).apply(instance, Raw::new));

    public static final Codec<ProductStackData> CODEC = RAW_CODEC.comapFlatMap(ProductStackData::fromRaw, ProductStackData::toRaw);

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductStackData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ProductStackData::productTypeId,
            ByteBufCodecs.STRING_UTF8,
            ProductStackData::sourceCategoryId,
            ByteBufCodecs.STRING_UTF8,
            ProductStackData::processingStateId,
            ByteBufCodecs.VAR_LONG,
            ProductStackData::quantityValue,
            ByteBufCodecs.STRING_UTF8,
            ProductStackData::quantityUnitId,
            ByteBufCodecs.VAR_INT,
            ProductStackData::qualityScore,
            ProductStackData::new
    );

    public ProductStackData {
        productTypeId = requireEngineId(productTypeId, "productTypeId").value();
        sourceCategoryId = requireEngineId(sourceCategoryId, "sourceCategoryId").value();
        processingStateId = requireEngineId(processingStateId, "processingStateId").value();
        quantityUnitId = Objects.requireNonNull(quantityUnitId, "quantityUnitId").strip();

        ProductCategory.fromId(EngineId.of(sourceCategoryId));
        ProcessingState.fromId(EngineId.of(processingStateId));
        QuantityUnit.fromId(quantityUnitId);

        if (quantityValue < 0) {
            throw new IllegalArgumentException("Product quantity cannot be negative: " + quantityValue);
        }
        if (qualityScore < ProductQuality.MIN_SCORE || qualityScore > ProductQuality.MAX_SCORE) {
            throw new IllegalArgumentException("Quality score must be between 0 and 1000: " + qualityScore);
        }
    }

    public static ProductStackData fromEngineValues(
            EngineId productTypeId,
            ProductCategory sourceCategory,
            ProcessingState processingState,
            long quantityValue,
            QuantityUnit quantityUnit,
            int qualityScore
    ) {
        return new ProductStackData(
                Objects.requireNonNull(productTypeId, "productTypeId").value(),
                Objects.requireNonNull(sourceCategory, "sourceCategory").id().value(),
                Objects.requireNonNull(processingState, "processingState").id().value(),
                quantityValue,
                Objects.requireNonNull(quantityUnit, "quantityUnit").id(),
                qualityScore
        );
    }

    private static EngineId requireEngineId(String value, String fieldName) {
        return EngineId.of(Objects.requireNonNull(value, fieldName));
    }

    private static DataResult<ProductStackData> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProductStackData(
                    raw.productTypeId,
                    raw.sourceCategoryId,
                    raw.processingStateId,
                    raw.quantityValue,
                    raw.quantityUnitId,
                    raw.qualityScore
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(productTypeId, sourceCategoryId, processingStateId, quantityValue, quantityUnitId, qualityScore);
    }

    private record Raw(
            String productTypeId,
            String sourceCategoryId,
            String processingStateId,
            long quantityValue,
            String quantityUnitId,
            int qualityScore
    ) {
    }
}
