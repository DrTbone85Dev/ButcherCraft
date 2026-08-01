package com.butchercraft.world.simulation.time;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldTimeDependencyBoundaryTest {
    @Test
    void pureWorldTimeModelDoesNotImportMinecraftOrNeoForgeApis() throws IOException {
        Path packagePath = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/simulation/time");
        try (var files = Files.list(packagePath)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("WorldTimeService.java")) {
                    continue;
                }
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
            }
        }
    }

    @Test
    void schedulerPlanningProductionExecutionAndWorkstationsDoNotOwnDayTimeMutation() throws IOException {
        List<Path> roots = List.of(
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/simulation/scheduler"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/planning"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/production"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/execution"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/workstation")
        );

        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("getDayTime("), file + " must not derive runtime authority from dayTime");
                    assertFalse(source.contains("setDayTime("), file + " must not mutate dayTime");
                    assertFalse(source.contains("WorldTimeService"), file + " must not consume world time authority");
                    if (!explicitProductionDeadlineCalendarConsumer(file)) {
                        assertFalse(source.contains("BusinessCalendarSnapshot"),
                                file + " must not own calendar decisions");
                    }
                }
            }
        }
    }

    private static boolean explicitProductionDeadlineCalendarConsumer(Path file) {
        String normalized = file.toString().replace('\\', '/');
        return normalized.endsWith("/world/production/ProductionDeadline.java")
                || normalized.endsWith("/world/production/ProductionManager.java")
                || normalized.endsWith("/world/production/ProductionRunRuntime.java");
    }
}
