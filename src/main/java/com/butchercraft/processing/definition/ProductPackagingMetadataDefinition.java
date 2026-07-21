package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Optional product-definition metadata linking a retail product to packaging content.
 */
public record ProductPackagingMetadataDefinition(
        ResourceLocation definition,
        ResourceLocation sourceProduct
) {
    public static final Codec<ProductPackagingMetadataDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(ProductPackagingMetadataDefinition::definition),
            ResourceLocation.CODEC.fieldOf("source_product").forGetter(ProductPackagingMetadataDefinition::sourceProduct)
    ).apply(instance, ProductPackagingMetadataDefinition::new));

    public ProductPackagingMetadataDefinition {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(sourceProduct, "sourceProduct");
    }
}
