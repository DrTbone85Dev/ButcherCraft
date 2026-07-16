package com.butchercraft.processing.definition;

import com.butchercraft.registration.ModDataPackRegistries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class BuiltInProcessingDefinitions {
    private BuiltInProcessingDefinitions() {
    }

    public static DefinitionRegistryView builtInView() {
        return new DefinitionRegistryView(
                Map.of(BuiltInDefinitionIds.BEEF, beefSpecies()),
                Map.of(BuiltInDefinitionIds.RED_MEAT, redMeatProfile()),
                Map.of(
                        BuiltInDefinitionIds.BEEF_TRIM, beefTrimProduct(),
                        BuiltInDefinitionIds.GROUND_BEEF, groundBeefProduct()
                ),
                Map.of(BuiltInDefinitionIds.GRIND_BEEF, grindBeefOperation())
        );
    }

    public static void bootstrapSpecies(BootstrapContext<SpeciesDefinition> context) {
        context.register(key(ModDataPackRegistries.SPECIES, BuiltInDefinitionIds.BEEF), beefSpecies());
    }

    public static void bootstrapProcessingProfiles(BootstrapContext<ProcessingProfileDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_PROFILE, BuiltInDefinitionIds.RED_MEAT), redMeatProfile());
    }

    public static void bootstrapProducts(BootstrapContext<ProductDefinition> context) {
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_TRIM), beefTrimProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.GROUND_BEEF), groundBeefProduct());
    }

    public static void bootstrapOperations(BootstrapContext<ProcessingOperationDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BEEF), grindBeefOperation());
    }

    public static SpeciesDefinition beefSpecies() {
        return new SpeciesDefinition(
                "definition.butchercraft.species.beef",
                BuiltInDefinitionIds.RED_MEAT,
                BuiltInDefinitionIds.BEEF,
                true,
                true,
                List.of()
        );
    }

    public static ProcessingProfileDefinition redMeatProfile() {
        return new ProcessingProfileDefinition(
                "definition.butchercraft.processing_profile.red_meat",
                BuiltInDefinitionIds.PROFILE_CATEGORY_RED_MEAT,
                List.of(BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING),
                List.of(
                        BuiltInDefinitionIds.WORKFLOW_STAGE_PRIMARY_PROCESSING,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_SIZE_REDUCTION
                ),
                List.of(),
                false
        );
    }

    public static ProductDefinition beefTrimProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.beef_trim",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("trim"),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(BuiltInDefinitionIds.id("trait/trim")),
                true,
                false
        );
    }

    public static ProductDefinition groundBeefProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.ground_beef",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("ground"),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(BuiltInDefinitionIds.id("trait/ground")),
                false,
                true
        );
    }

    public static ProcessingOperationDefinition grindBeefOperation() {
        return new ProcessingOperationDefinition(
                "definition.butchercraft.processing_operation.grind_beef",
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.GROUND_BEEF,
                BuiltInDefinitionIds.id("trim"),
                BuiltInDefinitionIds.id("ground"),
                3_000,
                new YieldDefinition(9, 10),
                -5,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                java.util.Optional.of(BuiltInDefinitionIds.WORKSTATION_CAPABILITY_GRINDING),
                false,
                false
        );
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, ResourceLocation id) {
        return ResourceKey.create(registry, id);
    }
}
