package com.butchercraft.command;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentCheckpointCommandTest {
    @Test
    void checkpointCommandsRemainUnderDevelopmentDiagnosticBranch() throws IOException {
        String diagnostics = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));
        String checkpointCommands = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/DevelopmentCheckpointCommands.java"
        ));

        assertTrue(diagnostics.contains("Commands.literal(\"diagnostic\")"));
        assertTrue(diagnostics.contains("DevelopmentCheckpointCommands.branch()"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"checkpoint\")"));
        assertTrue(checkpointCommands.contains("ENABLE_DEVELOPMENT_DIAGNOSTIC"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"capture\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"create\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"status\")"));
        assertTrue(checkpointCommands.contains("source.hasPermission(2)"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"list\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"validate\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"inspect-selected\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"restore-selected\")"));
        assertTrue(checkpointCommands.contains("Commands.literal(\"workstation-projections\")"));
        assertTrue(checkpointCommands.contains("var service = DurableWorkstationProjectionService.INSTANCE"));
        assertTrue(checkpointCommands.contains("service.diagnostics(source.getServer())"));
        assertTrue(checkpointCommands.contains("rejectUnsafeLiveRestore"));
        assertFalse(checkpointCommands.contains("restoreSelectedControlled("),
                "Live command surface must not perform controlled harness restoration");
    }

    @Test
    void liveCheckpointHooksDoNotInstallStartupRestoration() throws IOException {
        String modEntry = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        String clockService = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/simulation/SimulationClockService.java"
        ));
        String schedulerService = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/SimulationSchedulerService.java"
        ));

        assertFalse(modEntry.contains("DevelopmentCheckpointHarness"));
        assertFalse(modEntry.contains("CheckpointFilesystemStore"));
        assertTrue(modEntry.contains("LiveCheckpointService.INSTANCE::advance"));
        assertTrue(modEntry.contains("LiveCheckpointService.INSTANCE::stop"));
        assertTrue(modEntry.contains("LiveCheckpointService.INSTANCE::initialize"));
        assertFalse(clockService.contains("DevelopmentCheckpointHarness"));
        assertFalse(clockService.contains("CheckpointFilesystemStore"));
        assertFalse(schedulerService.contains("DevelopmentCheckpointHarness"));
        assertFalse(schedulerService.contains("CheckpointFilesystemStore"));
    }
}
