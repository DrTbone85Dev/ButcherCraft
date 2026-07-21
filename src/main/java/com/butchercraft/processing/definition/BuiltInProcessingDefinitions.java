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
                        Map.entry(BuiltInDefinitionIds.RETAIL_GROUND_BEEF, retailGroundBeefProduct()),
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
                        Map.entry(BuiltInDefinitionIds.BEEF_BONE, beefBoneProduct()),
                        Map.entry(BuiltInDefinitionIds.BEEF_HINDQUARTER, beefHindquarterProduct()),
                        Map.entry(BuiltInDefinitionIds.BEEF_ROUND, beefPrimalProduct("definition.butchercraft.product.beef_round", BuiltInDefinitionIds.BEEF_ROUND, true)),
                        Map.entry(BuiltInDefinitionIds.BEEF_SIRLOIN, beefPrimalProduct("definition.butchercraft.product.beef_sirloin", BuiltInDefinitionIds.BEEF_SIRLOIN, true)),
                        Map.entry(BuiltInDefinitionIds.BEEF_SHORT_LOIN, beefPrimalProduct("definition.butchercraft.product.beef_short_loin", BuiltInDefinitionIds.BEEF_SHORT_LOIN, true)),
                        Map.entry(BuiltInDefinitionIds.BEEF_FLANK, beefPrimalProduct("definition.butchercraft.product.beef_flank", BuiltInDefinitionIds.BEEF_FLANK)),
                        Map.entry(BuiltInDefinitionIds.T_BONE_STEAK, beefFabricatedProduct("definition.butchercraft.product.t_bone_steak", BuiltInDefinitionIds.T_BONE_STEAK, "steak", "trait/steak")),
                        Map.entry(BuiltInDefinitionIds.PORTERHOUSE_STEAK, beefFabricatedProduct("definition.butchercraft.product.porterhouse_steak", BuiltInDefinitionIds.PORTERHOUSE_STEAK, "steak", "trait/steak")),
                        Map.entry(BuiltInDefinitionIds.BEEF_STRIP_LOIN, beefFabricatedProduct("definition.butchercraft.product.beef_strip_loin", BuiltInDefinitionIds.BEEF_STRIP_LOIN, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.BEEF_TENDERLOIN, beefFabricatedProduct("definition.butchercraft.product.beef_tenderloin", BuiltInDefinitionIds.BEEF_TENDERLOIN, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.TOP_ROUND, beefFabricatedProduct("definition.butchercraft.product.top_round", BuiltInDefinitionIds.TOP_ROUND, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.BOTTOM_ROUND, beefFabricatedProduct("definition.butchercraft.product.bottom_round", BuiltInDefinitionIds.BOTTOM_ROUND, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.EYE_OF_ROUND, beefFabricatedProduct("definition.butchercraft.product.eye_of_round", BuiltInDefinitionIds.EYE_OF_ROUND, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.SIRLOIN_TIP, beefFabricatedProduct("definition.butchercraft.product.sirloin_tip", BuiltInDefinitionIds.SIRLOIN_TIP, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.TOP_SIRLOIN, beefFabricatedProduct("definition.butchercraft.product.top_sirloin", BuiltInDefinitionIds.TOP_SIRLOIN, "subprimal", "trait/subprimal")),
                        Map.entry(BuiltInDefinitionIds.SIRLOIN_STEAK, beefFabricatedProduct("definition.butchercraft.product.sirloin_steak", BuiltInDefinitionIds.SIRLOIN_STEAK, "steak", "trait/steak")),
                        Map.entry(BuiltInDefinitionIds.TRI_TIP, beefFabricatedProduct("definition.butchercraft.product.tri_tip", BuiltInDefinitionIds.TRI_TIP, "subprimal", "trait/subprimal"))
                ),
                Map.ofEntries(
                        Map.entry(BuiltInDefinitionIds.GRIND_BEEF, grindBeefOperation()),
                        Map.entry(BuiltInDefinitionIds.GRIND_PORK, grindPorkOperation()),
                        Map.entry(BuiltInDefinitionIds.GRIND_BISON, grindBisonOperation()),
                        Map.entry(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER, breakBeefForequarterOperation()),
                        Map.entry(BuiltInDefinitionIds.BREAK_BEEF_HINDQUARTER, breakBeefHindquarterOperation()),
                        Map.entry(BuiltInDefinitionIds.CUT_BEEF_SHORT_LOIN, cutBeefShortLoinOperation()),
                        Map.entry(BuiltInDefinitionIds.CUT_BEEF_ROUND, cutBeefRoundOperation()),
                        Map.entry(BuiltInDefinitionIds.CUT_BEEF_SIRLOIN, cutBeefSirloinOperation()),
                        Map.entry(BuiltInDefinitionIds.PACKAGE_RETAIL, packageRetailOperation())
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
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.RETAIL_GROUND_BEEF), retailGroundBeefProduct());
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
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_HINDQUARTER), beefHindquarterProduct());
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_ROUND), beefPrimalProduct(
                "definition.butchercraft.product.beef_round",
                BuiltInDefinitionIds.BEEF_ROUND,
                true
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_SIRLOIN), beefPrimalProduct(
                "definition.butchercraft.product.beef_sirloin",
                BuiltInDefinitionIds.BEEF_SIRLOIN,
                true
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_SHORT_LOIN), beefPrimalProduct(
                "definition.butchercraft.product.beef_short_loin",
                BuiltInDefinitionIds.BEEF_SHORT_LOIN,
                true
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_FLANK), beefPrimalProduct(
                "definition.butchercraft.product.beef_flank",
                BuiltInDefinitionIds.BEEF_FLANK
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.T_BONE_STEAK), beefFabricatedProduct(
                "definition.butchercraft.product.t_bone_steak",
                BuiltInDefinitionIds.T_BONE_STEAK,
                "steak",
                "trait/steak"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.PORTERHOUSE_STEAK), beefFabricatedProduct(
                "definition.butchercraft.product.porterhouse_steak",
                BuiltInDefinitionIds.PORTERHOUSE_STEAK,
                "steak",
                "trait/steak"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_STRIP_LOIN), beefFabricatedProduct(
                "definition.butchercraft.product.beef_strip_loin",
                BuiltInDefinitionIds.BEEF_STRIP_LOIN,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BEEF_TENDERLOIN), beefFabricatedProduct(
                "definition.butchercraft.product.beef_tenderloin",
                BuiltInDefinitionIds.BEEF_TENDERLOIN,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.TOP_ROUND), beefFabricatedProduct(
                "definition.butchercraft.product.top_round",
                BuiltInDefinitionIds.TOP_ROUND,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.BOTTOM_ROUND), beefFabricatedProduct(
                "definition.butchercraft.product.bottom_round",
                BuiltInDefinitionIds.BOTTOM_ROUND,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.EYE_OF_ROUND), beefFabricatedProduct(
                "definition.butchercraft.product.eye_of_round",
                BuiltInDefinitionIds.EYE_OF_ROUND,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.SIRLOIN_TIP), beefFabricatedProduct(
                "definition.butchercraft.product.sirloin_tip",
                BuiltInDefinitionIds.SIRLOIN_TIP,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.TOP_SIRLOIN), beefFabricatedProduct(
                "definition.butchercraft.product.top_sirloin",
                BuiltInDefinitionIds.TOP_SIRLOIN,
                "subprimal",
                "trait/subprimal"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.SIRLOIN_STEAK), beefFabricatedProduct(
                "definition.butchercraft.product.sirloin_steak",
                BuiltInDefinitionIds.SIRLOIN_STEAK,
                "steak",
                "trait/steak"
        ));
        context.register(key(ModDataPackRegistries.PRODUCT, BuiltInDefinitionIds.TRI_TIP), beefFabricatedProduct(
                "definition.butchercraft.product.tri_tip",
                BuiltInDefinitionIds.TRI_TIP,
                "subprimal",
                "trait/subprimal"
        ));
    }

    public static void bootstrapOperations(BootstrapContext<ProcessingOperationDefinition> context) {
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BEEF), grindBeefOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_PORK), grindPorkOperation());
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.GRIND_BISON), grindBisonOperation());
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER),
                breakBeefForequarterOperation()
        );
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.BREAK_BEEF_HINDQUARTER),
                breakBeefHindquarterOperation()
        );
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.CUT_BEEF_SHORT_LOIN),
                cutBeefShortLoinOperation()
        );
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.CUT_BEEF_ROUND),
                cutBeefRoundOperation()
        );
        context.register(
                key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.CUT_BEEF_SIRLOIN),
                cutBeefSirloinOperation()
        );
        context.register(key(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInDefinitionIds.PACKAGE_RETAIL), packageRetailOperation());
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
                        BuiltInDefinitionIds.OPERATION_CATEGORY_FABRICATION,
                        BuiltInDefinitionIds.OPERATION_CATEGORY_PACKAGING
                ),
                List.of(
                        BuiltInDefinitionIds.WORKFLOW_STAGE_PRIMARY_PROCESSING,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_SIZE_REDUCTION,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_FABRICATION,
                        BuiltInDefinitionIds.WORKFLOW_STAGE_RETAIL_PACKAGING
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

    public static ProductDefinition retailGroundBeefProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.retail_ground_beef",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("retail_packaged"),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(BuiltInDefinitionIds.id("trait/retail_packaged")),
                java.util.Optional.of(new ProductPackagingMetadataDefinition(
                        BuiltInDefinitionIds.RETAIL_PACKAGE,
                        BuiltInDefinitionIds.GROUND_BEEF
                )),
                false,
                true
        );
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

    public static ProductDefinition beefHindquarterProduct() {
        return new ProductDefinition(
                "definition.butchercraft.product.beef_hindquarter",
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("hindquarter"),
                "gram",
                true,
                BoneState.BONE_IN,
                true,
                List.of(BuiltInDefinitionIds.id("trait/hindquarter")),
                true,
                false
        );
    }

    private static ProductDefinition beefPrimalProduct(String displayNameKey, ResourceLocation productId) {
        return beefPrimalProduct(displayNameKey, productId, false);
    }

    private static ProductDefinition beefPrimalProduct(String displayNameKey, ResourceLocation productId, boolean graphInput) {
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
                graphInput,
                true
        );
    }

    private static ProductDefinition beefFabricatedProduct(
            String displayNameKey,
            ResourceLocation productId,
            String state,
            String trait
    ) {
        return new ProductDefinition(
                displayNameKey,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id(state),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(BuiltInDefinitionIds.id(trait), productId),
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

    public static ProcessingOperationDefinition breakBeefHindquarterOperation() {
        return fabricationOperation(
                "definition.butchercraft.processing_operation.break_beef_hindquarter",
                BuiltInDefinitionIds.BEEF_HINDQUARTER,
                BuiltInDefinitionIds.id("hindquarter"),
                100_000,
                List.of(
                        outputQuantity(BuiltInDefinitionIds.BEEF_ROUND, BuiltInDefinitionIds.id("primal"), 30_000, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_SIRLOIN, BuiltInDefinitionIds.id("primal"), 15_000, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_SHORT_LOIN, BuiltInDefinitionIds.id("primal"), 15_000, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_FLANK, BuiltInDefinitionIds.id("primal"), 7_500, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.id("trim"), 15_000, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_FAT, BuiltInDefinitionIds.id("fat"), 7_500, 100_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_BONE, BuiltInDefinitionIds.id("bone"), 10_000, 100_000)
                )
        );
    }

    public static ProcessingOperationDefinition cutBeefShortLoinOperation() {
        return fabricationOperation(
                "definition.butchercraft.processing_operation.cut_beef_short_loin",
                BuiltInDefinitionIds.BEEF_SHORT_LOIN,
                BuiltInDefinitionIds.id("primal"),
                15_000,
                List.of(
                        outputQuantity(BuiltInDefinitionIds.T_BONE_STEAK, BuiltInDefinitionIds.id("steak"), 4_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.PORTERHOUSE_STEAK, BuiltInDefinitionIds.id("steak"), 3_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_STRIP_LOIN, BuiltInDefinitionIds.id("subprimal"), 3_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_TENDERLOIN, BuiltInDefinitionIds.id("subprimal"), 2_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.id("trim"), 1_500, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_BONE, BuiltInDefinitionIds.id("bone"), 1_500, 15_000)
                )
        );
    }

    public static ProcessingOperationDefinition cutBeefRoundOperation() {
        return fabricationOperation(
                "definition.butchercraft.processing_operation.cut_beef_round",
                BuiltInDefinitionIds.BEEF_ROUND,
                BuiltInDefinitionIds.id("primal"),
                30_000,
                List.of(
                        outputQuantity(BuiltInDefinitionIds.TOP_ROUND, BuiltInDefinitionIds.id("subprimal"), 7_500, 30_000),
                        outputQuantity(BuiltInDefinitionIds.BOTTOM_ROUND, BuiltInDefinitionIds.id("subprimal"), 6_500, 30_000),
                        outputQuantity(BuiltInDefinitionIds.EYE_OF_ROUND, BuiltInDefinitionIds.id("subprimal"), 3_500, 30_000),
                        outputQuantity(BuiltInDefinitionIds.SIRLOIN_TIP, BuiltInDefinitionIds.id("subprimal"), 5_000, 30_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.id("trim"), 4_000, 30_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_FAT, BuiltInDefinitionIds.id("fat"), 1_500, 30_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_BONE, BuiltInDefinitionIds.id("bone"), 2_000, 30_000)
                )
        );
    }

    public static ProcessingOperationDefinition cutBeefSirloinOperation() {
        return fabricationOperation(
                "definition.butchercraft.processing_operation.cut_beef_sirloin",
                BuiltInDefinitionIds.BEEF_SIRLOIN,
                BuiltInDefinitionIds.id("primal"),
                15_000,
                List.of(
                        outputQuantity(BuiltInDefinitionIds.TOP_SIRLOIN, BuiltInDefinitionIds.id("subprimal"), 5_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.SIRLOIN_STEAK, BuiltInDefinitionIds.id("steak"), 3_500, 15_000),
                        outputQuantity(BuiltInDefinitionIds.TRI_TIP, BuiltInDefinitionIds.id("subprimal"), 2_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.id("trim"), 2_500, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_FAT, BuiltInDefinitionIds.id("fat"), 1_000, 15_000),
                        outputQuantity(BuiltInDefinitionIds.BEEF_BONE, BuiltInDefinitionIds.id("bone"), 1_000, 15_000)
                )
        );
    }

    public static ProcessingOperationDefinition packageRetailOperation() {
        return new ProcessingOperationDefinition(
                "definition.butchercraft.processing_operation.package_retail",
                BuiltInDefinitionIds.OPERATION_CATEGORY_PACKAGING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.GROUND_BEEF,
                BuiltInDefinitionIds.id("ground"),
                3_000,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(new ProcessingOutputDefinition(
                        BuiltInDefinitionIds.RETAIL_GROUND_BEEF,
                        BuiltInDefinitionIds.id("retail_packaged"),
                        new YieldDefinition(1, 1),
                        0,
                        "gram",
                        false
                )),
                List.of(),
                java.util.Optional.of(BuiltInDefinitionIds.WORKSTATION_CAPABILITY_PACKAGING),
                false,
                false
        );
    }

    private static ProcessingOperationDefinition fabricationOperation(
            String displayNameKey,
            ResourceLocation inputProduct,
            ResourceLocation inputState,
            long inputGrams,
            List<ProcessingOutputDefinition> outputs
    ) {
        return new ProcessingOperationDefinition(
                displayNameKey,
                BuiltInDefinitionIds.OPERATION_CATEGORY_FABRICATION,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                inputProduct,
                inputState,
                6_000,
                new QuantityDefinition(inputGrams, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                outputs,
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

    private static ProcessingOutputDefinition outputQuantity(
            ResourceLocation product,
            ResourceLocation state,
            long outputGrams,
            long inputGrams
    ) {
        return new ProcessingOutputDefinition(
                product,
                state,
                new YieldDefinition(outputGrams, inputGrams),
                -5,
                "gram",
                false
        );
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, ResourceLocation id) {
        return ResourceKey.create(registry, id);
    }
}
