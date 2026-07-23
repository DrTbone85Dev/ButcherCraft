package com.butchercraft.world.simulation.scheduler;

import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileLoadsBuiltInEmptyStateAtAuthoritativeBaselineAndSavesAtomically() {
        SimulationSchedulerStorage storage = storage("simulation_scheduler.json", handlers(), 12);

        SimulationSchedulerManager manager = storage.load();

        assertEquals(12, manager.lastFinalizedSimulationTick());
        assertEquals(BuiltInSimulationStages.definitions(), manager.stageRegistry().definitions());
        assertEquals(0, manager.registry().size());
        storage.save(manager);
        assertTrue(Files.exists(storage.filePath()));
        assertFalse(Files.exists(storage.filePath().resolveSibling("simulation_scheduler.json.tmp")));
    }

    @Test
    void completeSchemaRoundTripsWithDeterministicJson() {
        SimulationSchedulerStorage storage = storage("round-trip.json", handlers(), 0);
        SimulationSchedulerManager manager = storage.load();
        SimulationWorkRequest request = new SimulationWorkRequest(
                SimulationWorkId.of("test:persisted"), SchedulerTestFixtures.TYPE,
                BuiltInSimulationStages.EXECUTION, 1, WorkPriority.HIGH,
                new WorkOrigin(
                        "test:scheduler", java.util.Optional.of("test:source"),
                        java.util.Optional.of("test:one"), 0, "test:unit_test",
                        java.util.Optional.of("test:correlation"), java.util.Optional.empty()
                ),
                new WorkPayload(List.of(
                        WorkPayloadEntry.longValue("test:quantity", 42),
                        WorkPayloadEntry.identifier("test:actor", "test:actor_one")
                )),
                RetryPolicy.fixedInterval(4), 3, java.util.OptionalLong.of(20),
                List.of(new WorkReference("test:actor", "test:actor_one"))
        );
        manager.submit(request, 0);
        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        String json = storage.serialize(manager);
        SimulationSchedulerManager loaded = storage.deserialize(json);

        assertEquals(json, storage.serialize(loaded));
        assertEquals(manager.registry().definitions(), loaded.registry().definitions());
        assertEquals(manager.runtimeRecords().stream().map(SimulationWorkRuntime::status).toList(),
                loaded.runtimeRecords().stream().map(SimulationWorkRuntime::status).toList());
        assertTrue(json.contains("\"authoritative_submission_sequence\": 0"));
        assertTrue(json.contains("\"last_finalized_simulation_tick\": 1"));
        assertFalse(json.contains("ItemStack"));
    }

    @Test
    void malformedUnsupportedDuplicateAndIncompleteDocumentsFailVisibly() {
        SimulationSchedulerStorage storage = storage("invalid.json", handlers(), 0);
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{broken"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                "{\"schema_version\":2,\"last_finalized_simulation_tick\":0,"
                        + "\"next_submission_sequence\":0,\"stages\":[],\"work\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                "{\"schema_version\":1,\"last_finalized_simulation_tick\":0,"
                        + "\"next_submission_sequence\":0,\"stages\":[],\"work\":[]}"));

        SimulationSchedulerManager manager = storage.load();
        manager.submit(SchedulerTestFixtures.request(
                "test:duplicate_persisted", BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        JsonObject root = JsonParser.parseString(storage.serialize(manager)).getAsJsonObject();
        JsonArray work = root.getAsJsonArray("work");
        work.add(work.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(toJson(root)));

        JsonObject missingRuntime = JsonParser.parseString(storage.serialize(manager)).getAsJsonObject();
        missingRuntime.getAsJsonArray("work").get(0).getAsJsonObject().add("runtime", null);
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(toJson(missingRuntime)));

        JsonObject missingSequence = JsonParser.parseString(storage.serialize(manager)).getAsJsonObject();
        missingSequence.remove("next_submission_sequence");
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(toJson(missingSequence)));
    }

    @Test
    void unknownStageTypeAndInterruptedRunningStateRejectEntireLoad() {
        SimulationSchedulerStorage storage = storage("unknown.json", handlers(), 0);
        SimulationSchedulerManager manager = storage.load();
        manager.submit(SchedulerTestFixtures.request(
                "test:unknown_cases", BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        String json = storage.serialize(manager);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                json.replace("butchercraft:execution", "test:unknown_stage")));
        SimulationSchedulerStorage noHandlers = storage("no-handlers.json", SimulationWorkHandlerRegistry.empty(), 0);
        assertThrows(IllegalArgumentException.class, () -> noHandlers.deserialize(json));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                json.replace("\"status\": \"scheduled\"", "\"status\": \"running\"")));
    }

    @Test
    void persistenceRefusesInFlightRunningWork() {
        SimulationSchedulerStorage storage = storage("running.json", handlers(), 0);
        SimulationSchedulerManager manager = storage.load();
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:running_save", BuiltInSimulationStages.EXECUTION, 0, 1
        );
        manager.submit(request, 0);
        manager.promoteDue(1, 1);
        manager.start(request.id(), 1);

        assertTrue(manager.hasRunningWork());
        assertThrows(IllegalStateException.class, () -> storage.serialize(manager));
        assertThrows(IllegalStateException.class, () -> storage.save(manager));
    }

    private SimulationSchedulerStorage storage(
            String name, SimulationWorkHandlerRegistry handlers, long initialTick
    ) {
        return new SimulationSchedulerStorage(
                temporaryDirectory.resolve("butchercraft").resolve(name), handlers, initialTick
        );
    }

    private static SimulationWorkHandlerRegistry handlers() {
        return new SimulationWorkHandlerRegistry(List.of(SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 2))));
    }

    private static String toJson(JsonObject object) {
        return new GsonBuilder().serializeNulls().create().toJson(object);
    }
}
