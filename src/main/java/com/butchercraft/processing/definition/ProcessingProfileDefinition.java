package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record ProcessingProfileDefinition(
        String displayNameKey,
        ResourceLocation profileCategory,
        List<ResourceLocation> allowedOperationCategories,
        List<ResourceLocation> requiredWorkflowStages,
        List<ResourceLocation> compatibilityMarkers,
        boolean crossProfileOperationsPermittedByDefault
) {
    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DefinitionCodecs.nonBlankString("display_name_key").fieldOf("display_name_key").forGetter(Raw::displayNameKey),
            ResourceLocation.CODEC.fieldOf("profile_category").forGetter(Raw::profileCategory),
            DefinitionCodecs.markerList("allowed_operation_categories").fieldOf("allowed_operation_categories").forGetter(Raw::allowedOperationCategories),
            DefinitionCodecs.markerList("required_workflow_stages").fieldOf("required_workflow_stages").forGetter(Raw::requiredWorkflowStages),
            DefinitionCodecs.markerList("compatibility_markers").optionalFieldOf("compatibility_markers", List.of()).forGetter(Raw::compatibilityMarkers),
            Codec.BOOL.fieldOf("cross_profile_operations_permitted_by_default").forGetter(Raw::crossProfileOperationsPermittedByDefault)
    ).apply(instance, Raw::new));

    public static final Codec<ProcessingProfileDefinition> CODEC =
            RAW_CODEC.comapFlatMap(ProcessingProfileDefinition::fromRaw, ProcessingProfileDefinition::toRaw);

    public ProcessingProfileDefinition {
        displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey").strip();
        if (displayNameKey.isEmpty()) {
            throw new IllegalArgumentException("Profile display name key cannot be blank");
        }
        Objects.requireNonNull(profileCategory, "profileCategory");
        allowedOperationCategories = requireNonEmptyBounded(allowedOperationCategories, "allowedOperationCategories");
        requiredWorkflowStages = requireNonEmptyBounded(requiredWorkflowStages, "requiredWorkflowStages");
        compatibilityMarkers = List.copyOf(Objects.requireNonNull(compatibilityMarkers, "compatibilityMarkers"));
        if (compatibilityMarkers.size() > DefinitionCodecs.MAX_MARKERS) {
            throw new IllegalArgumentException("Profile compatibility markers are bounded to " + DefinitionCodecs.MAX_MARKERS);
        }
    }

    private static List<ResourceLocation> requireNonEmptyBounded(List<ResourceLocation> values, String fieldName) {
        List<ResourceLocation> copied = List.copyOf(Objects.requireNonNull(values, fieldName));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        if (copied.size() > DefinitionCodecs.MAX_MARKERS) {
            throw new IllegalArgumentException(fieldName + " is bounded to " + DefinitionCodecs.MAX_MARKERS);
        }
        return copied;
    }

    public boolean allowsOperationCategory(ResourceLocation operationCategory) {
        return allowedOperationCategories.contains(Objects.requireNonNull(operationCategory, "operationCategory"));
    }

    private static DataResult<ProcessingProfileDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProcessingProfileDefinition(
                    raw.displayNameKey,
                    raw.profileCategory,
                    raw.allowedOperationCategories,
                    raw.requiredWorkflowStages,
                    raw.compatibilityMarkers,
                    raw.crossProfileOperationsPermittedByDefault
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(
                displayNameKey,
                profileCategory,
                allowedOperationCategories,
                requiredWorkflowStages,
                compatibilityMarkers,
                crossProfileOperationsPermittedByDefault
        );
    }

    private record Raw(
            String displayNameKey,
            ResourceLocation profileCategory,
            List<ResourceLocation> allowedOperationCategories,
            List<ResourceLocation> requiredWorkflowStages,
            List<ResourceLocation> compatibilityMarkers,
            boolean crossProfileOperationsPermittedByDefault
    ) {
    }
}
