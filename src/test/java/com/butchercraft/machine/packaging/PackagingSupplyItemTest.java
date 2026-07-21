package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModItems;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingSupplyItemTest {
    private static final List<SupplyItem> SUPPLY_ITEMS = List.of(
            new SupplyItem("foam_tray", "Foam Tray", ModItems.FOAM_TRAY),
            new SupplyItem("plastic_wrap_roll", "Plastic Wrap Roll", ModItems.PLASTIC_WRAP_ROLL),
            new SupplyItem("vacuum_bag", "Vacuum Bag", ModItems.VACUUM_BAG),
            new SupplyItem("butcher_paper_roll", "Butcher Paper Roll", ModItems.BUTCHER_PAPER_ROLL),
            new SupplyItem("freezer_paper_roll", "Freezer Paper Roll", ModItems.FREEZER_PAPER_ROLL),
            new SupplyItem("retail_label_roll", "Retail Label Roll", ModItems.RETAIL_LABEL_ROLL)
    );

    @Test
    void packagingSupplyItemsAreRegistered() {
        for (SupplyItem item : SUPPLY_ITEMS) {
            assertTrue(item.registration().isBound(), "Expected registered packaging supply item " + item.id());
            assertEquals("butchercraft:" + item.id(), item.registration().getId().toString());
        }
    }

    @Test
    void packagingSupplyItemMappingsIdentifyKnownSuppliesOnly() {
        assertTrue(PackagingSupplyItemMappings.matches(
                new ItemStack(ModItems.FOAM_TRAY.get()),
                com.butchercraft.packaging.definition.BuiltInPackagingRegistry.FOAM_TRAY
        ));
        assertTrue(PackagingSupplyItemMappings.isKnownSupplyItem(new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get())));
        assertTrue(!PackagingSupplyItemMappings.isKnownSupplyItem(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance()));
    }

    @Test
    void packagingSupplyItemsAppearInCreativeTabSource() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/registration/ModCreativeModeTabs.java"
        ));

        for (SupplyItem item : SUPPLY_ITEMS) {
            assertTrue(source.contains("output.accept(ModItems." + item.constantName() + ".get())"),
                    "Missing creative tab entry for " + item.id());
        }
    }

    @Test
    void packagingSupplyItemsHaveLocalizationEntries() throws IOException {
        String provider = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/data/ButcherCraftLanguageProvider.java"
        ));
        String language = Files.readString(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/lang/en_us.json"
        ));

        for (SupplyItem item : SUPPLY_ITEMS) {
            assertTrue(provider.contains("add(ModItems." + item.constantName() + ".get(), \"" + item.displayName() + "\")"),
                    "Missing language provider entry for " + item.id());
            assertTrue(language.contains("\"item.butchercraft." + item.id() + "\": \"" + item.displayName() + "\""),
                    "Missing language resource entry for " + item.id());
        }
    }

    @Test
    void packagingSupplyItemsHaveGeneratedModelsAndStableTextureTargets() throws IOException {
        for (SupplyItem item : SUPPLY_ITEMS) {
            Path texture = TestProjectPaths.projectPath(
                    "src/main/resources/assets/butchercraft/textures/item/packaging/" + item.id() + ".png"
            );
            assertTextureDimensions(texture, 16, 16);

            var model = JsonParser.parseString(Files.readString(TestProjectPaths.projectPath(
                    "src/generated/resources/assets/butchercraft/models/item/" + item.id() + ".json"
            ))).getAsJsonObject();

            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("butchercraft:item/packaging/" + item.id(),
                    model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    private static void assertTextureDimensions(Path texture, int expectedWidth, int expectedHeight) throws IOException {
        assertTrue(Files.isRegularFile(texture), "Expected texture at " + texture);
        BufferedImage image = ImageIO.read(texture.toFile());
        assertEquals(expectedWidth, image.getWidth(), "Unexpected width for " + texture);
        assertEquals(expectedHeight, image.getHeight(), "Unexpected height for " + texture);
    }

    private record SupplyItem(String id, String displayName, DeferredItem<? extends Item> registration) {
        String constantName() {
            return id.toUpperCase(java.util.Locale.ROOT);
        }
    }
}
