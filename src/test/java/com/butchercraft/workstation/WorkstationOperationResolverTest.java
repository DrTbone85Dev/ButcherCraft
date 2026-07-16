package com.butchercraft.workstation;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationOperationResolverTest {
    private final WorkstationOperationResolver resolver = new WorkstationOperationResolver();

    @Test
    void beefTrimFindsGrindBeef() {
        WorkstationOperationResolution result = resolve(beefTrimStack(), DevelopmentWorkstationFixtures.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(BuiltInDefinitionIds.GRIND_BEEF, result.operation().orElseThrow().operationId());
        assertEquals(60, result.operation().orElseThrow().totalTicks());
    }

    @Test
    void groundBeefFindsNoGrindOperation() {
        WorkstationOperationResolution result = resolve(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance(), DevelopmentWorkstationFixtures.capability());

        assertFailure(result, WorkstationFailureCode.NO_COMPATIBLE_OPERATION);
    }

    @Test
    void nonProductItemIsRejected() {
        assertFailure(resolve(new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()), DevelopmentWorkstationFixtures.capability()), WorkstationFailureCode.INPUT_NOT_PRODUCT);
    }

    @Test
    void missingProductComponentIsRejected() {
        assertFailure(resolve(new ItemStack(ModItems.BEEF_TRIM_TEST.get()), DevelopmentWorkstationFixtures.capability()), WorkstationFailureCode.MISSING_PRODUCT_DATA);
    }

    @Test
    void unknownProductDefinitionIsRejected() {
        ItemStack stack = beefTrimStack();
        ProductStackAdapter.writeProductData(stack, productData("butchercraft:unknown_product", ProductCategory.BEEF, ProcessingState.RAW, 1_000));

        assertFailure(resolve(stack, DevelopmentWorkstationFixtures.capability()), WorkstationFailureCode.UNKNOWN_PRODUCT_DEFINITION);
    }

    @Test
    void productDataMismatchIsRejected() {
        ItemStack stack = beefTrimStack();
        ProductStackAdapter.writeProductData(stack, productData(
                "butchercraft:beef_trim",
                ProductCategory.fromId(EngineId.of("butchercraft:poultry")),
                ProcessingState.RAW,
                1_000
        ));

        assertFailure(resolve(stack, DevelopmentWorkstationFixtures.capability()), WorkstationFailureCode.PRODUCT_DATA_MISMATCH);
    }

    @Test
    void capabilityMismatchIsRejected() {
        WorkstationCapability unsupported = new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath("butchercraft_test", "unsupported"),
                Set.of(),
                Set.of(),
                Set.of(BuiltInDefinitionIds.RED_MEAT),
                10_000,
                true,
                false,
                1,
                1
        );

        assertFailure(resolve(beefTrimStack(), unsupported), WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH);
    }

    @Test
    void processingProfileMismatchIsRejected() {
        WorkstationCapability unsupportedProfile = new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath("butchercraft_test", "profile_mismatch"),
                Set.of(BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING),
                Set.of(DevelopmentWorkstationFixtures.DEVELOPMENT_WORKSTATION_CAPABILITY),
                Set.of(ResourceLocation.fromNamespaceAndPath("butchercraft_test", "other_profile")),
                10_000,
                true,
                false,
                1,
                1
        );

        assertFailure(resolve(beefTrimStack(), unsupportedProfile), WorkstationFailureCode.OPERATION_PROFILE_MISMATCH);
    }

    @Test
    void quantityTooLowAndTooHighAreRejected() {
        ItemStack tooLow = beefTrimStack();
        ProductStackAdapter.writeProductData(tooLow, productData("butchercraft:beef_trim", ProductCategory.BEEF, ProcessingState.RAW, 50));
        assertFailure(resolve(tooLow, DevelopmentWorkstationFixtures.capability()), WorkstationFailureCode.INPUT_QUANTITY_TOO_LOW);

        WorkstationCapability lowCapacity = new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath("butchercraft_test", "low_capacity"),
                Set.of(BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING),
                Set.of(DevelopmentWorkstationFixtures.DEVELOPMENT_WORKSTATION_CAPABILITY),
                Set.of(BuiltInDefinitionIds.RED_MEAT),
                500,
                true,
                false,
                1,
                1
        );
        assertFailure(resolve(beefTrimStack(), lowCapacity), WorkstationFailureCode.INPUT_QUANTITY_TOO_HIGH);
    }

    @Test
    void multipleCompatibleOperationsRequireSelectionAndAreOrdered() {
        DefinitionRegistryView base = BuiltInProcessingDefinitions.builtInView();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        ResourceLocation alternate = ResourceLocation.fromNamespaceAndPath("butchercraft", "alternate_grind_beef");
        operations.put(alternate, BuiltInProcessingDefinitions.grindBeefOperation());
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        WorkstationOperationResolution result = resolver.resolve(view, DevelopmentWorkstationFixtures.capability(), beefTrimStack());

        assertFailure(result, WorkstationFailureCode.MULTIPLE_COMPATIBLE_OPERATIONS);
        assertEquals(alternate, result.compatibleOperationIds().get(0));
        assertEquals(BuiltInDefinitionIds.GRIND_BEEF, result.compatibleOperationIds().get(1));
    }

    private WorkstationOperationResolution resolve(ItemStack stack, WorkstationCapability capability) {
        return resolver.resolve(BuiltInProcessingDefinitions.builtInView(), capability, stack);
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
}
