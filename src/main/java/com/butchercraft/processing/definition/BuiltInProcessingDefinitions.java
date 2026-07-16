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
                Map.of(
                        BuiltInDefinitionIds.BEEF, beefSpecies(),
                        BuiltInDefinitionIds.PORK, porkSpecies(),
                        BuiltInDefinitionIds.BISON, bisonSpecies()
                ),
                Map.of(BuiltInDefinitionIds.RED_MEAT, redMeatProfile()),
                Map.of(
                        BuiltInDefinitionIds.BEEF_TRIM, beefTrimProduct(),
                        BuiltInDefinitionIds.GROUND_BEEF, groundBeefProduct(),
                        BuiltInDefinitionIds.PORK_TRIM, porkTrimProduct(),
                        BuiltInDefinitionIds.GROUND_PORK, groundPorkProduct(),
                        BuiltInDefinitionIds.BISON_TRIM, bisonTrimProduct(),
                        BuiltInDefinitionIds.GROUND_BISON, groundBisonProduct()
                ),
                Map.of(
                        BuiltInDefinitionIds.GRIND_BEEF, grindBeefOperation(),
                        BuiltInDefinitionIds.GRIND_PORK, grindPorkOperation(),
                        BuiltInDefinitionIds.GRIND_BISON, grindBisonOperation()
                )
        );
    }

    public static void bootstrapSpecies(BootstrapContext<SpeciesDefinition> context) {
        context.register(key(ModDataPackRegistries.SPECIES, BuiltInDefinitionIds.BEEF), beefSpecies());
        context.register(key(ModDataPackRegistries.SPECIES, BuiltInDefinitionIds.PORK), porkSpecies());
        context.register(key(ModDataPackRegistries.SPECIES, BuiltInDefinitionIds.BISON), bisonSpecies());
    }

    public static void bootstrapProcessingProfiles(BootstrapContext<ProcessingProfileDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_PROFILE, BuiltInDefinitionIds.RED_MEAT), redMeatProfile());
    }

    public static void bootstrapProducts(BootstrapContext<ProductDefinition> context) {
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_TRIM), beefTrimProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.GROUND_BEEF), groundBeefProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.PORK_TRIM), porkTrimProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.GROUND_PORK), groundPorkProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BISON_TRIM), bisonTrimProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.GROUND_BISON), groundBisonProduct());
    }

    public static void bootstrapOperations(BootstrapContext<ProcessingOperationDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BEEF), grindBeefOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_PORK), grindPorkOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BISON), grindBisonOperation());
    }

    public static SpeciesDefinition beefSpecies() {
        return redMeatSpecies("definition.butchercraft.species.beef", BuiltInDefinitionIds.BEEF);
    }

    public static SpeciesDefinition porkSpecies() {
        return redMeatSpecies("definition.butchercraft.species.pork", BuiltInDefinitionIds.PORK);
    }

    public static SpeciesDefinition bisonSpecies() {
        return redMeatSpecies("definition.butchercraft.species.bison", BuiltInDefinitionIds.BISON);
    }

    private static SpeciesDefinition redMeatSpecies(String displayNameKey, ResourceLocation productFamily) {
        return new SpeciesDefinition(
                displayNameKey,
                BuiltInDefinitionIds.RED_MEAT,
                productFamily,
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
        return trimProduct("definition.butchercraft.product.beef_trim", BuiltInDefinitionIds.BEEF);
    }

    public static ProductDefinition groundBeefProduct() {
        return groundProduct("definition.butchercraft.product.ground_beef", BuiltInDefinitionIds.BEEF);
    }

    public static ProductDefinition porkTrimProduct() {
        return trimProduct("definition.butchercraft.product.pork_trim", BuiltInDefinitionIds.PORK);
    }

    public static ProductDefinition groundPorkProduct() {
        return groundProduct("definition.butchercraft.product.ground_pork", BuiltInDefinitionIds.PORK);
    }

    public static ProductDefinition bisonTrimProduct() {
        return trimProduct("definition.butchercraft.product.bison_trim", BuiltInDefinitionIds.BISON);
    }

    public static ProductDefinition groundBisonProduct() {
        return groundProduct("definition.butchercraft.product.ground_bison", BuiltInDefinitionIds.BISON);
    }

    private static ProductDefinition trimProduct(String displayNameKey, ResourceLocation species) {
        return new ProductDefinition(
                displayNameKey,
                species,
                species,
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

    private static ProductDefinition groundProduct(String displayNameKey, ResourceLocation species) {
        return new ProductDefinition(
                displayNameKey,
                species,
                species,
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
        return grindOperation(
                "definition.butchercraft.processing_operation.grind_beef",
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.GROUND_BEEF
        );
    }

    public static ProcessingOperationDefinition grindPorkOperation() {
        return grindOperation(
                "definition.butchercraft.processing_operation.grind_pork",
                BuiltInDefinitionIds.PORK_TRIM,
                BuiltInDefinitionIds.GROUND_PORK
        );
    }

    public static ProcessingOperationDefinition grindBisonOperation() {
        return grindOperation(
                "definition.butchercraft.processing_operation.grind_bison",
                BuiltInDefinitionIds.BISON_TRIM,
                BuiltInDefinitionIds.GROUND_BISON
        );
    }

    private static ProcessingOperationDefinition grindOperation(
            String displayNameKey,
            ResourceLocation inputProduct,
            ResourceLocation outputProduct
    ) {
        return new ProcessingOperationDefinition(
                displayNameKey,
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                inputProduct,
                outputProduct,
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
