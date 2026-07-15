package com.butchercraft.workstation;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import net.minecraft.world.item.BlockItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationRegistrationTest {
    @Test
    void developmentWorkstationRegistrationsAreBound() {
        assertTrue(ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.isBound());
        assertTrue(ModItems.DEVELOPMENT_PROCESSING_WORKSTATION.isBound());
        assertTrue(ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound());
        assertTrue(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound());
    }

    @Test
    void blockItemTargetsDevelopmentWorkstationBlock() {
        BlockItem blockItem = assertInstanceOf(BlockItem.class, ModItems.DEVELOPMENT_PROCESSING_WORKSTATION.get());

        assertEquals(ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get(), blockItem.getBlock());
    }
}
