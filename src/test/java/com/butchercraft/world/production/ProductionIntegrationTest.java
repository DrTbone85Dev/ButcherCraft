package com.butchercraft.world.production;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionIntegrationTest {
    @Test
    void modRegistersProductionAroundSchedulerInRequiredInitializationOrder() throws Exception {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"));
        int productionInitialize = source.indexOf("ProductionService.INSTANCE::initialize");
        int schedulerInitialize = source.indexOf("SimulationSchedulerService.INSTANCE::initialize");
        int productionBind = source.indexOf("ProductionService.INSTANCE::bindScheduler");
        int schedulerAdvance = source.indexOf("SimulationSchedulerService.INSTANCE::advance");

        assertTrue(productionInitialize >= 0);
        assertTrue(productionInitialize < schedulerInitialize);
        assertTrue(schedulerInitialize < productionBind);
        assertTrue(productionBind < schedulerAdvance);
        assertTrue(source.contains("ProductionService.INSTANCE::save"));
    }

    @Test
    void serviceUsesThreeWorldOwnedSchemaFilesAndExplicitHandlerInstallation() throws Exception {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/ProductionService.java"));
        String scheduler = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/SimulationSchedulerService.java"));

        assertTrue(service.contains("ProductionSchema.PROCESSES_FILE_NAME"));
        assertTrue(service.contains("ProductionSchema.PLANS_FILE_NAME"));
        assertTrue(service.contains("ProductionSchema.RUNS_FILE_NAME"));
        assertTrue(service.contains("new ProductionSimulationWorkHandler(manager)"));
        assertTrue(scheduler.contains("installHandler(SimulationWorkHandler handler)"));
    }
}
