package com.butchercraft.processing.definition;

import com.butchercraft.processing.definition.BoneState;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.processing.definition.ProcessingOperationDefinition;
import com.butchercraft.processing.definition.ProcessingProfileDefinition;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.QuantityDefinition;
import com.butchercraft.processing.definition.SpeciesDefinition;
import com.butchercraft.processing.definition.YieldDefinition;
import com.butchercraft.processing.definition.ZeroOutputPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DefinitionTestFixtures {
    static final ResourceLocation POULTRY_PROFILE = id("poultry_profile");
    static final ResourceLocation POULTRY_SPECIES = id("poultry_species");
    static final ResourceLocation POULTRY_PRODUCT = id("poultry_product");
    static final ResourceLocation POULTRY_OUTPUT = id("poultry_output");
    static final ResourceLocation POULTRY_OPERATION = id("poultry_operation");
    static final ResourceLocation POULTRY_CATEGORY = id("operation_category/poultry_test");

    private DefinitionTestFixtures() {
    }

    static DefinitionRegistryView builtIns() {
        return BuiltInProcessingDefinitions.builtInView();
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("butchercraft_test", path);
    }

    static DefinitionRegistryView withSpecies(ResourceLocation id, SpeciesDefinition definition) {
        DefinitionRegistryView base = builtIns();
        Map<ResourceLocation, SpeciesDefinition> species = new LinkedHashMap<>(base.species());
        species.put(id, definition);
        return new DefinitionRegistryView(species, base.processingProfiles(), base.products(), base.operations());
    }

    static DefinitionRegistryView withProduct(ResourceLocation id, ProductDefinition definition) {
        DefinitionRegistryView base = builtIns();
        Map<ResourceLocation, ProductDefinition> products = new LinkedHashMap<>(base.products());
        products.put(id, definition);
        return new DefinitionRegistryView(base.species(), base.processingProfiles(), products, base.operations());
    }

    static DefinitionRegistryView withOperation(ResourceLocation id, ProcessingOperationDefinition definition) {
        DefinitionRegistryView base = builtIns();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        operations.put(id, definition);
        return new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);
    }

    static DefinitionRegistryView withProfilesAndPoultryProducts() {
        DefinitionRegistryView base = builtIns();
        Map<ResourceLocation, ProcessingProfileDefinition> profiles = new LinkedHashMap<>(base.processingProfiles());
        profiles.put(POULTRY_PROFILE, poultryProfile());

        Map<ResourceLocation, SpeciesDefinition> species = new LinkedHashMap<>(base.species());
        species.put(POULTRY_SPECIES, new SpeciesDefinition(
                "definition.butchercraft_test.species.poultry_species",
                POULTRY_PROFILE,
                POULTRY_SPECIES,
                true,
                true,
                List.of()
        ));

        Map<ResourceLocation, ProductDefinition> products = new LinkedHashMap<>(base.products());
        products.put(POULTRY_PRODUCT, poultryProduct(POULTRY_PRODUCT, "butchercraft:trim", true, false));
        products.put(POULTRY_OUTPUT, poultryProduct(POULTRY_OUTPUT, "butchercraft:ground", false, true));
        return new DefinitionRegistryView(species, profiles, products, base.operations());
    }

    static ProcessingProfileDefinition poultryProfile() {
        return new ProcessingProfileDefinition(
                "definition.butchercraft_test.processing_profile.poultry",
                id("profile_category/poultry"),
                List.of(POULTRY_CATEGORY),
                List.of(id("workflow_stage/poultry_specific")),
                List.of(),
                false
        );
    }

    static ProductDefinition poultryProduct(ResourceLocation id, String processingState, boolean graphInput, boolean graphOutput) {
        return new ProductDefinition(
                "definition.butchercraft_test.product." + id.getPath().replace('/', '.'),
                POULTRY_SPECIES,
                ResourceLocation.fromNamespaceAndPath("butchercraft", "poultry"),
                ResourceLocation.parse(processingState),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(),
                graphInput,
                graphOutput
        );
    }

    static ProcessingOperationDefinition operation(
            ResourceLocation inputProduct,
            ResourceLocation outputProduct,
            List<ResourceLocation> requiredProfiles
    ) {
        return new ProcessingOperationDefinition(
                "definition.butchercraft_test.operation",
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                requiredProfiles,
                inputProduct,
                outputProduct,
                ResourceLocation.fromNamespaceAndPath("butchercraft", "trim"),
                ResourceLocation.fromNamespaceAndPath("butchercraft", "ground"),
                3_000,
                new YieldDefinition(9, 10),
                -5,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                Optional.empty(),
                false,
                false
        );
    }

    static ProcessingOperationDefinition poultryOperation() {
        return new ProcessingOperationDefinition(
                "definition.butchercraft_test.operation.poultry",
                POULTRY_CATEGORY,
                List.of(POULTRY_PROFILE),
                POULTRY_PRODUCT,
                POULTRY_OUTPUT,
                ResourceLocation.fromNamespaceAndPath("butchercraft", "trim"),
                ResourceLocation.fromNamespaceAndPath("butchercraft", "ground"),
                2_000,
                new YieldDefinition(1, 1),
                0,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                Optional.empty(),
                false,
                false
        );
    }
}
