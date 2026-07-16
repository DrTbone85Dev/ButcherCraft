package com.butchercraft.machine.bandsaw;

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

class BandsawRegistrationTest {
    @Test
    void bandsawRegistrationsAreBound() {
        assertTrue(ModBlocks.BANDSAW.isBound());
        assertTrue(ModBlocks.BANDSAW_UPPER.isBound());
        assertTrue(ModItems.BANDSAW.isBound());
        assertTrue(ModBlockEntityTypes.BANDSAW.isBound());
        assertTrue(ModMenuTypes.BANDSAW.isBound());
    }

    @Test
    void blockItemTargetsLowerBandsawBlock() {
        BlockItem blockItem = assertInstanceOf(BlockItem.class, ModItems.BANDSAW.get());

        assertEquals(ModBlocks.BANDSAW.get(), blockItem.getBlock());
    }

    @Test
    void bandsawAppearsInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"));

        assertTrue(source.contains("output.accept(ModItems.BANDSAW.get())"));
    }
}
