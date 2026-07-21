package com.butchercraft.machine.bandsaw;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsawOperationResolverTest {
    private final WorkstationOperationResolver resolver = new WorkstationOperationResolver();

    @Test
    void bandsawCapabilityIdIsStable() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("butchercraft", "bandsaw"), BandsawWorkstation.CAPABILITY_ID);
        assertTrue(BandsawWorkstation.capability().supportsWorkstationCapability(BandsawWorkstation.CAPABILITY_ID));
    }

    @Test
    void builtInForequarterBreakdownRequiresBandsawCapabilityAndDefinesOrderedOutputs() {
        var operation = BuiltInProcessingDefinitions.breakBeefForequarterOperation();

        assertEquals(BandsawWorkstation.CAPABILITY_ID, operation.workstationCapability().orElseThrow());
        assertEquals(8, operation.outputs().size());
        assertEquals(BuiltInDefinitionIds.BEEF_CHUCK, operation.outputs().get(0).product());
        assertEquals(BuiltInDefinitionIds.BEEF_BONE, operation.outputs().get(7).product());
    }

    @Test
    void builtInBeefFabricationOperationsRequireBandsawCapability() {
        assertBandsawOperation(BuiltInProcessingDefinitions.breakBeefHindquarterOperation(), 7);
        assertBandsawOperation(BuiltInProcessingDefinitions.cutBeefShortLoinOperation(), 6);
        assertBandsawOperation(BuiltInProcessingDefinitions.cutBeefRoundOperation(), 7);
        assertBandsawOperation(BuiltInProcessingDefinitions.cutBeefSirloinOperation(), 6);
    }

    @Test
    void beefForequarterFindsBreakdownForBandsaw() {
        WorkstationOperationResolution result = resolve(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance(), BandsawWorkstation.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER, result.operation().orElseThrow().operationId());
        assertEquals(120, result.operation().orElseThrow().totalTicks());
        assertEquals(8, result.operation().orElseThrow().engineOperation().outputs().size());
    }

    @Test
    void beefHindquarterAndPrimalsFindFabricationOperationsForBandsaw() {
        assertResolvedOperation(ModItems.BEEF_HINDQUARTER_TEST.get().getDefaultInstance(),
                BuiltInDefinitionIds.BREAK_BEEF_HINDQUARTER, 7);
        assertResolvedOperation(ModItems.BEEF_SHORT_LOIN_TEST.get().getDefaultInstance(),
                BuiltInDefinitionIds.CUT_BEEF_SHORT_LOIN, 6);
        assertResolvedOperation(ModItems.BEEF_ROUND_TEST.get().getDefaultInstance(),
                BuiltInDefinitionIds.CUT_BEEF_ROUND, 7);
        assertResolvedOperation(ModItems.BEEF_SIRLOIN_TEST.get().getDefaultInstance(),
                BuiltInDefinitionIds.CUT_BEEF_SIRLOIN, 6);
    }

    @Test
    void bandsawDoesNotAcceptGrinderOperationsAndGrinderDoesNotAcceptBandsawOperation() {
        assertFailure(resolve(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance(), BandsawWorkstation.capability()),
                WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH);
        assertFailure(resolve(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance(), GrinderWorkstation.capability()),
                WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH);
    }

    @Test
    void quantityBelowForequarterMinimumIsRejected() {
        ItemStack tooLow = ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance();
        ProductStackAdapter.writeProductData(tooLow, ProductStackData.fromEngineValues(
                EngineId.of("butchercraft:beef_forequarter"),
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of("butchercraft:forequarter")),
                50_000,
                QuantityUnit.GRAM,
                700
        ));

        assertFailure(resolve(tooLow, BandsawWorkstation.capability()), WorkstationFailureCode.INPUT_QUANTITY_TOO_LOW);
    }

    private WorkstationOperationResolution resolve(ItemStack stack, com.butchercraft.workstation.WorkstationCapability capability) {
        return resolver.resolve(BuiltInProcessingDefinitions.builtInView(), capability, stack);
    }

    private static void assertBandsawOperation(
            com.butchercraft.processing.definition.ProcessingOperationDefinition operation,
            int outputCount
    ) {
        assertEquals(BandsawWorkstation.CAPABILITY_ID, operation.workstationCapability().orElseThrow());
        assertEquals(outputCount, operation.outputs().size());
    }

    private void assertResolvedOperation(ItemStack stack, ResourceLocation operationId, int outputCount) {
        WorkstationOperationResolution result = resolve(stack, BandsawWorkstation.capability());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(operationId, result.operation().orElseThrow().operationId());
        assertEquals(120, result.operation().orElseThrow().totalTicks());
        assertEquals(outputCount, result.operation().orElseThrow().engineOperation().outputs().size());
    }

    private static void assertFailure(WorkstationOperationResolution result, WorkstationFailureCode code) {
        assertTrue(result.failure().isPresent(), result.toString());
        assertEquals(code, result.failure().orElseThrow().code());
    }
}
