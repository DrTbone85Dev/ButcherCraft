package com.butchercraft.product.component;

import com.butchercraft.engine.EngineId;
import com.butchercraft.packaging.definition.PackagingFormat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/**
 * Immutable stack-level packaging snapshot for packaged product items.
 */
public record ProductStackPackagingData(
        String packagingDefinitionId,
        String packagingFormatId,
        String sourceProductId
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("packaging_definition_id").forGetter(Raw::packagingDefinitionId),
            Codec.STRING.fieldOf("packaging_format_id").forGetter(Raw::packagingFormatId),
            Codec.STRING.fieldOf("source_product_id").forGetter(Raw::sourceProductId)
    ).apply(instance, Raw::new));

    public static final Codec<ProductStackPackagingData> CODEC =
            RAW_CODEC.comapFlatMap(ProductStackPackagingData::fromRaw, ProductStackPackagingData::toRaw);

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductStackPackagingData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProductStackPackagingData decode(RegistryFriendlyByteBuf buffer) {
                    return new ProductStackPackagingData(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUtf()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ProductStackPackagingData value) {
                    buffer.writeUtf(value.packagingDefinitionId());
                    buffer.writeUtf(value.packagingFormatId());
                    buffer.writeUtf(value.sourceProductId());
                }
            };

    public ProductStackPackagingData {
        packagingDefinitionId = EngineId.of(Objects.requireNonNull(
                packagingDefinitionId,
                "packagingDefinitionId"
        )).value();
        packagingFormatId = Objects.requireNonNull(packagingFormatId, "packagingFormatId").strip();
        PackagingFormat.fromId(packagingFormatId);
        sourceProductId = EngineId.of(Objects.requireNonNull(sourceProductId, "sourceProductId")).value();
    }

    private static DataResult<ProductStackPackagingData> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProductStackPackagingData(
                    raw.packagingDefinitionId,
                    raw.packagingFormatId,
                    raw.sourceProductId
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(packagingDefinitionId, packagingFormatId, sourceProductId);
    }

    private record Raw(
            String packagingDefinitionId,
            String packagingFormatId,
            String sourceProductId
    ) {
    }
}
