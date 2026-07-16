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
        assertEquals(
                GrinderWorkstation.CAPABILITY_ID,
                BuiltInProcessingDefinitions.grindBeefOperation().workstationCapability().orElseThrow()
        );
    }

    @Test
    void beefTrimFindsGrindBeefForGrinder() {
        WorkstationOperationResolution result = resolve(beefTrimStack(), GrinderWorkstation.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(BuiltInDefinitionIds.GRIND_BEEF, result.operation().orElseThrow().operationId());
        assertEquals(60, result.operation().orElseThrow().totalTicks());
    }

    @Test
    void porkAndBisonTrimFindSpeciesSpecificGrindingOperationsForGrinder() {
        WorkstationOperationResolution pork = resolve(ModItems.PORK_TRIM_TEST.get().getDefaultInstance(), GrinderWorkstation.capability());
        WorkstationOperationResolution bison = resolve(ModItems.BISON_TRIM_TEST.get().getDefaultInstance(), GrinderWorkstation.capability());

        assertTrue(pork.succeeded(), pork.toString());
        assertEquals(BuiltInDefinitionIds.GRIND_PORK, pork.operation().orElseThrow().operationId());
        assertEquals(BuiltInDefinitionIds.GROUND_PORK, pork.operation().orElseThrow().definition().operation().outputProduct());
        assertEquals(60, pork.operation().orElseThrow().totalTicks());

        assertTrue(bison.succeeded(), bison.toString());
        assertEquals(BuiltInDefinitionIds.GRIND_BISON, bison.operation().orElseThrow().operationId());
        assertEquals(BuiltInDefinitionIds.GROUND_BISON, bison.operation().orElseThrow().definition().operation().outputProduct());
        assertEquals(60, bison.operation().orElseThrow().totalTicks());
    }

    @Test
    void groundBeefFindsNoGrindOperation() {
        assertFailure(resolve(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance(), GrinderWorkstation.capability()),
                WorkstationFailureCode.NO_COMPATIBLE_OPERATION);
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
