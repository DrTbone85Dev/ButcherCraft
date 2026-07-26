package com.butchercraft.machine.pattyformer;

import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerOperationResolverTest {
    private final WorkstationOperationResolver resolver = new WorkstationOperationResolver();

    @Test
    void pattyFormerCapabilityIdIsStable() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("butchercraft", "patty_forming"),
                PattyFormerWorkstation.CAPABILITY_ID);
        assertTrue(PattyFormerWorkstation.capability().supportsWorkstationCapability(
                PattyFormerWorkstation.CAPABILITY_ID
        ));
    }

    @Test
    void builtInFormBeefPattiesRequiresPattyFormerCapability() {
        assertEquals(
                PattyFormerWorkstation.CAPABILITY_ID,
                BuiltInProcessingDefinitions.formBeefPattiesOperation().workstationCapability().orElseThrow()
        );
    }

    @Test
    void groundBeefFindsFormBeefPattiesForPattyFormer() {
        WorkstationOperationResolution result = resolve(ModItems.GROUND_BEEF.get().getDefaultInstance());

        assertTrue(result.succeeded(), result.toString());
        assertEquals(BuiltInDefinitionIds.FORM_BEEF_PATTIES, result.operation().orElseThrow().operationId());
        assertEquals(BuiltInDefinitionIds.BEEF_PATTIES,
                result.operation().orElseThrow().definition().operation().outputProduct());
        assertEquals(60, result.operation().orElseThrow().totalTicks());
    }

    @Test
    void secondPattyFormerRecipeIsNotPromotedByThisMilestone() {
        WorkstationOperationResolution result = resolve(ModItems.GROUND_PORK.get().getDefaultInstance());

        assertFailure(result, WorkstationFailureCode.NO_COMPATIBLE_OPERATION);
    }

    @Test
    void trimInputRemainsOwnedByGrinderCapability() {
        WorkstationOperationResolution result = resolve(ModItems.BEEF_TRIM.get().getDefaultInstance());

        assertFailure(result, WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH);
    }

    private WorkstationOperationResolution resolve(net.minecraft.world.item.ItemStack stack) {
        return resolver.resolve(BuiltInProcessingDefinitions.builtInView(), PattyFormerWorkstation.capability(), stack);
    }

    private static void assertFailure(WorkstationOperationResolution result, WorkstationFailureCode code) {
        assertTrue(result.failure().isPresent(), result.toString());
        assertEquals(code, result.failure().orElseThrow().code());
    }
}
