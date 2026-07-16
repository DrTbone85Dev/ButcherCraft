package com.butchercraft.workstation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationInteractionTest {
    @Test
    void rightClickInteractionOpensServerMenuForEmptyHandAndHeldItems() throws IOException {
        String source = source("src/main/java/com/butchercraft/workstation/block/ProcessingWorkstationBlock.java");

        assertTrue(source.contains("protected InteractionResult useWithoutItem("), "Empty-hand interaction must be handled");
        assertTrue(source.contains("protected ItemInteractionResult useItemOn("), "Held-item interaction must be handled");
        assertTrue(source.contains("player.openMenu(blockEntity, pos)"), "Server should open the block entity menu");
        assertTrue(source.contains("InteractionResult.sidedSuccess(level.isClientSide)"), "Empty-hand interaction should report success on both sides");
        assertTrue(source.contains("ItemInteractionResult.sidedSuccess(level.isClientSide)"), "Held-item interaction should report success on both sides");
        assertTrue(source.contains("ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION"), "Missing block entity should fall back safely");
    }

    @Test
    void blockEntityIsTheMenuProvider() throws IOException {
        String baseSource = source("src/main/java/com/butchercraft/workstation/block/AbstractProcessingWorkstationBlockEntity.java");
        String source = source("src/main/java/com/butchercraft/workstation/block/ProcessingWorkstationBlockEntity.java");

        assertTrue(baseSource.contains("implements MenuProvider"), "Block entity base should provide the menu");
        assertTrue(source.contains("extends AbstractProcessingWorkstationBlockEntity"), "Development block entity should inherit menu-provider behavior");
        assertTrue(source.contains("Component.translatable(\"container.butchercraft.development_processing_workstation\")"));
        assertTrue(source.contains("new ProcessingWorkstationMenu(containerId, playerInventory, this)"));
    }

    @Test
    void clientScreenIsRegisteredForDevelopmentWorkstationMenu() throws IOException {
        String registration = source("src/main/java/com/butchercraft/client/ButcherCraftClient.java");
        String abstractScreen = source("src/main/java/com/butchercraft/client/screen/AbstractProcessingWorkstationScreen.java");
        String screen = source("src/main/java/com/butchercraft/client/screen/ProcessingWorkstationScreen.java");

        assertTrue(registration.contains("RegisterMenuScreensEvent"));
        assertTrue(registration.contains("value = Dist.CLIENT"));
        assertTrue(registration.contains("event.register(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(), ProcessingWorkstationScreen::new)"));
        assertTrue(registration.contains("ModClientRegistrationStatus.markDevelopmentWorkstationScreenRegistered()"));
        assertTrue(abstractScreen.contains("extends AbstractContainerScreen<T>"));
        assertTrue(screen.contains("extends AbstractProcessingWorkstationScreen<ProcessingWorkstationMenu>"));
    }

    @Test
    void diagnosticReportsClientScreenBindingStatus() throws IOException {
        String source = source("src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java");

        assertTrue(source.contains("Development workstation block registered: "));
        assertTrue(source.contains("Development workstation block entity registered: "));
        assertTrue(source.contains("Development workstation menu registered: "));
        assertTrue(source.contains("Development workstation screen binding observed: "));
        assertTrue(source.contains("ModClientRegistrationStatus.developmentWorkstationScreenRegistered()"));
    }

    @Test
    void menuSynchronizesProgressAndKeepsSlotsDirectional() throws IOException {
        String source = source("src/main/java/com/butchercraft/workstation/menu/ProcessingWorkstationMenu.java");

        assertTrue(source.contains("addDataSlots(data)"), "Progress and state should be synced through menu data");
        assertTrue(source.contains("elapsedTicks()"));
        assertTrue(source.contains("totalTicks()"));
        assertTrue(source.contains("progressPercent()"));
        assertTrue(source.contains("statusComponent()"));
        assertTrue(source.contains("addSlot(new SlotItemHandler(inventory, WorkstationInventory.INPUT_SLOT"));
        assertTrue(source.contains("addSlot(new OutputSlot(inventory"));
        assertTrue(source.contains("public boolean mayPlace(ItemStack stack)"));
        assertTrue(source.contains("return false;"), "Output slot should reject manual insertion");
        assertTrue(source.contains("inventory.isInputLocked()"), "Shift-click should respect active input reservation");
        assertTrue(source.contains("inventory.isOutputExtractionAllowed()"), "Shift-click should respect output extraction state");
        assertTrue(source.contains("moveItemStackTo(original, WorkstationInventory.INPUT_SLOT, WorkstationInventory.INPUT_SLOT + 1, false)"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
