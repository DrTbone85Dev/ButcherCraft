package com.butchercraft.machine.packaging;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingTableInteractionTest {
    @Test
    void rightClickInteractionOpensServerMenuForEmptyHandAndHeldItems() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/packaging/PackagingTableBlock.java");

        assertTrue(source.contains("protected InteractionResult useWithoutItem("));
        assertTrue(source.contains("protected ItemInteractionResult useItemOn("));
        assertTrue(source.contains("player.openMenu(blockEntity, pos)"));
        assertTrue(source.contains("InteractionResult.sidedSuccess(level.isClientSide)"));
        assertTrue(source.contains("ItemInteractionResult.sidedSuccess(level.isClientSide)"));
        assertTrue(source.contains("ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION"));
    }

    @Test
    void packagingTableHasPlacementFacingAndRemovalRecovery() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/packaging/PackagingTableBlock.java");

        assertTrue(source.contains("getStateForPlacement("));
        assertTrue(source.contains("context.getHorizontalDirection().getOpposite()"));
        assertTrue(source.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
    }

    @Test
    void packagingTableUsesProcessingBlockEntityFoundation() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/packaging/PackagingTableBlockEntity.java");

        assertTrue(source.contains("extends AbstractProcessingWorkstationBlockEntity"));
        assertTrue(source.contains("PackagingTableWorkstation.capability()"));
        assertTrue(source.contains("new WorkstationOperationResolver()"));
        assertTrue(source.contains("new PackagingTableExecutionStrategy()"));
        assertTrue(source.contains("Component.translatable(\"container.butchercraft.packaging_table\")"));
        assertTrue(source.contains("new PackagingTableMenu(containerId, playerInventory, this)"));
        assertTrue(source.contains("PackagingSupplyItemMappings.isKnownSupplyItem(stack)"));
    }

    @Test
    void packagingTableMenuAndScreenAreRegistered() throws IOException {
        String menus = source("src/main/java/com/butchercraft/registration/ModMenuTypes.java");
        String client = source("src/main/java/com/butchercraft/client/ButcherCraftClient.java");
        String screen = source("src/main/java/com/butchercraft/client/screen/PackagingTableScreen.java");

        assertTrue(menus.contains("PACKAGING_TABLE = MENU_TYPES.register("));
        assertTrue(menus.contains("IMenuTypeExtension.create(PackagingTableMenu::new)"));
        assertTrue(client.contains("event.register(ModMenuTypes.PACKAGING_TABLE.get(), PackagingTableScreen::new)"));
        assertTrue(client.contains("ModClientRegistrationStatus.markPackagingTableScreenRegistered()"));
        assertTrue(screen.contains("extends AbstractProcessingWorkstationScreen<PackagingTableMenu>"));
        assertTrue(screen.contains("slot.meat"));
        assertTrue(screen.contains("slot.tray"));
        assertTrue(screen.contains("slot.wrap"));
        assertTrue(screen.contains("slot.result"));
        assertTrue(screen.contains("renderProgress(guiGraphics"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
