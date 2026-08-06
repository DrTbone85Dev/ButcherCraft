package com.butchercraft.workstation.endpoint;

import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationEndpointPersistenceTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/test",
            1,
            "sha256:" + "1".repeat(64)
    );
    private static final WorkstationEndpointConfiguration CONFIG = WorkstationEndpointConfiguration.standard();

    @TempDir
    Path tempDir;

    @Test
    void instanceGenerationsAreMonotonicAndNeverReused() {
        WorkstationInstanceRegistry registry = WorkstationInstanceRegistry.empty(
                WORLD,
                CONFIG.instanceAllocationConfigurationIdentity()
        );
        WorkstationEndpointKey firstKey = key(1);
        WorkstationInstanceRegistry.AllocationCandidate first = registry.allocate(firstKey, 10);
        WorkstationInstanceRegistry activated = first.registry().update(
                first.record().instanceId(),
                record -> record.transition(
                        WorkstationInstanceLifecycle.ACTIVE,
                        first.registry().ownerRevision() + 1L,
                        Optional.empty(),
                        List.of()
                )
        );
        WorkstationInstanceRegistry retired = activated.update(
                first.record().instanceId(),
                record -> record.transition(
                        WorkstationInstanceLifecycle.RETIRED,
                        activated.ownerRevision() + 1L,
                        Optional.of("test replacement"),
                        List.of()
                )
        );

        WorkstationInstanceRegistry.AllocationCandidate replacement = retired.allocate(firstKey, 10);

        assertEquals(1L, first.record().generation());
        assertEquals(2L, replacement.record().generation());
        assertNotEquals(first.record().instanceId(), replacement.record().instanceId());
        assertEquals(3L, replacement.registry().nextInstanceGeneration());
    }

    @Test
    void instanceRegistryRoundTripsDeterministicallyThroughStrictPublication() {
        WorkstationInstanceRegistry.AllocationCandidate allocation = WorkstationInstanceRegistry.empty(
                WORLD,
                CONFIG.instanceAllocationConfigurationIdentity()
        ).allocate(key(2), 10);
        WorkstationInstanceStorage storage = new WorkstationInstanceStorage(
                tempDir.resolve(WorkstationEndpointSchema.INSTANCE_FILE_NAME)
        );

        storage.save(allocation.registry());
        WorkstationInstanceRegistry loaded = storage.loadExisting().orElseThrow();

        assertEquals(allocation.registry(), loaded);
        assertEquals(storage.serialize(allocation.registry()), storage.serialize(loaded));
    }

    @Test
    void endpointJournalFreezesSequenceFreshnessAndOwnerResultBeforeProjectionPublication() {
        WorkstationInstanceId instanceId = instance(3);
        WorkstationEndpointJournal.AppendCandidate request = WorkstationEndpointJournal.empty(
                WORLD,
                CONFIG.endpointConfigurationIdentity()
        ).request(
                instanceId,
                "butchercraft:test_invocation/source",
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                0,
                stack(),
                4L,
                7L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                2L,
                10
        );
        WorkstationEndpointEffectId effectId = request.record().effectId();
        WorkstationEndpointJournal prepared = request.journal().update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.PREPARED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
        WorkstationEndpointJournalRecord preparedRecord = prepared.find(effectId).orElseThrow();
        WorkstationEndpointOwnerResult result = WorkstationEndpointOwnerResult.create(
                preparedRecord,
                WorkstationEndpointResultCode.APPLIED,
                Optional.empty()
        );
        WorkstationEndpointJournal committed = prepared.update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.EFFECT_COMMITTED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.of(result),
                        Optional.empty()
                )
        );
        WorkstationEndpointJournalStorage storage = new WorkstationEndpointJournalStorage(
                tempDir.resolve(WorkstationEndpointSchema.JOURNAL_FILE_NAME)
        );

        storage.save(committed);
        WorkstationEndpointJournal loaded = storage.loadExisting().orElseThrow();
        WorkstationEndpointJournalRecord restored = loaded.find(effectId).orElseThrow();

        assertEquals(committed, loaded);
        assertEquals(1L, restored.journalSequence());
        assertEquals(2L, loaded.nextJournalSequence());
        assertEquals(preparedRecord.preFreshnessIdentity(), result.preFreshnessIdentity());
        assertEquals(preparedRecord.postFreshnessIdentity(), result.postFreshnessIdentity());
        assertEquals(CONFIG.endpointConfigurationIdentity(), result.endpointConfigurationIdentity());
        assertThrows(IllegalArgumentException.class, () -> prepared.update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.EFFECT_COMMITTED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.empty()
                )
        ));
    }

    @Test
    void endpointFreshnessChangesWithLockOperationStateAndOwnerResultPosition() {
        WorkstationInstanceId instanceId = instance(30);
        WorkstationEndpointEffectId effectId = WorkstationEndpointEffectId.create(
                instanceId,
                "butchercraft:test_invocation/freshness",
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
        );
        WorkstationEndpointFreshnessIdentity baseline = WorkstationEndpointFreshnessIdentity.create(
                instanceId,
                0,
                4L,
                2L,
                stack().contentDigest(),
                false,
                Optional.empty(),
                "butchercraft:test/idle",
                3L,
                CONFIG.endpointConfigurationIdentity()
        );

        assertNotEquals(baseline, WorkstationEndpointFreshnessIdentity.create(
                instanceId, 0, 4L, 2L, stack().contentDigest(), true, Optional.of(effectId),
                "butchercraft:test/idle", 3L, CONFIG.endpointConfigurationIdentity()
        ));
        assertNotEquals(baseline, WorkstationEndpointFreshnessIdentity.create(
                instanceId, 0, 4L, 2L, stack().contentDigest(), false, Optional.empty(),
                "butchercraft:test/ready", 3L, CONFIG.endpointConfigurationIdentity()
        ));
        assertNotEquals(baseline, WorkstationEndpointFreshnessIdentity.create(
                instanceId, 0, 4L, 2L, stack().contentDigest(), false, Optional.empty(),
                "butchercraft:test/idle", 4L, CONFIG.endpointConfigurationIdentity()
        ));
    }

    @Test
    void duplicateEffectIdentityRejectsDifferentCanonicalContent() {
        WorkstationInstanceId instanceId = instance(4);
        WorkstationEndpointJournal journal = WorkstationEndpointJournal.empty(
                WORLD,
                CONFIG.endpointConfigurationIdentity()
        ).request(
                instanceId,
                "butchercraft:test_invocation/source",
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                0,
                stack(),
                0L,
                0L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                0L,
                10
        ).journal();

        assertThrows(IllegalArgumentException.class, () -> journal.request(
                instanceId,
                "butchercraft:test_invocation/source",
                WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL,
                0,
                WorkstationEndpointStackPayload.create(
                        "butchercraft:item_stack_codec/v1/test",
                        "butchercraft:beef_trim_test",
                        1,
                        "different-stack".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ),
                0L,
                0L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                0L,
                10
        ));
    }

    @Test
    void sourceReturnUsesOneCanonicalJournalEffectAcrossPersistenceAndRetry() {
        WorkstationInstanceId instanceId = instance(5);
        String invocation = "butchercraft:test_invocation/source_return";
        WorkstationEndpointJournal.AppendCandidate requested = WorkstationEndpointJournal.empty(
                WORLD,
                CONFIG.endpointConfigurationIdentity()
        ).request(
                instanceId,
                invocation,
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                0,
                stack(),
                8L,
                3L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                4L,
                10
        );
        WorkstationEndpointJournal.AppendCandidate duplicateRequest = requested.journal().request(
                instanceId,
                invocation,
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                0,
                stack(),
                8L,
                3L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                4L,
                10
        );
        assertTrue(duplicateRequest.duplicate());
        assertEquals(requested.journal(), duplicateRequest.journal());

        WorkstationEndpointEffectId effectId = requested.record().effectId();
        WorkstationEndpointJournal prepared = requested.journal().update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.PREPARED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
        WorkstationEndpointJournalRecord preparedRecord = prepared.find(effectId).orElseThrow();
        WorkstationEndpointOwnerResult result = WorkstationEndpointOwnerResult.create(
                preparedRecord,
                WorkstationEndpointResultCode.APPLIED,
                Optional.empty()
        );
        WorkstationEndpointJournal committed = prepared.update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.EFFECT_COMMITTED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.of(result),
                        Optional.empty()
                )
        );
        WorkstationEndpointJournalStorage storage = new WorkstationEndpointJournalStorage(
                tempDir.resolve("source-return-journal.json")
        );

        storage.save(committed);
        WorkstationEndpointJournal loaded = storage.loadExisting().orElseThrow();

        assertEquals(1, loaded.records().size());
        assertEquals(WorkstationEndpointEffectKind.SOURCE_RETURN,
                loaded.find(effectId).orElseThrow().effectKind());
        assertEquals(stack(), loaded.find(effectId).orElseThrow().exactStack());
        assertEquals(result, loaded.find(effectId).orElseThrow().ownerResult().orElseThrow());
        assertTrue(loaded.request(
                instanceId,
                invocation,
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                0,
                stack(),
                8L,
                3L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                4L,
                10
        ).duplicate());
    }

    @Test
    void rejectedPreparationPersistsAsProofThatNoEndpointEffectCommitted() {
        WorkstationEndpointJournal.AppendCandidate requested = WorkstationEndpointJournal.empty(
                WORLD,
                CONFIG.endpointConfigurationIdentity()
        ).request(
                instance(6),
                "butchercraft:test_invocation/rejected_return",
                WorkstationEndpointEffectKind.SOURCE_RETURN,
                0,
                stack(),
                2L,
                1L,
                "butchercraft:test/idle",
                "butchercraft:test/idle",
                0L,
                10
        );
        WorkstationEndpointEffectId effectId = requested.record().effectId();
        WorkstationEndpointJournal prepared = requested.journal().update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.PREPARED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
        WorkstationEndpointJournal rejected = prepared.update(
                effectId,
                (record, revision) -> record.transition(
                        WorkstationEndpointJournalState.REJECTED,
                        revision,
                        record.postInventoryRevision(),
                        record.endpointEffectRevision(),
                        Optional.empty(),
                        Optional.of("explicitly cancelled before commitment")
                )
        );
        WorkstationEndpointJournalStorage storage = new WorkstationEndpointJournalStorage(
                tempDir.resolve("rejected-return-journal.json")
        );

        storage.save(rejected);
        WorkstationEndpointJournalRecord restored = storage.loadExisting().orElseThrow()
                .find(effectId)
                .orElseThrow();

        assertEquals(WorkstationEndpointJournalState.REJECTED, restored.state());
        assertTrue(restored.ownerResult().isEmpty());
        assertEquals(stack(), restored.exactStack());
    }

    @Test
    void unsupportedSchemasFailInsteadOfLoadingEmptyState() {
        WorkstationInstanceStorage storage = new WorkstationInstanceStorage(tempDir.resolve("instances.json"));
        String invalid = "{\"schema_version\":2,\"owner_revision\":0,\"world_identity\":{},"
                + "\"next_instance_generation\":1,\"allocation_configuration_identity\":\"butchercraft:test\","
                + "\"instances\":[]}";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> storage.deserialize(invalid));

        assertTrue(failure.getMessage().contains("Unsupported Workstation instance schema"));
    }

    @Test
    void orphanedTemporaryPublicationNeverBecomesAuthoritative() throws Exception {
        Path authoritative = tempDir.resolve("instances.json");
        Files.writeString(authoritative.resolveSibling("instances.json.tmp"), "interrupted");
        WorkstationInstanceStorage storage = new WorkstationInstanceStorage(authoritative);

        IllegalStateException failure = assertThrows(IllegalStateException.class, storage::loadExisting);

        assertTrue(failure.getMessage().contains("requires recovery"));
    }

    private static WorkstationInstanceId instance(int x) {
        return WorkstationInstanceId.create(
                WORLD,
                key(x),
                x,
                CONFIG.instanceAllocationConfigurationIdentity()
        );
    }

    private static WorkstationEndpointKey key(int x) {
        return new WorkstationEndpointKey("butchercraft:cutting_table", "minecraft:overworld", x, 64, 0);
    }

    private static WorkstationEndpointStackPayload stack() {
        return WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test",
                "butchercraft:beef_trim_test",
                1,
                "exact-stack".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
