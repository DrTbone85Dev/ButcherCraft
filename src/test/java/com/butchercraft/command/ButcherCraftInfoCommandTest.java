package com.butchercraft.command;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherCraftInfoCommandTest {
    @Test
    void infoCommandBuildsPublicFacingTranslatableLines() {
        List<ButcherCraftDiagnostics.InfoMessageLine> lines = ButcherCraftDiagnostics.infoLines("test-version");

        assertEquals(3, lines.size());
        assertEquals("commands.butchercraft.info.title", lines.get(0).translationKey());
        assertEquals(List.of(), lines.get(0).arguments());
        assertEquals("commands.butchercraft.info.version", lines.get(1).translationKey());
        assertEquals(List.of("test-version"), lines.get(1).arguments());
        assertEquals("commands.butchercraft.info.status", lines.get(2).translationKey());
        assertEquals(List.of(), lines.get(2).arguments());
    }

    @Test
    void infoCommandIsRegisteredWithoutOperatorGateOrDiagnosticConfigGate() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));

        int infoIndex = source.indexOf("Commands.literal(\"info\")");
        int diagnosticIndex = source.indexOf("Commands.literal(\"diagnostic\")");
        assertTrue(infoIndex > 0, "Missing /butchercraft info command branch");
        assertTrue(diagnosticIndex > infoIndex, "Info command should be registered before diagnostic branch");

        String infoBranch = source.substring(infoIndex, diagnosticIndex);
        assertTrue(infoBranch.contains("runInfo(context.getSource())"));
        assertFalse(infoBranch.contains(".requires("), "Info command should be available to ordinary players");
        assertFalse(infoBranch.contains("ENABLE_DEVELOPMENT_DIAGNOSTIC"), "Info command must not use the diagnostic config gate");
    }

    @Test
    void infoCommandLanguageEntriesArePublicAndConcise() throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/lang/en_us.json")
        )).getAsJsonObject();

        assertEquals("ButcherCraft", language.get("commands.butchercraft.info.title").getAsString());
        assertEquals(
                "Version %s - Early Development / Project Meat Counter",
                language.get("commands.butchercraft.info.version").getAsString()
        );
        assertEquals(
                "Processing, inventory, employee, and business simulation systems are under active development.",
                language.get("commands.butchercraft.info.status").getAsString()
        );
    }
}
