package com.butchercraft.integration.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationStateStorage;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveOwnerCoherenceAnalyzerTest {
    private static final SimulationConfiguration CONFIGURATION = SimulationConfiguration.standard();
    private static final WorldIdentityRootReference WORLD = new WorldIdentityRootReference(
            "butchercraft:world/test", 1,
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @TempDir
    Path temporary;

    @Test
    void emptyNewWorldIsCoherentWithoutPublishingState() {
        Path root = temporary.resolve("butchercraft");

        LiveOwnerCoherenceReport report = analyze(root);

        assertEquals(LiveOwnerCoherenceStatus.COHERENT_EMPTY, report.status());
        assertTrue(report.coherent());
        assertFalse(Files.exists(root));
        assertEquals(17, report.ownersInspected().size());
    }

    @Test
    void matchingClockAndSchedulerAreCoherent() throws Exception {
        Path root = temporary.resolve("butchercraft");
        writeClockAndScheduler(root, 10_000L, 10_000L);

        LiveOwnerCoherenceReport report = analyze(root);

        assertEquals(LiveOwnerCoherenceStatus.COHERENT, report.status());
        assertEquals(10_000L, report.clockTick().orElseThrow());
        assertEquals(10_000L, report.schedulerTick().orElseThrow());
    }

    @Test
    void splitClockAndSchedulerAreRejectedBeforeMutation() throws Exception {
        Path root = temporary.resolve("butchercraft");
        writeClockAndScheduler(root, 39_872L, 39_084L);

        LiveOwnerCoherenceReport report = analyze(root);

        assertFalse(report.coherent());
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT
                        && issue.ownerId().orElseThrow()
                        .equals(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER)));
    }

    @Test
    void identicalLegacyFixedTempIsIgnoredButDifferingTempBlocks() throws Exception {
        Path root = temporary.resolve("butchercraft");
        writeClockAndScheduler(root, 20L, 20L);
        Path clock = root.resolve("simulation_state.json");
        Path legacyTemp = root.resolve("simulation_state.json.tmp");
        Files.copy(clock, legacyTemp);
        assertTrue(analyze(root).coherent());

        Files.writeString(legacyTemp, "different", StandardCharsets.UTF_8);
        LiveOwnerCoherenceReport report = analyze(root);
        assertFalse(report.coherent());
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.LIVE_SPLIT_SNAPSHOT));
    }

    @Test
    void unsupportedOwnerSchemaAndObjectWorldIdentityMismatchAreTyped() throws Exception {
        Path schemaRoot = temporary.resolve("schema");
        writeClockAndScheduler(schemaRoot, 5L, 5L);
        String invalidSchema = Files.readString(schemaRoot.resolve("simulation_state.json"))
                .replace("\"schema_version\": 1", "\"schema_version\": 99");
        Files.writeString(schemaRoot.resolve("simulation_state.json"), invalidSchema);
        LiveOwnerCoherenceReport schema = analyze(schemaRoot);
        assertTrue(schema.issues().stream().anyMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.OWNER_SCHEMA_UNSUPPORTED));

        Path worldRoot = temporary.resolve("world");
        Files.createDirectories(worldRoot);
        Files.writeString(worldRoot.resolve("unknown_owner.json"), """
                {"schema_version":1,"world_identity":{"identity":"butchercraft:world/other",
                "root_digest":"sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}}
                """, StandardCharsets.UTF_8);
        LiveOwnerCoherenceReport world = analyze(worldRoot);
        assertTrue(world.issues().stream().anyMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.WORLD_IDENTITY_MISMATCH));
    }

    @Test
    void ownerSpecificPrimitiveWorldIdentityDoesNotMasqueradeAsPlatformRoot() throws Exception {
        Path root = temporary.resolve("butchercraft");
        Files.createDirectories(root);
        Files.writeString(root.resolve("execution_operations.json"), """
                {"schema_version":1,"operations":[{"authorization_evidence":{
                "world_identity":"butchercraft:world/test"}}]}
                """, StandardCharsets.UTF_8);

        LiveOwnerCoherenceReport report = analyze(root);

        assertTrue(report.issues().stream().noneMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.WORLD_IDENTITY_MISMATCH));

        Files.writeString(root.resolve("execution_operations.json"), """
                {"schema_version":1,"world_identity_root":"butchercraft:world_identity/other"}
                """, StandardCharsets.UTF_8);
        LiveOwnerCoherenceReport mismatch = analyze(root);
        assertTrue(mismatch.issues().stream().anyMatch(issue ->
                issue.code() == StartupRecoveryFailureCode.WORLD_IDENTITY_MISMATCH));
    }

    @Test
    void notAnalyzedStatusNeverAuthorizesStartup() {
        LiveOwnerCoherenceReport report = new LiveOwnerCoherenceReport(
                LiveOwnerCoherenceStatus.NOT_ANALYZED,
                java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty(),
                List.of(),
                List.of(),
                new com.butchercraft.world.checkpoint.RecoveryMutationGate(1, false, List.of(), List.of()),
                0L
        );

        assertFalse(report.coherent());
    }

    private LiveOwnerCoherenceReport analyze(Path root) {
        return new LiveOwnerCoherenceAnalyzer().analyze(null, root, WORLD);
    }

    private void writeClockAndScheduler(Path root, long clockTick, long schedulerTick) throws Exception {
        Files.createDirectories(root);
        SimulationClock clock = new SimulationClock(CONFIGURATION);
        clock.advance(clockTick);
        Files.writeString(
                root.resolve("simulation_state.json"),
                new SimulationStateStorage(root.resolve("unused_clock.json"), CONFIGURATION)
                        .serialize(clock.state()),
                StandardCharsets.UTF_8
        );
        SimulationWorkHandlerRegistry handlers = new SimulationWorkHandlerRegistry(List.of());
        SimulationSchedulerManager scheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), handlers, schedulerTick);
        Files.writeString(
                root.resolve("simulation_scheduler.json"),
                new SimulationSchedulerStorage(root.resolve("unused_scheduler.json"), handlers, schedulerTick)
                        .serialize(scheduler),
                StandardCharsets.UTF_8
        );
    }
}
