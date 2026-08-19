package com.butchercraft.machine.grinder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.operation.MachineOperatingState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderRunStatePresentationTest {
    @Test
    void machineStatesHaveDedicatedPlayerFacingKeys() {
        for (MachineOperatingState state : MachineOperatingState.values()) {
            assertEquals(
                    "screen.butchercraft.grinder.machine_state." + state.serializedName(),
                    GrinderMenu.machineStatusKey(state)
            );
        }
    }

    @Test
    void offIsIdleWithNoCycleProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.idle",
                GrinderMenu.cycleStatusKey(MachineOperatingState.OFF, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.OFF, false, 100));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.OFF, true, 100));
    }

    @Test
    void runningShowsOnlyTheCurrentActiveChildProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.processing",
                GrinderMenu.cycleStatusKey(MachineOperatingState.RUNNING, true));
        assertEquals(37, GrinderMenu.cycleProgressPercent(MachineOperatingState.RUNNING, true, 37));
        assertEquals("screen.butchercraft.grinder.cycle_state.waiting_next_cycle",
                GrinderMenu.cycleStatusKey(MachineOperatingState.RUNNING, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.RUNNING, false, 100));
    }

    @Test
    void runningEmptyWaitsForInputWithResetProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.waiting_input",
                GrinderMenu.cycleStatusKey(MachineOperatingState.RUNNING_EMPTY, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.RUNNING_EMPTY, false, 100));
    }

    @Test
    void outputBlockedWaitsForCapacityWithResetProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.waiting_output_space",
                GrinderMenu.cycleStatusKey(MachineOperatingState.OUTPUT_BLOCKED, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.OUTPUT_BLOCKED, false, 100));
    }

    @Test
    void stoppingRetainsOnlyAStillActiveChildProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.processing",
                GrinderMenu.cycleStatusKey(MachineOperatingState.STOPPING, true));
        assertEquals(61, GrinderMenu.cycleProgressPercent(MachineOperatingState.STOPPING, true, 61));
        assertEquals("screen.butchercraft.grinder.cycle_state.stopping",
                GrinderMenu.cycleStatusKey(MachineOperatingState.STOPPING, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.STOPPING, false, 100));
    }

    @Test
    void restartRequiredWaitsForOperatorWithResetProgress() {
        assertEquals("screen.butchercraft.grinder.cycle_state.waiting_operator",
                GrinderMenu.cycleStatusKey(MachineOperatingState.RESTART_REQUIRED, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.RESTART_REQUIRED, true, 100));
    }

    @Test
    void exceptionalStatesRemainExplicitAndProgressFree() {
        assertEquals("screen.butchercraft.grinder.cycle_state.faulted",
                GrinderMenu.cycleStatusKey(MachineOperatingState.FAULTED, false));
        assertEquals("screen.butchercraft.grinder.cycle_state.recovery_required",
                GrinderMenu.cycleStatusKey(MachineOperatingState.RECOVERY_REQUIRED, false));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.FAULTED, true, 100));
        assertEquals(0, GrinderMenu.cycleProgressPercent(MachineOperatingState.RECOVERY_REQUIRED, true, 100));
    }

    @Test
    void runtimeAndGeneratedLanguageResourcesResolveEveryGrinderRunKey() throws IOException {
        assertLanguageResource("src/main/resources/assets/butchercraft/lang/en_us.json");
        assertLanguageResource("src/generated/resources/assets/butchercraft/lang/en_us.json");
    }

    @Test
    void presentationDependsOnSynchronizedStateRatherThanInventory() throws IOException {
        String menu = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/machine/grinder/GrinderMenu.java"));

        String presentation = menu.substring(menu.indexOf("static String machineStatusKey"));
        assertTrue(presentation.contains("MachineOperatingState state"));
        assertTrue(presentation.contains("boolean activeChild"));
        assertFalse(presentation.contains("inventory"));
        assertFalse(presentation.contains("ItemStack"));
    }

    private static void assertLanguageResource(String relativePath) throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath(relativePath))).getAsJsonObject();

        assertEquals("Start", value(language, "screen.butchercraft.grinder.start"));
        assertEquals("Stop", value(language, "screen.butchercraft.grinder.stop"));
        assertEquals("Resume", value(language, "screen.butchercraft.grinder.resume"));
        assertEquals("Machine: OFF", value(language, GrinderMenu.machineStatusKey(MachineOperatingState.OFF)));
        assertEquals("Machine: RUNNING", value(language, GrinderMenu.machineStatusKey(MachineOperatingState.RUNNING)));
        assertEquals("Machine: RUNNING - EMPTY",
                value(language, GrinderMenu.machineStatusKey(MachineOperatingState.RUNNING_EMPTY)));
        assertEquals("Machine: OUTPUT BLOCKED",
                value(language, GrinderMenu.machineStatusKey(MachineOperatingState.OUTPUT_BLOCKED)));
        assertEquals("Machine: STOPPING",
                value(language, GrinderMenu.machineStatusKey(MachineOperatingState.STOPPING)));
        assertEquals("Machine: RESTART REQUIRED",
                value(language, GrinderMenu.machineStatusKey(MachineOperatingState.RESTART_REQUIRED)));
        assertEquals("Waiting for input",
                value(language, "screen.butchercraft.grinder.cycle_state.waiting_input"));
        assertEquals("Waiting for output space",
                value(language, "screen.butchercraft.grinder.cycle_state.waiting_output_space"));
        assertEquals("Waiting for operator",
                value(language, "screen.butchercraft.grinder.cycle_state.waiting_operator"));
        assertEquals("Processing", value(language, "screen.butchercraft.grinder.cycle_state.processing"));
        assertEquals("Idle", value(language, "screen.butchercraft.grinder.cycle_state.idle"));
    }

    private static String value(JsonObject language, String key) {
        assertTrue(language.has(key), "Missing Grinder GUI translation: " + key);
        return language.get(key).getAsString();
    }
}
