package com.butchercraft.world.planning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void allSixPlanningFilesRoundTripDeterministically() throws Exception {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCycle(20L);
        manager.executeCycle(21L);
        PlanningStorage storage = storage(context);

        storage.save(manager);
        List<String> before = fileContents();
        PlanningManager loaded = storage.load();
        storage.save(loaded);

        assertEquals(manager.cycles(), loaded.cycles());
        assertEquals(before, fileContents());
        for (String name : fileNames()) {
            assertTrue(Files.exists(temporaryDirectory.resolve(name)), name);
        }
    }

    @Test
    void partialPersistenceFailsBeforePublication() throws Exception {
        Files.writeString(temporaryDirectory.resolve(PlanningStorage.OBSERVATIONS_FILE),
                "{\"schema_version\":1,\"records\":[]}");

        assertThrows(IllegalArgumentException.class,
                () -> storage(PlanningTestFixtures.context()).load());
    }

    @Test
    void malformedAndUnsupportedSchemasFailVisibly() throws Exception {
        PlanningTestFixtures.Context malformedContext = savedContext();
        Files.writeString(temporaryDirectory.resolve(PlanningStorage.OBSERVATIONS_FILE), "{");
        assertThrows(IllegalArgumentException.class, () -> storage(malformedContext).load());

        PlanningTestFixtures.Context unsupportedContext = savedContext();
        Path runtime = temporaryDirectory.resolve(PlanningStorage.RUNTIME_FILE);
        Files.writeString(runtime, Files.readString(runtime)
                .replaceFirst("\"schema_version\": 1", "\"schema_version\": 2"));
        assertThrows(IllegalArgumentException.class, () -> storage(unsupportedContext).load());
    }

    @Test
    void interruptedCycleFailsRatherThanBeingSilentlyReplayed() throws Exception {
        PlanningTestFixtures.Context context = savedContext();
        Path runtime = temporaryDirectory.resolve(PlanningStorage.RUNTIME_FILE);
        String changed = Files.readString(runtime)
                .replaceFirst("\"status\": \"COMPLETED\"", "\"status\": \"OBSERVING\"");
        Files.writeString(runtime, changed);

        assertThrows(IllegalArgumentException.class, () -> storage(context).load());
    }

    @Test
    void missingExternalAuthorityReferenceRejectsLoadedSnapshot() {
        PlanningTestFixtures.Context saved = savedContext();
        PlanningTestFixtures.Context withoutOrder = PlanningTestFixtures.context();

        assertThrows(IllegalArgumentException.class, () -> storage(withoutOrder).load());
    }

    private PlanningTestFixtures.Context savedContext() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCycle(20L);
        storage(context).save(manager);
        return context;
    }

    private PlanningStorage storage(PlanningTestFixtures.Context context) {
        return new PlanningStorage(
                temporaryDirectory, context.dependencies(), PlanningSelectionPolicy.standard(),
                PlanningExecutionBudget.standard()
        );
    }

    private List<String> fileContents() {
        return fileNames().stream().map(name -> {
            try {
                return Files.readString(temporaryDirectory.resolve(name));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
    }

    private static List<String> fileNames() {
        return List.of(
                PlanningStorage.OBSERVATIONS_FILE,
                PlanningStorage.NEEDS_FILE,
                PlanningStorage.OPPORTUNITIES_FILE,
                PlanningStorage.CANDIDATES_FILE,
                PlanningStorage.APPROVED_PLANS_FILE,
                PlanningStorage.RUNTIME_FILE
        );
    }
}
