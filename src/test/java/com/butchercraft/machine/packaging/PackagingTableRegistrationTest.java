package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.test.TestProjectPaths;
import net.minecraft.world.item.BlockItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingTableRegistrationTest {
    @Test
    void packagingTableRegistrationsAreBound() {
        assertTrue(ModBlocks.PACKAGING_TABLE.isBound());
        assertTrue(ModItems.PACKAGING_TABLE.isBound());
        assertTrue(ModBlockEntityTypes.PACKAGING_TABLE.isBound());
        assertTrue(ModMenuTypes.PACKAGING_TABLE.isBound());
    }

    @Test
    void blockItemTargetsPackagingTableBlock() {
        BlockItem blockItem = assertInstanceOf(BlockItem.class, ModItems.PACKAGING_TABLE.get());

        assertEquals(ModBlocks.PACKAGING_TABLE.get(), blockItem.getBlock());
    }

    @Test
    void packagingTableAppearsInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"));

        assertTrue(source.contains("output.accept(ModItems.PACKAGING_TABLE.get())"));
    }

    @Test
    void packagingTableExposesItemHandlerCapability() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/registration/ModCapabilities.java"));

        assertTrue(source.contains("ModBlockEntityTypes.PACKAGING_TABLE.get()"));
        assertTrue(source.contains("(blockEntity, side) -> blockEntity.inventory()"));
    }
}
