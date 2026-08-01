package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeConfigurationIdentity;
import com.butchercraft.world.production.persistence.ProductionPersistenceSnapshot;
import com.butchercraft.world.production.persistence.ProductionStorage;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPersistenceTest {
    private static final BusinessRuntimeConfigurationIdentity BUSINESS_RUNTIME_CONFIGURATION_ID =
            BusinessRuntimeCalendarConfiguration.defaults(WorldTimeConfiguration.enabled(60).identity()).identity();

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
    void productionDeadlineRoundTripsWithRunPersistence() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionRunSnapshot run = manager.runs().getFirst();
        ProductionDeadline deadline = ProductionDeadline.target(
                run.id(),
                calendar(0L, 10, 0),
                BUSINESS_RUNTIME_CONFIGURATION_ID,
                120,
                "butchercraft:test_deadline"
        );
        assertTrue(manager.setDeadline(run.id(), deadline, 1L).accepted());
        ProductionStorage storage = storage(context);

        String runs = storage.serializeRuns(manager.runs());
        ProductionRunSnapshot restored = storage.deserializeRuns(runs).getFirst();

        assertEquals(manager.runs().getFirst(), restored);
        assertEquals(deadline.identity(), restored.deadline().orElseThrow().identity());
        assertEquals(runs, storage.serializeRuns(java.util.List.of(restored)));
    }

    @Test
    void legacyRunWithoutDeadlineFieldLoadsAsNoDeadline() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionStorage storage = storage(context);
        JsonObject root = JsonParser.parseString(storage.serializeRuns(manager.runs())).getAsJsonObject();
        root.getAsJsonArray("runs").get(0).getAsJsonObject().remove("deadline");
        String legacyRuns = new GsonBuilder()
                .disableHtmlEscaping()
                .serializeNulls()
                .setPrettyPrinting()
                .create()
                .toJson(root) + System.lineSeparator();

        ProductionRunSnapshot restored = storage.deserializeRuns(legacyRuns).getFirst();

        assertTrue(restored.deadline().isEmpty());
        assertEquals(manager.runs().getFirst().id(), restored.id());
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

    private static BusinessCalendarSnapshot calendar(long dayIndex, int hour, int minute) {
        long minuteOfDay = hour * 60L + minute;
        long dayTimeOfDay = minuteOfDay * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                / BusinessCalendarSnapshot.BUSINESS_MINUTES_PER_DAY;
        long observedDayTime = dayIndex * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                + dayTimeOfDay - BusinessCalendarSnapshot.MINECRAFT_VISIBLE_MIDNIGHT_OFFSET;
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                dayIndex,
                BusinessDayOfWeek.fromDayIndex(dayIndex),
                new BusinessTimeOfDay(hour, minute),
                dayTimeOfDay,
                dayTimeOfDay,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/v1/minecraft:overworld/" + dayIndex,
                WorldTimeConfiguration.enabled(60).identity(),
                "minecraft:overworld",
                Math.max(0L, dayIndex),
                observedDayTime
        );
    }
}
