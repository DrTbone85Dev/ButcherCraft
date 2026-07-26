package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PattyFormerAssetsTest {
    @Test
    void pattyFormerAssetsExist() {
        assertResourceExists("assets/butchercraft/blockstates/patty_former.json");
        assertResourceExists("assets/butchercraft/models/block/patty_former.json");
        assertResourceExists("assets/butchercraft/models/item/patty_former.json");
        assertResourceExists("assets/butchercraft/models/item/beef_patties.json");
        assertResourceExists("assets/butchercraft/textures/block/workstation/patty_former_frame.png");
        assertResourceExists("assets/butchercraft/textures/block/workstation/patty_former_surface.png");
        assertResourceExists("assets/butchercraft/textures/block/workstation/patty_former_press.png");
        assertResourceExists("assets/butchercraft/textures/item/product/beef_patties.png");
        assertResourceExists("data/butchercraft/loot_table/blocks/patty_former.json");
    }

    @Test
    void pattyFormerUsesDistinctGameplayTexturesAndBlockShape() throws IOException {
        var model = JsonParser.parseString(Files.readString(
                resourcePath("assets/butchercraft/models/block/patty_former.json")
        )).getAsJsonObject();

        var textures = model.getAsJsonObject("textures");
        assertEquals("butchercraft:block/workstation/patty_former_frame", textures.get("frame").getAsString());
        assertEquals("butchercraft:block/workstation/patty_former_surface", textures.get("surface").getAsString());
        assertEquals("butchercraft:block/workstation/patty_former_press", textures.get("press").getAsString());
        assertTrue(model.getAsJsonArray("elements").size() > 1,
                "Patty Former should not reuse the development cube shape");
    }

    @Test
    void pattyFormerBlockstateHasHorizontalFacingVariants() throws IOException {
        var blockstate = JsonParser.parseString(Files.readString(
                resourcePath("assets/butchercraft/blockstates/patty_former.json")
        )).getAsJsonObject();

        var variants = blockstate.getAsJsonObject("variants");
        assertTrue(variants.has("facing=north"));
        assertTrue(variants.has("facing=east"));
        assertTrue(variants.has("facing=south"));
        assertTrue(variants.has("facing=west"));
    }

    @Test
    void languageEntriesExist() throws IOException {
        var language = JsonParser.parseString(Files.readString(
                resourcePath("assets/butchercraft/lang/en_us.json")
        )).getAsJsonObject();

        assertEquals("Patty Former", language.get("block.butchercraft.patty_former").getAsString());
        assertEquals("Patty Former", language.get("container.butchercraft.patty_former").getAsString());
        assertEquals("Beef Patties", language.get("item.butchercraft.beef_patties").getAsString());
    }

    private static void assertResourceExists(String relativePath) {
        Path path = resourcePath(relativePath);
        assertTrue(Files.isRegularFile(path), "Missing Patty Former resource: " + relativePath);
    }

    private static Path resourcePath(String relativePath) {
        Path sourceResource = TestProjectPaths.projectPath("src/main/resources/" + relativePath);
        if (Files.isRegularFile(sourceResource)) {
            return sourceResource;
        }

        Path generatedResource = TestProjectPaths.projectPath("src/generated/resources/" + relativePath);
        if (Files.isRegularFile(generatedResource)) {
            return generatedResource;
        }

        return fail("Missing resource in src/main/resources or src/generated/resources: " + relativePath);
    }
}
