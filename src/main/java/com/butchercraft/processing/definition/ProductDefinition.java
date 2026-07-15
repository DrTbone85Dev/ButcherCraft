package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record ProductDefinition(
        String displayNameKey,
        ResourceLocation species,
        ResourceLocation productCategory,
        ResourceLocation processingState,
        String quantityUnit,
        boolean edible,
        BoneState boneState,
        boolean spoilageEligible,
        List<ResourceLocation> traits,
        boolean graphInput,
        boolean graphOutput
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DefinitionCodecs.nonBlankString("display_name_key").fieldOf("display_name_key").forGetter(Raw::displayNameKey),
            ResourceLocation.CODEC.fieldOf("species").forGetter(Raw::species),
            ResourceLocation.CODEC.fieldOf("product_category").forGetter(Raw::productCategory),
            ResourceLocation.CODEC.fieldOf("processing_state").forGetter(Raw::processingState),
            DefinitionCodecs.nonBlankString("quantity_unit").fieldOf("quantity_unit").forGetter(Raw::quantityUnit),
            Codec.BOOL.fieldOf("edible").forGetter(Raw::edible),
            BoneState.CODEC.fieldOf("bone_state").forGetter(Raw::boneState),
            Codec.BOOL.fieldOf("spoilage_eligible").forGetter(Raw::spoilageEligible),
            DefinitionCodecs.markerList("traits").optionalFieldOf("traits", List.of()).forGetter(Raw::traits),
            Codec.BOOL.fieldOf("graph_input").forGetter(Raw::graphInput),
            Codec.BOOL.fieldOf("graph_output").forGetter(Raw::graphOutput)
    ).apply(instance, Raw::new));

    public static final Codec<ProductDefinition> CODEC = RAW_CODEC.comapFlatMap(ProductDefinition::fromRaw, ProductDefinition::toRaw);

    public ProductDefinition {
        displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey").strip();
        if (displayNameKey.isEmpty()) {
            throw new IllegalArgumentException("Product display name key cannot be blank");
        }
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(productCategory, "productCategory");
        Objects.requireNonNull(processingState, "processingState");
        quantityUnit = Objects.requireNonNull(quantityUnit, "quantityUnit").strip();
        if (quantityUnit.isEmpty()) {
            throw new IllegalArgumentException("Product quantity unit cannot be blank");
        }
        Objects.requireNonNull(boneState, "boneState");
        traits = List.copyOf(Objects.requireNonNull(traits, "traits"));
        if (traits.size() > DefinitionCodecs.MAX_MARKERS) {
            throw new IllegalArgumentException("Product traits are bounded to " + DefinitionCodecs.MAX_MARKERS);
        }
    }

    private static DataResult<ProductDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProductDefinition(
                    raw.displayNameKey,
                    raw.species,
                    raw.productCategory,
                    raw.processingState,
                    raw.quantityUnit,
                    raw.edible,
                    raw.boneState,
                    raw.spoilageEligible,
                    raw.traits,
                    raw.graphInput,
                    raw.graphOutput
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(
                displayNameKey,
                species,
                productCategory,
                processingState,
                quantityUnit,
                edible,
                boneState,
                spoilageEligible,
                traits,
                graphInput,
                graphOutput
        );
    }

    private record Raw(
            String displayNameKey,
            ResourceLocation species,
            ResourceLocation productCategory,
            ResourceLocation processingState,
            String quantityUnit,
            boolean edible,
            BoneState boneState,
            boolean spoilageEligible,
            List<ResourceLocation> traits,
            boolean graphInput,
            boolean graphOutput
    ) {
    }
}
