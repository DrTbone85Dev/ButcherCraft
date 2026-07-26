package com.butchercraft.machine.pattyformer;

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

class PattyFormerRegistrationTest {
    @Test
    void pattyFormerRegistrationsAreBound() {
        assertTrue(ModBlocks.PATTY_FORMER.isBound());
        assertTrue(ModItems.PATTY_FORMER.isBound());
        assertTrue(ModItems.BEEF_PATTIES.isBound());
        assertTrue(ModBlockEntityTypes.PATTY_FORMER.isBound());
        assertTrue(ModMenuTypes.PATTY_FORMER.isBound());
    }

    @Test
    void blockItemTargetsPattyFormerBlock() {
        BlockItem blockItem = assertInstanceOf(BlockItem.class, ModItems.PATTY_FORMER.get());

        assertEquals(ModBlocks.PATTY_FORMER.get(), blockItem.getBlock());
    }

    @Test
    void pattyFormerAndBeefPattiesAppearInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"
        ));

        assertTrue(source.contains("output.accept(ModItems.PATTY_FORMER.get())"));
        assertTrue(source.contains("output.accept(ModItems.BEEF_PATTIES.get().getDefaultInstance())"));
    }
}
