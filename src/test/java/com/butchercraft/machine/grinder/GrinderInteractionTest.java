package com.butchercraft.machine.grinder;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderInteractionTest {
    @Test
    void rightClickInteractionOpensServerMenuForEmptyHandAndHeldItems() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlock.java");

        assertTrue(source.contains("protected InteractionResult useWithoutItem("), "Empty-hand interaction must be handled");
        assertTrue(source.contains("protected ItemInteractionResult useItemOn("), "Held-item interaction must be handled");
        assertTrue(source.contains("player.openMenu(blockEntity, pos)"), "Server should open the block entity menu");
        assertTrue(source.contains("InteractionResult.sidedSuccess(level.isClientSide)"));
        assertTrue(source.contains("ItemInteractionResult.sidedSuccess(level.isClientSide)"));
        assertTrue(source.contains("ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION"));
    }

    @Test
    void grinderBlockEntityProvidesGrinderMenu() throws IOException {
        String baseSource = source("src/main/java/com/butchercraft/workstation/block/AbstractProcessingWorkstationBlockEntity.java");
        String source = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java");

        assertTrue(baseSource.contains("implements MenuProvider"));
        assertTrue(source.contains("Component.translatable(\"container.butchercraft.grinder\")"));
        assertTrue(source.contains("new GrinderMenu(containerId, playerInventory, this)"));
        assertTrue(source.contains("WorkstationExecutionStrategy.transformation()"));
    }

    @Test
    void grinderAndBandsawOptIntoSeparateTransformationExecutionStrategies() throws IOException {
        String grinderSource = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java");
        String bandsawSource = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawBlockEntity.java");

        assertTrue(grinderSource.contains("WorkstationExecutionStrategy.transformation()"));
        assertTrue(bandsawSource.contains("WorkstationExecutionStrategy.atomicTransformation()"));
    }

    @Test
    void grinderMenuAndScreenAreRegistered() throws IOException {
        String menus = source("src/main/java/com/butchercraft/registration/ModMenuTypes.java");
        String client = source("src/main/java/com/butchercraft/client/ButcherCraftClient.java");
        String screen = source("src/main/java/com/butchercraft/client/screen/GrinderScreen.java");

        assertTrue(menus.contains("GRINDER = MENU_TYPES.register("));
        assertTrue(menus.contains("IMenuTypeExtension.create(GrinderMenu::new)"));
        assertTrue(client.contains("value = Dist.CLIENT"));
        assertTrue(client.contains("event.register(ModMenuTypes.GRINDER.get(), GrinderScreen::new)"));
        assertTrue(client.contains("ModClientRegistrationStatus.markGrinderScreenRegistered()"));
        assertTrue(screen.contains("extends AbstractProcessingWorkstationScreen<GrinderMenu>"));
    }

    @Test
    void menuSynchronizesProgressAndKeepsSlotsDirectional() throws IOException {
        String source = source("src/main/java/com/butchercraft/workstation/menu/ProcessingWorkstationMenu.java");

        assertTrue(source.contains("addDataSlots(data)"));
        assertTrue(source.contains("progressPercent()"));
        assertTrue(source.contains("statusComponent()"));
        assertTrue(source.contains("addSlot(new SlotItemHandler(inventory, inventory.firstInputSlot()"));
        assertTrue(source.contains("addSlot(new OutputSlot(inventory"));
        assertTrue(source.contains("for (int outputIndex = 0; outputIndex < inventory.outputSlotCount(); outputIndex++)"));
        assertTrue(source.contains("public boolean mayPlace(ItemStack stack)"));
        assertTrue(source.contains("inventory.isInputLocked()"));
        assertTrue(source.contains("inventory.isOutputExtractionAllowed()"));
        assertTrue(source.contains("playerInventoryStart = workstationSlotCount"));
        assertTrue(source.contains("moveItemStackTo(original, inventory.firstInputSlot(), inventory.firstOutputSlot(), false)"));
    }

    @Test
    void blockRemovalDropsRecoverableContents() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlock.java");

        assertTrue(source.contains("onRemove("));
        assertTrue(source.contains("blockEntity.dropContents(level, pos)"));
    }

    @Test
    void diagnosticReportsGrinderChecks() throws IOException {
        String source = source("src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java");

        assertTrue(source.contains("private static GrinderDiagnostic verifyGrinder("));
        assertTrue(source.contains("ModBlockEntityTypes.GRINDER"));
        assertTrue(source.contains("ModMenuTypes.GRINDER"));
        assertTrue(source.contains("GrinderWorkstation.capability()"));
        assertTrue(source.contains("GrinderWorkstation.CAPABILITY_ID"));
        assertTrue(source.contains("BuiltInDefinitionIds.GRIND_BEEF"));
        assertTrue(source.contains("BuiltInDefinitionIds.GRIND_PORK"));
        assertTrue(source.contains("BuiltInDefinitionIds.GRIND_BISON"));
        assertTrue(source.contains("WorkstationDuration.millisecondsToTicks(3_000)"));
        assertTrue(source.contains("DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF)"));
        assertTrue(source.contains("DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_PORK)"));
        assertTrue(source.contains("DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BISON)"));
        assertTrue(source.contains("Grinder block registered: "));
        assertTrue(source.contains("Grinder block entity registered: "));
        assertTrue(source.contains("Grinder menu registered: "));
        assertTrue(source.contains("Grinder screen binding observed: "));
        assertTrue(source.contains("Grinder capability available: "));
        assertTrue(source.contains("Built-in grind_beef supports Grinder capability: "));
        assertTrue(source.contains("Built-in grind_pork supports Grinder capability: "));
        assertTrue(source.contains("Built-in grind_bison supports Grinder capability: "));
        assertTrue(source.contains("Beef Trim resolves to grind_beef for Grinder: "));
        assertTrue(source.contains("Pork Trim resolves to grind_pork for Grinder: "));
        assertTrue(source.contains("Bison Trim resolves to grind_bison for Grinder: "));
        assertTrue(source.contains("Grinder grind_beef duration resolves to 60 ticks: "));
        assertTrue(source.contains("Ground Beef output mapping resolves for Grinder: "));
        assertTrue(source.contains("Ground Pork output mapping resolves for Grinder: "));
        assertTrue(source.contains("Ground Bison output mapping resolves for Grinder: "));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
