package com.butchercraft.machine.grinder;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.processing.definition.ProcessingOperationDefinition;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderOperationResolverTest {
    private final WorkstationOperationResolver resolver = new WorkstationOperationResolver();

    @Test
    void grinderCapabilityIdIsStable() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("butchercraft", "grinding"), GrinderWorkstation.CAPABILITY_ID);
        assertTrue(GrinderWorkstation.capability().supportsWorkstationCapability(GrinderWorkstation.CAPABILITY_ID));
    }

    @Test
    void builtInGrindBeefRequiresGrinderCapability() {
        assertGrindingCapability(BuiltInProcessingDefinitions.grindBeefOperation());
        assertGrindingCapability(BuiltInProcessingDefinitions.grindPorkOperation());
        assertGrindingCapability(BuiltInProcessingDefinitions.grindChickenOperation());
        assertGrindingCapability(BuiltInProcessingDefinitions.grindBisonOperation());
        assertGrindingCapability(BuiltInProcessingDefinitions.grindLambOperation());
        assertGrindingCapability(BuiltInProcessingDefinitions.grindVenisonOperation());
    }

    @Test
    void beefTrimFindsGrindBeefForGrinder() {
        WorkstationOperationResolution result = resolve(beefTrimStack(), GrinderWorkstation.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(BuiltInDefinitionIds.GRIND_BEEF, result.operation().orElseThrow().operationId());
        assertEquals(60, result.operation().orElseThrow().totalTicks());
    }

    @Test
    void allPromotedTrimProductsFindSpeciesSpecificGrindingOperationsForGrinder() {
        for (RecipeCase recipe : promotedRecipes()) {
            assertResolvesTo(recipe.input().get().getDefaultInstance(), recipe.operationId(), recipe.outputProductId());
        }
    }

    @Test
    void groundProductsFindNoGrindOperation() {
        for (var output : List.of(
                ModItems.GROUND_BEEF,
                ModItems.GROUND_PORK,
                ModItems.GROUND_CHICKEN,
                ModItems.GROUND_BUFFALO,
                ModItems.GROUND_LAMB,
                ModItems.GROUND_VENISON
        )) {
            assertFailure(resolve(output.get().getDefaultInstance(), GrinderWorkstation.capability()),
                    WorkstationFailureCode.NO_COMPATIBLE_OPERATION);
        }
    }

    @Test
    void vanillaItemIsRejected() {
        assertFailure(resolve(new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()), GrinderWorkstation.capability()),
                WorkstationFailureCode.INPUT_NOT_PRODUCT);
    }

    @Test
    void capabilityMismatchIsRejected() {
        WorkstationCapability unsupported = new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath("butchercraft_test", "unsupported"),
                Set.of(),
                Set.of(ResourceLocation.fromNamespaceAndPath("butchercraft_test", "slicing")),
                Set.of(),
                10_000,
                true,
                false,
                1,
                1
        );

        assertFailure(resolve(beefTrimStack(), unsupported), WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH);
    }

    @Test
    void profileMismatchIsRejectedWhenCapabilityNarrowsProfiles() {
        WorkstationCapability unsupportedProfile = new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath("butchercraft_test", "profile_mismatch"),
                Set.of(),
                Set.of(GrinderWorkstation.CAPABILITY_ID),
                Set.of(ResourceLocation.fromNamespaceAndPath("butchercraft_test", "poultry_profile")),
                10_000,
                true,
                false,
                1,
                1
        );

        assertFailure(resolve(beefTrimStack(), unsupportedProfile), WorkstationFailureCode.OPERATION_PROFILE_MISMATCH);
    }

    @Test
    void quantityBelowMinimumIsRejected() {
        ItemStack tooLow = beefTrimStack();
        ProductStackAdapter.writeProductData(tooLow, productData("butchercraft:beef_trim", ProductCategory.BEEF, ProcessingState.RAW, 50));

        assertFailure(resolve(tooLow, GrinderWorkstation.capability()), WorkstationFailureCode.INPUT_QUANTITY_TOO_LOW);
    }

    @Test
    void multipleCompatibleGrindingOperationsRequireSelectionAndAreOrdered() {
        DefinitionRegistryView base = BuiltInProcessingDefinitions.builtInView();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        ResourceLocation alternate = ResourceLocation.fromNamespaceAndPath("butchercraft", "alternate_grind_beef");
        operations.put(alternate, BuiltInProcessingDefinitions.grindBeefOperation());
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        WorkstationOperationResolution result = resolver.resolve(view, GrinderWorkstation.capability(), beefTrimStack());

        assertFailure(result, WorkstationFailureCode.MULTIPLE_COMPATIBLE_OPERATIONS);
        assertEquals(alternate, result.compatibleOperationIds().get(0));
        assertEquals(BuiltInDefinitionIds.GRIND_BEEF, result.compatibleOperationIds().get(1));
    }

    private WorkstationOperationResolution resolve(ItemStack stack, WorkstationCapability capability) {
        return resolver.resolve(BuiltInProcessingDefinitions.builtInView(), capability, stack);
    }

    private void assertResolvesTo(
            ItemStack input,
            ResourceLocation operationId,
            ResourceLocation outputProductId
    ) {
        WorkstationOperationResolution result = resolve(input, GrinderWorkstation.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(operationId, result.operation().orElseThrow().operationId());
        assertEquals(outputProductId, result.operation().orElseThrow().definition().operation().outputProduct());
        assertEquals(60, result.operation().orElseThrow().totalTicks());
    }

    private static void assertGrindingCapability(ProcessingOperationDefinition operation) {
        assertEquals(
                GrinderWorkstation.CAPABILITY_ID,
                operation.workstationCapability().orElseThrow()
        );
    }

    private static List<RecipeCase> promotedRecipes() {
        return List.of(
                new RecipeCase(ModItems.BEEF_TRIM, BuiltInDefinitionIds.GRIND_BEEF, BuiltInDefinitionIds.GROUND_BEEF),
                new RecipeCase(ModItems.PORK_TRIM, BuiltInDefinitionIds.GRIND_PORK, BuiltInDefinitionIds.GROUND_PORK),
                new RecipeCase(ModItems.CHICKEN_TRIM, BuiltInDefinitionIds.GRIND_CHICKEN, BuiltInDefinitionIds.GROUND_CHICKEN),
                new RecipeCase(ModItems.BUFFALO_TRIM, BuiltInDefinitionIds.GRIND_BISON, BuiltInDefinitionIds.GROUND_BISON),
                new RecipeCase(ModItems.LAMB_TRIM, BuiltInDefinitionIds.GRIND_LAMB, BuiltInDefinitionIds.GROUND_LAMB),
                new RecipeCase(ModItems.VENISON_TRIM, BuiltInDefinitionIds.GRIND_VENISON, BuiltInDefinitionIds.GROUND_VENISON)
        );
    }

    private static ItemStack beefTrimStack() {
        return ModItems.BEEF_TRIM_TEST.get().getDefaultInstance();
    }

    private static ProductStackData productData(String productId, ProductCategory category, ProcessingState state, long quantity) {
        return ProductStackData.fromEngineValues(
                EngineId.of(productId),
                category,
                state,
                quantity,
                QuantityUnit.GRAM,
                700
        );
    }

    private static void assertFailure(WorkstationOperationResolution result, WorkstationFailureCode code) {
        assertTrue(result.failure().isPresent(), result.toString());
        assertEquals(code, result.failure().orElseThrow().code());
    }

    private record RecipeCase(
            net.neoforged.neoforge.registries.DeferredItem<? extends net.minecraft.world.item.Item> input,
            ResourceLocation operationId,
            ResourceLocation outputProductId
    ) {
    }
}
