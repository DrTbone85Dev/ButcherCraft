package com.butchercraft.machine.grinder;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationState;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GrinderAssetsTest {
    @Test
    void grinderAssetsExist() {
        assertResourceExists("assets/butchercraft/blockstates/grinder.json");
        assertResourceExists("assets/butchercraft/models/block/grinder.json");
        assertResourceExists("assets/butchercraft/models/item/grinder.json");
        assertResourceExists("assets/butchercraft/textures/block/workstation/grinder_frame.png");
        assertResourceExists("assets/butchercraft/textures/block/workstation/grinder_surface.png");
        assertResourceExists("assets/butchercraft/textures/block/workstation/grinder_feed.png");
        assertResourceExists("data/butchercraft/loot_table/blocks/grinder.json");
    }

    @Test
    void grinderUsesDistinctGameplayTexturesAndBlockShape() throws IOException {
        var model = JsonParser.parseString(Files.readString(
                resourcePath("assets/butchercraft/models/block/grinder.json")
        )).getAsJsonObject();

        var textures = model.getAsJsonObject("textures");
        assertEquals("butchercraft:block/workstation/grinder_frame", textures.get("frame").getAsString());
        assertEquals("butchercraft:block/workstation/grinder_surface", textures.get("surface").getAsString());
        assertEquals("butchercraft:block/workstation/grinder_feed", textures.get("feed").getAsString());
        assertTrue(model.getAsJsonArray("elements").size() > 1, "Grinder should not reuse the development cube shape");
    }

    @Test
    void grinderBlockModelDoesNotCullPartialMachineFaces() throws IOException {
        String model = Files.readString(resourcePath("assets/butchercraft/models/block/grinder.json"));

        assertTrue(!model.contains("\"cullface\""), "Grinder model must not cull partial machine faces");
    }

    @Test
    void grinderBlockstateHasHorizontalFacingVariants() throws IOException {
        var blockstate = JsonParser.parseString(Files.readString(
                resourcePath("assets/butchercraft/blockstates/grinder.json")
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

        assertEquals("Grinder", language.get("block.butchercraft.grinder").getAsString());
        assertEquals("Grinder", language.get("container.butchercraft.grinder").getAsString());
        for (WorkstationFailureCode code : WorkstationFailureCode.values()) {
            assertTrue(language.has(code.messageKey()), "Missing translation for " + code.messageKey());
        }
        for (WorkstationState state : WorkstationState.values()) {
            assertTrue(language.has(state.messageKey()), "Missing translation for " + state.messageKey());
        }
    }

    private static void assertResourceExists(String relativePath) {
        Path path = resourcePath(relativePath);
        assertTrue(Files.isRegularFile(path), "Missing Grinder resource: " + relativePath);
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
