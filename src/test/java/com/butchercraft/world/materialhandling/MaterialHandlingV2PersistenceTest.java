package com.butchercraft.world.materialhandling;

import com.butchercraft.integration.materialhandling.StackAwareEndpointMigrationGate;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointOwnerResultV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackStateV2;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorage;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialHandlingV2PersistenceTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/material_v2_test", 1, "sha256:" + "c".repeat(64));
    private static final String ENDPOINT_CONFIG = "butchercraft:workstation_endpoint_configuration/v2/test";
    private static final String MATERIAL_CONFIG = "butchercraft:material_handling_configuration/v2/test";

    @TempDir
    Path tempDir;

    @Test
    void materialHandlingSchema2RoundTripsEmbeddedEndpointEvidenceAndCustody() {
        WorkstationEndpointObservationV2 observation = withdrawalObservation();
        WorkstationEndpointPreparationV2 preparation = WorkstationEndpointPreparationV2.create(
                1L, 2L, "butchercraft:test_invocation/source", observation, "butchercraft:test/idle");
        WorkstationEndpointOwnerResultV2 result = WorkstationEndpointOwnerResultV2.applied(preparation);
        MaterialTransferRecordV2 transfer = transfer(
                MaterialTransferLifecycle.IN_TRANSIT,
                Optional.of(observation.transferStack()),
                List.of(observation),
                List.of(preparation),
                List.of(result)
        );
        MaterialHandlingRuntimeV2 runtime = new MaterialHandlingRuntimeV2(
                MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION, 1L, 2L, WORLD, MATERIAL_CONFIG,
                Optional.empty(), List.of(transfer));
        MaterialHandlingStorageV2 storage = new MaterialHandlingStorageV2(tempDir.resolve("material_handling.json"));

        storage.save(runtime);
        MaterialHandlingRuntimeV2 loaded = ((MaterialHandlingStorageV2.StackAwareRuntime)
                storage.loadVersioned().orElseThrow()).runtime();

        assertEquals(runtime, loaded);
        assertEquals(10, loaded.transfers().getFirst().exactTransferStack().count());
        assertEquals(54, loaded.transfers().getFirst().endpointOwnerResults().getFirst().postStack().count());
    }

    @Test
    void materialHandlingEvidenceRejectsQuantityMismatch() {
        WorkstationEndpointObservationV2 observation = withdrawalObservation();

        assertThrows(IllegalArgumentException.class, () -> MaterialTransferRecordV2.create(
                1L, WORLD, source(), destination(), "minecraft:stone", 9,
                "butchercraft:test_assignment/manual", Optional.empty(), MaterialTransferLifecycle.IN_TRANSIT,
                observation.transferStack(), Optional.of(observation.transferStack()), List.of(observation), List.of(),
                List.of(), MATERIAL_CONFIG, 1L, 1L, Optional.empty()));
    }

    @Test
    void schemaAwareReaderPreservesLegacyMaterialHandlingWithoutReinterpretation() {
        MaterialHandlingRuntime legacy = MaterialHandlingRuntime.empty(
                WORLD,
                MaterialHandlingConfiguration.standard().configurationIdentity()
        );
        Path path = tempDir.resolve("legacy-material.json");
        new MaterialHandlingStorage(path).save(legacy);

        MaterialHandlingStorageV2.LoadedRuntime loaded = new MaterialHandlingStorageV2(path)
                .loadVersioned().orElseThrow();

        assertTrue(loaded instanceof MaterialHandlingStorageV2.LegacyRuntime);
        assertEquals(legacy, ((MaterialHandlingStorageV2.LegacyRuntime) loaded).runtime());
    }

    @Test
    void migrationGateAcceptsOnlyFullyResolvedLegacyAuthority() {
        WorkstationEndpointJournal endpoint = WorkstationEndpointJournal.empty(
                WORLD, WorkstationEndpointConfiguration.standard().endpointConfigurationIdentity());
        MaterialHandlingRuntime material = MaterialHandlingRuntime.empty(
                WORLD, MaterialHandlingConfiguration.standard().configurationIdentity());
        StackAwareEndpointMigrationGate gate = new StackAwareEndpointMigrationGate();

        StackAwareEndpointMigrationGate.Assessment eligible = gate.assess(input(endpoint, material));
        StackAwareEndpointMigrationGate.Assessment projectionBlocked = gate.assess(new StackAwareEndpointMigrationGate.Input(
                endpoint, material, false, 0, true, false, false));
        StackAwareEndpointMigrationGate.Assessment workforceBlocked = gate.assess(new StackAwareEndpointMigrationGate.Input(
                endpoint, material, true, 1, true, false, false));

        assertEquals(StackAwareEndpointMigrationGate.Decision.ELIGIBLE, eligible.decision());
        assertTrue(projectionBlocked.blockers().contains(
                StackAwareEndpointMigrationGate.Blocker.STALE_WORKSTATION_PROJECTION));
        assertTrue(workforceBlocked.blockers().contains(
                StackAwareEndpointMigrationGate.Blocker.ACTIVE_WORKFORCE_ASSIGNMENT));
    }

    @Test
    void activeRecoveryAndUnknownLegacyEndpointStateBlockMigration() {
        WorkstationEndpointJournal requested = legacyRequested();
        var effectId = requested.records().getFirst().effectId();
        WorkstationEndpointJournal recovery = requested.update(effectId, (record, revision) -> record.transition(
                WorkstationEndpointJournalState.RECOVERY_REQUIRED, revision, record.postInventoryRevision(),
                record.endpointEffectRevision(), Optional.empty(), Optional.of("test recovery")));
        WorkstationEndpointJournal prepared = requested.update(effectId, (record, revision) -> record.transition(
                WorkstationEndpointJournalState.PREPARED, revision, record.postInventoryRevision(),
                record.endpointEffectRevision(), Optional.empty(), Optional.empty()));
        WorkstationEndpointJournal unknown = prepared.update(effectId, (record, revision) -> record.transition(
                WorkstationEndpointJournalState.UNKNOWN_OUTCOME, revision, record.postInventoryRevision(),
                record.endpointEffectRevision(), Optional.empty(), Optional.of("test unknown")));
        MaterialHandlingRuntime material = MaterialHandlingRuntime.empty(
                WORLD, MaterialHandlingConfiguration.standard().configurationIdentity());
        StackAwareEndpointMigrationGate gate = new StackAwareEndpointMigrationGate();

        assertTrue(gate.assess(input(requested, material)).blockers().contains(
                StackAwareEndpointMigrationGate.Blocker.UNRESOLVED_ENDPOINT_EFFECT));
        assertTrue(gate.assess(input(recovery, material)).blockers().contains(
                StackAwareEndpointMigrationGate.Blocker.RECOVERY_REQUIRED_ENDPOINT));
        assertTrue(gate.assess(input(unknown, material)).blockers().contains(
                StackAwareEndpointMigrationGate.Blocker.UNKNOWN_OUTCOME_ENDPOINT));
    }

    @Test
    void activeLegacyTransferBlocksMigrationAndCannotBeReinterpreted() {
        MaterialHandlingRuntime active = MaterialHandlingRuntime.empty(
                WORLD, MaterialHandlingConfiguration.standard().configurationIdentity())
                .request(source(), destination(), "butchercraft:beef_trim", 1,
                        "butchercraft:test_assignment/manual", 10).runtime();
        StackAwareEndpointMigrationGate.Assessment assessment = new StackAwareEndpointMigrationGate().assess(input(
                WorkstationEndpointJournal.empty(
                        WORLD, WorkstationEndpointConfiguration.standard().endpointConfigurationIdentity()),
                active));

        assertEquals(StackAwareEndpointMigrationGate.Decision.BLOCKED, assessment.decision());
        assertTrue(assessment.blockers().contains(StackAwareEndpointMigrationGate.Blocker.ACTIVE_SCHEMA_1_TRANSFER));
    }

    @Test
    void eligibleMigrationPreservesLegacyDocumentsAndMonotonicSequences() {
        WorkstationEndpointJournal legacyEndpoint = WorkstationEndpointJournal.empty(
                WORLD, WorkstationEndpointConfiguration.standard().endpointConfigurationIdentity());
        MaterialHandlingRuntime legacyMaterial = MaterialHandlingRuntime.empty(
                WORLD, MaterialHandlingConfiguration.standard().configurationIdentity());
        Path endpointPath = tempDir.resolve("endpoint.json");
        Path materialPath = tempDir.resolve("material.json");
        WorkstationEndpointJournalStorage legacyEndpointStorage = new WorkstationEndpointJournalStorage(endpointPath);
        MaterialHandlingStorage legacyMaterialStorage = new MaterialHandlingStorage(materialPath);
        String endpointJson = legacyEndpointStorage.serialize(legacyEndpoint);
        String materialJson = legacyMaterialStorage.serialize(legacyMaterial);
        StackAwareEndpointMigrationGate gate = new StackAwareEndpointMigrationGate();
        StackAwareEndpointMigrationGate.Assessment assessment = gate.assess(input(legacyEndpoint, legacyMaterial));

        WorkstationEndpointJournalV2 endpointCandidate = gate.migrateEndpointCandidate(
                new WorkstationEndpointJournalV2Storage.LegacyJournal(legacyEndpoint, endpointJson),
                ENDPOINT_CONFIG,
                assessment
        );
        MaterialHandlingRuntimeV2 materialCandidate = gate.migrateMaterialHandlingCandidate(
                new MaterialHandlingStorageV2.LegacyRuntime(legacyMaterial, materialJson),
                MATERIAL_CONFIG,
                assessment
        );

        assertEquals(endpointJson, endpointCandidate.immutableLegacySchema1Journal().orElseThrow());
        assertEquals(materialJson, materialCandidate.immutableLegacySchema1Runtime().orElseThrow());
        assertEquals(legacyEndpoint.nextJournalSequence(), endpointCandidate.nextJournalSequence());
        assertEquals(legacyMaterial.nextTransferSequence(), materialCandidate.nextTransferSequence());
    }

    @Test
    void futureSchemaAndInterruptedPublicationFailVisibly() throws Exception {
        Path future = tempDir.resolve("future-material.json");
        Files.writeString(future, "{\"schema_version\":3}");
        assertThrows(IllegalArgumentException.class, () -> new MaterialHandlingStorageV2(future).loadVersioned());

        Path interrupted = tempDir.resolve("interrupted-material.json");
        Files.writeString(interrupted.resolveSibling("interrupted-material.json.tmp"), "partial");
        assertThrows(IllegalStateException.class, () -> new MaterialHandlingStorageV2(interrupted).loadVersioned());
    }

    @Test
    void schema1ReaderRejectsSchema2DowngradeWithoutMutatingFile() {
        MaterialHandlingRuntimeV2 runtime = MaterialHandlingRuntimeV2.empty(WORLD, MATERIAL_CONFIG);
        Path path = tempDir.resolve("schema2-material.json");
        MaterialHandlingStorageV2 storage = new MaterialHandlingStorageV2(path);
        storage.save(runtime);
        String before;
        try {
            before = Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }

        assertThrows(IllegalArgumentException.class, () -> new MaterialHandlingStorage(path).loadExisting());
        try {
            assertEquals(before, Files.readString(path));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static StackAwareEndpointMigrationGate.Input input(
            WorkstationEndpointJournal endpoint,
            MaterialHandlingRuntime material
    ) {
        return new StackAwareEndpointMigrationGate.Input(endpoint, material, true, 0, true, false, false);
    }

    private static MaterialTransferRecordV2 transfer(
            MaterialTransferLifecycle lifecycle,
            Optional<WorkstationEndpointStackStateV2> custody,
            List<WorkstationEndpointObservationV2> observations,
            List<WorkstationEndpointPreparationV2> preparations,
            List<WorkstationEndpointOwnerResultV2> results
    ) {
        WorkstationEndpointStackStateV2 payload = withdrawalObservation().transferStack();
        return MaterialTransferRecordV2.create(
                1L, WORLD, source(), destination(), "minecraft:stone", 10,
                "butchercraft:test_assignment/manual", Optional.empty(), lifecycle, payload, custody,
                observations, preparations, results, MATERIAL_CONFIG, 1L, 1L, Optional.empty()
        );
    }

    private static WorkstationEndpointObservationV2 withdrawalObservation() {
        return WorkstationEndpointObservationV2.create(
                source().instanceId(), source().endpointKey(), WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                0, 10, state(64), state(10), state(54), state(54), 0L, 0L, "butchercraft:test/idle",
                0L, 0L, 64, ENDPOINT_CONFIG
        );
    }

    private static WorkstationEndpointJournal legacyRequested() {
        return WorkstationEndpointJournal.empty(
                WORLD, WorkstationEndpointConfiguration.standard().endpointConfigurationIdentity())
                .request(source().instanceId(), "butchercraft:test_invocation/legacy",
                        WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL, 0, legacyStack(), 0L, 0L,
                        "butchercraft:test/idle", "butchercraft:test/idle", 0L, 10).journal();
    }

    private static WorkstationEndpointStackStateV2 state(int count) {
        WorkstationEndpointStackPayload exact = WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test", "minecraft:stone", count,
                ("lot:" + count).getBytes(StandardCharsets.UTF_8));
        WorkstationEndpointStackPayload normalized = WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test", "minecraft:stone", 1,
                "lot:1".getBytes(StandardCharsets.UTF_8));
        return WorkstationEndpointStackStateV2.create(exact, normalized);
    }

    private static WorkstationEndpointStackPayload legacyStack() {
        return WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test", "minecraft:stone", 1,
                "legacy".getBytes(StandardCharsets.UTF_8));
    }

    private static WorkstationEndpointReference source() {
        WorkstationEndpointKey key = new WorkstationEndpointKey(
                "butchercraft:cutting_table", "minecraft:overworld", 1, 64, 1);
        return new WorkstationEndpointReference(WorkstationInstanceId.create(
                WORLD, key, 1L, WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity()),
                key, 1L);
    }

    private static WorkstationEndpointReference destination() {
        WorkstationEndpointKey key = new WorkstationEndpointKey(
                "butchercraft:grinder", "minecraft:overworld", 2, 64, 1);
        return new WorkstationEndpointReference(WorkstationInstanceId.create(
                WORLD, key, 2L, WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity()),
                key, 2L);
    }
}
