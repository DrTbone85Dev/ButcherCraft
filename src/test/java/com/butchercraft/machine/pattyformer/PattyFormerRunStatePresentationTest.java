package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerRunStatePresentationTest {
    @Test
    void machineStatesHaveDedicatedPlayerFacingKeys() {
        for (MachineOperatingState state : MachineOperatingState.values()) {
            assertEquals(
                    "screen.butchercraft.patty_former.machine_state." + state.serializedName(),
                    PattyFormerMenu.machineStatusKey(state)
            );
        }
    }

    @Test
    void cyclePresentationUsesOnlyMachineStateAndCurrentChild() {
        assertCycle(MachineOperatingState.OFF, false, "idle", 0);
        assertCycle(MachineOperatingState.RUNNING, true, "processing", 37);
        assertCycle(MachineOperatingState.RUNNING, false, "waiting_next_cycle", 0);
        assertCycle(MachineOperatingState.RUNNING_EMPTY, false, "waiting_input", 0);
        assertCycle(MachineOperatingState.OUTPUT_BLOCKED, false, "waiting_output_space", 0);
        assertCycle(MachineOperatingState.STOPPING, true, "processing", 37);
        assertCycle(MachineOperatingState.STOPPING, false, "stopping", 0);
        assertCycle(MachineOperatingState.RESTART_REQUIRED, false, "waiting_operator", 0);
        assertCycle(MachineOperatingState.FAULTED, false, "faulted", 0);
        assertCycle(MachineOperatingState.RECOVERY_REQUIRED, false, "recovery_required", 0);
    }

    @Test
    void exceptionalAndInactiveStatesNeverRetainCompletedCycleProgress() {
        for (MachineOperatingState state : MachineOperatingState.values()) {
            int expected = state == MachineOperatingState.RUNNING || state == MachineOperatingState.STOPPING
                    ? 100 : 0;
            assertEquals(expected, PattyFormerMenu.cycleProgressPercent(state, true, 100));
            assertEquals(0, PattyFormerMenu.cycleProgressPercent(state, false, 100));
        }
    }

    @Test
    void runtimeAndGeneratedLanguageResourcesResolveEveryPattyFormerRunKey() throws IOException {
        assertLanguageResource("src/main/resources/assets/butchercraft/lang/en_us.json");
        assertLanguageResource("src/generated/resources/assets/butchercraft/lang/en_us.json");
    }

    @Test
    void presentationDependsOnSynchronizedStateRatherThanInventory() throws IOException {
        String menu = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/machine/pattyformer/PattyFormerMenu.java"));

        String presentation = menu.substring(menu.indexOf("static String machineStatusKey"));
        assertTrue(presentation.contains("MachineOperatingState state"));
        assertTrue(presentation.contains("boolean activeChild"));
        assertFalse(presentation.contains("inventory"));
        assertFalse(presentation.contains("ItemStack"));
    }

    private static void assertCycle(
            MachineOperatingState state,
            boolean activeChild,
            String expectedKeySuffix,
            int expectedProgress
    ) {
        assertEquals("screen.butchercraft.patty_former.cycle_state." + expectedKeySuffix,
                PattyFormerMenu.cycleStatusKey(state, activeChild));
        assertEquals(expectedProgress,
                PattyFormerMenu.cycleProgressPercent(state, activeChild, 37));
    }

    private static void assertLanguageResource(String relativePath) throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath(relativePath))).getAsJsonObject();

        assertEquals("Start", value(language, "screen.butchercraft.patty_former.start"));
        assertEquals("Stop", value(language, "screen.butchercraft.patty_former.stop"));
        assertEquals("Resume", value(language, "screen.butchercraft.patty_former.resume"));
        assertEquals("Machine: OFF", value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.OFF)));
        assertEquals("Machine: RUNNING",
                value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.RUNNING)));
        assertEquals("Machine: RUNNING - EMPTY",
                value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.RUNNING_EMPTY)));
        assertEquals("Machine: OUTPUT BLOCKED",
                value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.OUTPUT_BLOCKED)));
        assertEquals("Machine: STOPPING",
                value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.STOPPING)));
        assertEquals("Machine: RESTART REQUIRED",
                value(language, PattyFormerMenu.machineStatusKey(MachineOperatingState.RESTART_REQUIRED)));
        assertEquals("Waiting for input", value(language,
                "screen.butchercraft.patty_former.cycle_state.waiting_input"));
        assertEquals("Waiting for output space", value(language,
                "screen.butchercraft.patty_former.cycle_state.waiting_output_space"));
        assertEquals("Waiting for operator", value(language,
                "screen.butchercraft.patty_former.cycle_state.waiting_operator"));
        assertEquals("Processing", value(language,
                "screen.butchercraft.patty_former.cycle_state.processing"));
        assertEquals("Idle", value(language, "screen.butchercraft.patty_former.cycle_state.idle"));
    }

    private static String value(JsonObject language, String key) {
        assertTrue(language.has(key), "Missing Patty Former GUI translation: " + key);
        return language.get(key).getAsString();
    }
}
