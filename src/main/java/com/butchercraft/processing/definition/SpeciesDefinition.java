package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record SpeciesDefinition(
        String displayNameKey,
        ResourceLocation processingProfile,
        ResourceLocation productFamily,
        boolean productsEdible,
        boolean enabled,
        List<ResourceLocation> capabilityMarkers
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DefinitionCodecs.nonBlankString("display_name_key").fieldOf("display_name_key").forGetter(Raw::displayNameKey),
            ResourceLocation.CODEC.fieldOf("processing_profile").forGetter(Raw::processingProfile),
            ResourceLocation.CODEC.fieldOf("product_family").forGetter(Raw::productFamily),
            Codec.BOOL.fieldOf("products_edible").forGetter(Raw::productsEdible),
            Codec.BOOL.fieldOf("enabled").forGetter(Raw::enabled),
            DefinitionCodecs.markerList("capability_markers").optionalFieldOf("capability_markers", List.of()).forGetter(Raw::capabilityMarkers)
    ).apply(instance, Raw::new));

    public static final Codec<SpeciesDefinition> CODEC = RAW_CODEC.comapFlatMap(SpeciesDefinition::fromRaw, SpeciesDefinition::toRaw);

    public SpeciesDefinition {
        displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey").strip();
        if (displayNameKey.isEmpty()) {
            throw new IllegalArgumentException("Species display name key cannot be blank");
        }
        Objects.requireNonNull(processingProfile, "processingProfile");
        Objects.requireNonNull(productFamily, "productFamily");
        capabilityMarkers = List.copyOf(Objects.requireNonNull(capabilityMarkers, "capabilityMarkers"));
        if (capabilityMarkers.size() > DefinitionCodecs.MAX_MARKERS) {
            throw new IllegalArgumentException("Species capability markers are bounded to " + DefinitionCodecs.MAX_MARKERS);
        }
    }

    private static DataResult<SpeciesDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new SpeciesDefinition(
                    raw.displayNameKey,
                    raw.processingProfile,
                    raw.productFamily,
                    raw.productsEdible,
                    raw.enabled,
                    raw.capabilityMarkers
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(displayNameKey, processingProfile, productFamily, productsEdible, enabled, capabilityMarkers);
    }

    private record Raw(
            String displayNameKey,
            ResourceLocation processingProfile,
            ResourceLocation productFamily,
            boolean productsEdible,
            boolean enabled,
            List<ResourceLocation> capabilityMarkers
    ) {
    }
}
