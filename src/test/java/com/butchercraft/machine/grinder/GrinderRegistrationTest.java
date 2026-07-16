package com.butchercraft.machine.grinder;

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

class GrinderRegistrationTest {
    @Test
    void grinderRegistrationsAreBound() {
        assertTrue(ModBlocks.GRINDER.isBound());
        assertTrue(ModItems.GRINDER.isBound());
        assertTrue(ModBlockEntityTypes.GRINDER.isBound());
        assertTrue(ModMenuTypes.GRINDER.isBound());
    }

    @Test
    void blockItemTargetsGrinderBlock() {
        BlockItem blockItem = assertInstanceOf(BlockItem.class, ModItems.GRINDER.get());

        assertEquals(ModBlocks.GRINDER.get(), blockItem.getBlock());
    }

    @Test
    void grinderAppearsInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"));

        assertTrue(source.contains("output.accept(ModItems.GRINDER.get())"));
    }
}
