package com.butchercraft.world.production;

import com.butchercraft.world.production.persistence.ProductionPersistenceSnapshot;
import com.butchercraft.world.production.persistence.ProductionStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void everyProcessPlanAndRunFieldRoundTripsDeterministically() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionStorage storage = storage(context);

        String processes = storage.serializeProcesses(manager.processRegistry());
        String plans = storage.serializePlans(manager.planRegistry());
        String runs = storage.serializeRuns(manager.runs());
        ProductionPersistenceSnapshot snapshot = storage.deserialize(processes, plans, runs);

        assertEquals(manager.processRegistry().definitions(), snapshot.processRegistry().definitions());
        assertEquals(manager.planRegistry().definitions(), snapshot.planRegistry().definitions());
        assertEquals(manager.runs(), snapshot.runs());
        assertEquals(processes, storage.serializeProcesses(snapshot.processRegistry()));
        assertEquals(plans, storage.serializePlans(snapshot.planRegistry()));
        assertEquals(runs, storage.serializeRuns(snapshot.runs()));
    }

    @Test
    void storageSavesAndReloadsAllThreeFilesAsOneValidatedPublication() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionStorage storage = storage(context);
        storage.save(manager);

        ProductionManager loaded = storage.load();
        assertEquals(1, loaded.processRegistry().size());
        assertEquals(1, loaded.planRegistry().size());
        assertEquals(1, loaded.runs().size());
        assertTrue(java.nio.file.Files.exists(temporaryDirectory.resolve(ProductionSchema.PROCESSES_FILE_NAME)));
        assertTrue(java.nio.file.Files.exists(temporaryDirectory.resolve(ProductionSchema.PLANS_FILE_NAME)));
        assertTrue(java.nio.file.Files.exists(temporaryDirectory.resolve(ProductionSchema.RUNS_FILE_NAME)));
    }

    @Test
    void malformedUnsupportedDuplicateAndPartialPersistenceFailVisibly() throws Exception {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionStorage storage = storage(context);
        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("{", "{\"schema_version\":1,\"plans\":[]}",
                        "{\"schema_version\":1,\"runs\":[]}"));
        assertThrows(IllegalArgumentException.class, () ->
                storage.deserializeProcesses("{\"schema_version\":2,\"processes\":[]}"));

        ProductionProcessDefinition process = ProductionTestFixtures.process();
        String one = storage.serializeProcesses(
                ProductionProcessRegistry.builder().register(process).build());
        String duplicate = one.replaceFirst("]\\s*}", "," + one.substring(
                one.indexOf("{", one.indexOf("\"processes\"")),
                one.lastIndexOf("]")) + "]}");
        assertThrows(IllegalArgumentException.class, () -> storage.deserializeProcesses(duplicate));

        java.nio.file.Files.writeString(
                temporaryDirectory.resolve(ProductionSchema.PROCESSES_FILE_NAME),
                "{\"schema_version\":1,\"processes\":[]}"
        );
        assertThrows(IllegalStateException.class, storage::load);
    }

    private ProductionStorage storage(ProductionTestFixtures.TestContext context) {
        return new ProductionStorage(
                temporaryDirectory.resolve(ProductionSchema.PROCESSES_FILE_NAME),
                temporaryDirectory.resolve(ProductionSchema.PLANS_FILE_NAME),
                temporaryDirectory.resolve(ProductionSchema.RUNS_FILE_NAME),
                context.dependencies()
        );
    }
}
