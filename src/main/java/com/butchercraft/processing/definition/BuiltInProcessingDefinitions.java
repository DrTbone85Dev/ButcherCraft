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
                Map.ofEntries(
                        Map.entry(BuiltInDefinitionIds.BEEF_TRIM, beefTrimProduct()),
                        Map.entry(BuiltInDefinitionIds.GROUND_BEEF, groundBeefProduct()),
                        Map.entry(BuiltInDefinitionIds.PORK_TRIM, porkTrimProduct()),
                        Map.entry(BuiltInDefinitionIds.GROUND_PORK, groundPorkProduct()),
                        Map.entry(BuiltInDefinitionIds.BISON_TRIM, bisonTrimProduct()),
                        Map.entry(BuiltInDefinitionIds.GROUND_BISON, groundBisonProduct()),
                        Map.entry(BuiltInDefinitionIds.BEEF_FOREQUARTER, beefForequarterProduct()),
                        Map.entry(BuiltInDefinitionIds.BEEF_CHUCK, beefPrimalProduct("definition.butchercraft.product.beef_chuck", BuiltInDefinitionIds.BEEF_CHUCK)),
                        Map.entry(BuiltInDefinitionIds.BEEF_RIB, beefPrimalProduct("definition.butchercraft.product.beef_rib", BuiltInDefinitionIds.BEEF_RIB)),
                        Map.entry(BuiltInDefinitionIds.BEEF_PACKER_BRISKET, beefPrimalProduct("definition.butchercraft.product.beef_packer_brisket", BuiltInDefinitionIds.BEEF_PACKER_BRISKET)),
                        Map.entry(BuiltInDefinitionIds.BEEF_PLATE, beefPrimalProduct("definition.butchercraft.product.beef_plate", BuiltInDefinitionIds.BEEF_PLATE)),
                        Map.entry(BuiltInDefinitionIds.BEEF_SHANK, beefPrimalProduct("definition.butchercraft.product.beef_shank", BuiltInDefinitionIds.BEEF_SHANK)),
                        Map.entry(BuiltInDefinitionIds.BEEF_FAT, beefFatProduct()),
                        Map.entry(BuiltInDefinitionIds.BEEF_BONE, beefBoneProduct())
                ),
                Map.of(
                        BuiltInDefinitionIds.GRIND_BEEF, grindBeefOperation(),
                        BuiltInDefinitionIds.GRIND_PORK, grindPorkOperation(),
                        BuiltInDefinitionIds.GRIND_BISON, grindBisonOperation(),
                        BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER, breakBeefForequarterOperation()
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
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_FOREQUARTER), beefForequarterProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_CHUCK), beefPrimalProduct(
                "definition.butchercraft.product.beef_chuck",
                BuiltInDefinitionIds.BEEF_CHUCK
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_RIB), beefPrimalProduct(
                "definition.butchercraft.product.beef_rib",
                BuiltInDefinitionIds.BEEF_RIB
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_PACKER_BRISKET), beefPrimalProduct(
                "definition.butchercraft.product.beef_packer_brisket",
                BuiltInDefinitionIds.BEEF_PACKER_BRISKET
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_PLATE), beefPrimalProduct(
                "definition.butchercraft.product.beef_plate",
                BuiltInDefinitionIds.BEEF_PLATE
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_SHANK), beefPrimalProduct(
                "definition.butchercraft.product.beef_shank",
                BuiltInDefinitionIds.BEEF_SHANK
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_FAT), beefFatProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_BONE), beefBoneProduct());
    }

    public static void bootstrapOperations(BootstrapContext<ProcessingOperationDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BEEF), grindBeefOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_PORK), grindPorkOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BISON), grindBisonOperation());
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER),
                breakBeefForequarterOperation()
        );
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
                List.of(
                        BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                        BuiltInDefinitionIds.OPERATION_CATEGORY_FABRICATION
                ),
                List.of(
                        BuiltInDefinitionIds.WORKFLOW_STAGE_PRIMARY_PROCESSING,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_SIZE_REDUCTION,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_FABRICATION
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

    public static ProductDefinition beefForequarterProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.beef_forequarter",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("forequarter"),
                "gram",
                true,
                BoneState.BONE_IN,
                true,
                List.of(BuiltInDefinitionIds.id("trait/forequarter")),
                true,
                false
        );
    }

    private static ProductDefinition beefPrimalProduct(String displayNameKey, ResourceLocation productId) {
        return new ProductDefinition(
                displayNameKey,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("primal"),
                "gram",
                true,
                BoneState.BONE_IN,
                true,
                List.of(BuiltInDefinitionIds.id("trait/primal"), productId),
                false,
                true
        );
    }

    private static ProductDefinition beefFatProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.beef_fat",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("fat"),
                "gram",
                true,
                BoneState.NOT_APPLICABLE,
                true,
                List.of(BuiltInDefinitionIds.id("trait/fat")),
                false,
                true
        );
    }

    private static ProductDefinition beefBoneProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.beef_bone",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("bone"),
                "gram",
                false,
                BoneState.BONE_IN,
                false,
                List.of(BuiltInDefinitionIds.id("trait/bone")),
                false,
                true
        );
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
                species.equals(BuiltInDefinitionIds.BEEF)
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

    public static ProcessingOperationDefinition breakBeefForequarterOperation() {
        return new ProcessingOperationDefinition(
                "definition.butchercraft.processing_operation.break_beef_forequarter",
                BuiltInDefinitionIds.OPERATION_CATEGORY_FABRICATION,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.BEEF_FOREQUARTER,
                BuiltInDefinitionIds.id("forequarter"),
                6_000,
                new QuantityDefinition(100_000, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(
                        output(BuiltInDefinitionIds.BEEF_CHUCK, BuiltInDefinitionIds.id("primal"), 30),
                        output(BuiltInDefinitionIds.BEEF_RIB, BuiltInDefinitionIds.id("primal"), 10),
                        output(BuiltInDefinitionIds.BEEF_PACKER_BRISKET, BuiltInDefinitionIds.id("primal"), 10),
                        output(BuiltInDefinitionIds.BEEF_PLATE, BuiltInDefinitionIds.id("primal"), 10),
                        output(BuiltInDefinitionIds.BEEF_SHANK, BuiltInDefinitionIds.id("primal"), 5),
                        output(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.id("trim"), 15),
                        output(BuiltInDefinitionIds.BEEF_FAT, BuiltInDefinitionIds.id("fat"), 5),
                        output(BuiltInDefinitionIds.BEEF_BONE, BuiltInDefinitionIds.id("bone"), 10)
                ),
                List.of(),
                java.util.Optional.of(BuiltInDefinitionIds.WORKSTATION_CAPABILITY_BANDSAW),
                false,
                false
        );
    }

    private static ProcessingOutputDefinition output(ResourceLocation product, ResourceLocation state, long percent) {
        return new ProcessingOutputDefinition(
                product,
                state,
                new YieldDefinition(percent, 100),
                -5,
                "gram",
                false
        );
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, ResourceLocation id) {
        return ResourceKey.create(registry, id);
    }
}
