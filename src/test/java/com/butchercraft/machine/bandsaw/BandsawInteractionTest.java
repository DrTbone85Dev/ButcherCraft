package com.butchercraft.machine.bandsaw;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsawInteractionTest {
    @Test
    void lowerPlacementRequiresUpperSpaceAndPlacesUpperHalf() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawBlock.java");

        assertTrue(source.contains("getStateForPlacement("));
        assertTrue(source.contains("pos.above()"));
        assertTrue(source.contains("canBeReplaced(context)"));
        assertTrue(source.contains("setPlacedBy("));
        assertTrue(source.contains("ModBlocks.BANDSAW_UPPER"));
        assertTrue(source.contains("state.getValue(FACING)"));
    }

    @Test
    void upperForwardsInteractionAndHasNoBlockEntity() throws IOException {
        String upper = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawUpperBlock.java");
        String blockEntities = source("src/main/java/com/butchercraft/registration/ModBlockEntityTypes.java");

        assertTrue(upper.contains("BandsawBlock.openMenu(level, pos.below(), player)"));
        assertTrue(upper.contains("useWithoutItem("));
        assertTrue(upper.contains("useItemOn("));
        assertFalse(blockEntities.contains("BANDSAW_UPPER"));
    }

    @Test
    void breakingEitherHalfRoutesCleanupThroughLowerHalf() throws IOException {
        String lower = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawBlock.java");
        String upper = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawUpperBlock.java");

        assertTrue(lower.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(lower.contains("level.removeBlock(upperPos, false)"));
        assertTrue(upper.contains("level.destroyBlock(pos.below(), true)"));
    }

    @Test
    void bandsawMenuAndScreenAreRegistered() throws IOException {
        String menus = source("src/main/java/com/butchercraft/registration/ModMenuTypes.java");
        String client = source("src/main/java/com/butchercraft/client/ButcherCraftClient.java");
        String screen = source("src/main/java/com/butchercraft/client/screen/BandsawScreen.java");

        assertTrue(menus.contains("BANDSAW = MENU_TYPES.register("));
        assertTrue(menus.contains("IMenuTypeExtension.create(BandsawMenu::new)"));
        assertTrue(client.contains("event.register(ModMenuTypes.BANDSAW.get(), BandsawScreen::new)"));
        assertTrue(client.contains("ModClientRegistrationStatus.markBandsawScreenRegistered()"));
        assertTrue(screen.contains("extends AbstractProcessingWorkstationScreen<BandsawMenu>"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
