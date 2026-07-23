package com.butchercraft.world.simulation.scheduler;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerDependencyBoundaryTest {
    @Test
    void schedulerDomainHasNoMinecraftNeoForgeGameplayOrWallClockDependencies() throws IOException {
        Path packageRoot = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/simulation/scheduler"
        );
        List<String> forbidden = List.of(
                "import net.minecraft", "import net.neoforged", "ItemStack", "BlockEntity",
                "System.currentTimeMillis", "System.nanoTime", "Instant.now", "LocalDateTime.now",
                "ScheduledExecutorService", "java.util.Timer", "UUID.randomUUID"
        );
        try (var paths = Files.walk(packageRoot)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String token : forbidden) {
                    assertFalse(source.contains(token), file + " must not reference " + token);
                }
            }
        }
    }

    @Test
    void lifecycleServiceIsRegisteredAfterClockAndOrdersAndClearsOnShutdown() throws IOException {
        String entryPoint = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        int clockAdvance = entryPoint.indexOf("SimulationClockService.INSTANCE::advance");
        int orderInitialize = entryPoint.indexOf("OrderContractService.INSTANCE::initialize");
        int schedulerInitialize = entryPoint.indexOf("SimulationSchedulerService.INSTANCE::initialize");
        int schedulerAdvance = entryPoint.indexOf("SimulationSchedulerService.INSTANCE::advance");
        assertTrue(clockAdvance >= 0 && clockAdvance < schedulerAdvance);
        assertTrue(orderInitialize >= 0 && orderInitialize < schedulerInitialize);

        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/SimulationSchedulerService.java"
        ));
        assertTrue(service.contains("SimulationWorkHandlerRegistry.empty()"));
        assertTrue(service.contains("activeState.compareAndSet(active, null)"));
        assertTrue(service.contains("pipeline().execute(tick)"));
        assertTrue(service.contains("SchedulerSchema.FILE_NAME"));
    }
}
