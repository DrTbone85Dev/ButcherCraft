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

        assertTrue(source.contains(".icon(() -> ModItems.GRINDER.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GRINDER.get())"));
        assertTrue(source.contains("output.accept(ModItems.BEEF_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_BEEF.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.PORK_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_PORK.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.CHICKEN_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_CHICKEN.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.BUFFALO_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_BUFFALO.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.LAMB_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_LAMB.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.VENISON_TRIM.get().getDefaultInstance())"));
        assertTrue(source.contains("output.accept(ModItems.GROUND_VENISON.get().getDefaultInstance())"));
    }
}
