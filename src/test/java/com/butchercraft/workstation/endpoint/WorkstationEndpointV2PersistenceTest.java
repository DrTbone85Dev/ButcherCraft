package com.butchercraft.workstation.endpoint;

import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationEndpointV2PersistenceTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/stack_aware_test", 1, "sha256:" + "a".repeat(64));
    private static final String CONFIG = "butchercraft:workstation_endpoint_configuration/v2/test";

    @TempDir
    Path tempDir;

    @Test
    void stackContentIdentityIsQuantitySensitiveWhileCompatibilityIsNot() {
        WorkstationEndpointStackStateV2 one = state(1, "lot-a");
        WorkstationEndpointStackStateV2 sixtyThree = state(63, "lot-a");

        assertNotEquals(one.contentIdentity(), sixtyThree.contentIdentity());
        assertEquals(one.compatibilityIdentity(), sixtyThree.compatibilityIdentity());
        assertFalse(one.equals(sixtyThree));
    }

    @Test
    void withdrawalObservationProvesExactPreTransferRemainderAndPost() {
        WorkstationEndpointObservationV2 observation = withdrawal(64, 10, 0L, 0L, 0L);

        assertEquals(64, observation.preStack().count());
        assertEquals(10, observation.transferStack().count());
        assertEquals(54, observation.remainderStack().count());
        assertEquals(observation.remainderStack(), observation.postStack());
        assertNotEquals(observation.preStack().contentIdentity(), observation.postStack().contentIdentity());
    }

    @Test
    void invalidArithmeticAndZeroQuantityAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkstationEndpointObservationV2.create(
                instance(), key(), WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL, 0, 0,
                state(4, "lot-a"), state(1, "lot-a"), state(3, "lot-a"), state(3, "lot-a"),
                0L, 0L, "butchercraft:test/idle", 0L, 0L, 64, CONFIG));
        assertThrows(IllegalArgumentException.class, () -> WorkstationEndpointObservationV2.create(
                instance(), key(), WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL, 0, 2,
                state(4, "lot-a"), state(2, "lot-a"), state(1, "lot-a"), state(1, "lot-a"),
                0L, 0L, "butchercraft:test/idle", 0L, 0L, 64, CONFIG));
    }

    @Test
    void journalRoundTripFreezesSchema2OwnerResultBeforeProjection() {
        WorkstationEndpointJournalV2.AppendCandidate prepared = WorkstationEndpointJournalV2.empty(WORLD, CONFIG)
                .prepare("butchercraft:test_invocation/withdraw", withdrawal(64, 1, 4L, 7L, 0L),
                        "butchercraft:test/idle");
        WorkstationEndpointJournalV2 committed = prepared.journal().update(
                prepared.record().effectId(),
                WorkstationEndpointJournalRecordV2::commit
        );
        WorkstationEndpointJournalV2Storage storage = new WorkstationEndpointJournalV2Storage(
                tempDir.resolve("workstation_endpoint_journal.json"));

        storage.save(committed);
        WorkstationEndpointJournalV2 loaded = ((WorkstationEndpointJournalV2Storage.StackAwareJournal)
                storage.loadVersioned().orElseThrow()).journal();
        WorkstationEndpointJournalRecordV2 record = loaded.records().getFirst();

        assertEquals(committed, loaded);
        assertEquals(WorkstationEndpointJournalState.EFFECT_COMMITTED, record.state());
        assertEquals(64, record.ownerResult().orElseThrow().preStack().count());
        assertEquals(1, record.ownerResult().orElseThrow().transferStack().count());
        assertEquals(63, record.ownerResult().orElseThrow().postStack().count());
        assertEquals(5L, record.ownerResult().orElseThrow().resultingInventoryRevision());
    }

    @Test
    void destinationAndReturnEvidenceUseExactAllOrNothingMerge() {
        WorkstationEndpointObservationV2 deposit = merge(
                WorkstationEndpointEffectKind.DESTINATION_DEPOSIT, 60, 4, 64);
        WorkstationEndpointObservationV2 returned = merge(
                WorkstationEndpointEffectKind.SOURCE_RETURN, 63, 1, 64);

        assertEquals(64, deposit.postStack().count());
        assertTrue(deposit.remainderStack().isEmpty());
        assertEquals(64, returned.postStack().count());
        assertTrue(returned.remainderStack().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> merge(
                WorkstationEndpointEffectKind.DESTINATION_DEPOSIT, 63, 2, 64));
    }

    @Test
    void duplicateEffectIdentityObservesSamePreparationAndRejectsDifferentContent() {
        WorkstationEndpointJournalV2 journal = WorkstationEndpointJournalV2.empty(WORLD, CONFIG);
        WorkstationEndpointJournalV2.AppendCandidate first = journal.prepare(
                "butchercraft:test_invocation/duplicate", withdrawal(8, 2, 0L, 0L, 0L),
                "butchercraft:test/idle");
        WorkstationEndpointJournalV2.AppendCandidate duplicate = first.journal().prepare(
                "butchercraft:test_invocation/duplicate", withdrawal(8, 2, 0L, 0L, 0L),
                "butchercraft:test/idle");

        assertTrue(duplicate.duplicateObserved());
        assertEquals(first.record(), duplicate.record());
        assertThrows(IllegalArgumentException.class, () -> first.journal().prepare(
                "butchercraft:test_invocation/duplicate", withdrawal(8, 3, 0L, 0L, 0L),
                "butchercraft:test/idle"));
    }

    @Test
    void freshnessBindsCountCapacityConfigurationAndJournalRevision() {
        WorkstationEndpointObservationV2 baseline = withdrawal(64, 1, 2L, 3L, 4L);
        WorkstationEndpointObservationV2 differentCount = withdrawal(63, 1, 2L, 3L, 4L);
        WorkstationEndpointObservationV2 differentJournal = withdrawal(64, 1, 2L, 3L, 5L);

        assertNotEquals(baseline.freshnessIdentity(), differentCount.freshnessIdentity());
        assertNotEquals(baseline.freshnessIdentity(), differentJournal.freshnessIdentity());
    }

    @Test
    void schemaAwareReaderPreservesLegacyJournalWithoutReinterpretation() {
        WorkstationEndpointJournal legacy = WorkstationEndpointJournal.empty(
                WORLD,
                WorkstationEndpointConfiguration.standard().endpointConfigurationIdentity()
        );
        Path path = tempDir.resolve("legacy.json");
        new WorkstationEndpointJournalStorage(path).save(legacy);

        WorkstationEndpointJournalV2Storage.LoadedJournal loaded =
                new WorkstationEndpointJournalV2Storage(path).loadVersioned().orElseThrow();

        assertEquals(WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION, loaded.schemaVersion());
        assertTrue(loaded instanceof WorkstationEndpointJournalV2Storage.LegacyJournal);
        assertEquals(legacy, ((WorkstationEndpointJournalV2Storage.LegacyJournal) loaded).journal());
    }

    @Test
    void unsupportedFutureSchemaAndInterruptedPublicationFailVisibly() throws Exception {
        Path future = tempDir.resolve("future.json");
        Files.writeString(future, "{\"schema_version\":3}");
        assertThrows(IllegalArgumentException.class,
                () -> new WorkstationEndpointJournalV2Storage(future).loadVersioned());

        Path interrupted = tempDir.resolve("interrupted.json");
        Files.writeString(interrupted.resolveSibling("interrupted.json.tmp"), "partial");
        assertThrows(IllegalStateException.class,
                () -> new WorkstationEndpointJournalV2Storage(interrupted).loadVersioned());
    }

    @Test
    void malformedMissingStackEvidenceFailsWithoutFallback() {
        WorkstationEndpointJournalV2Storage storage = new WorkstationEndpointJournalV2Storage(
                tempDir.resolve("malformed.json"));
        WorkstationEndpointJournalV2.AppendCandidate prepared = WorkstationEndpointJournalV2.empty(WORLD, CONFIG)
                .prepare("butchercraft:test_invocation/malformed", withdrawal(4, 1, 0L, 0L, 0L),
                        "butchercraft:test/idle");
        String json = storage.serialize(prepared.journal()).replaceFirst("\"transfer_stack\"", "\"missing\"");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(json));
    }

    private static WorkstationEndpointObservationV2 withdrawal(
            int pre,
            int quantity,
            long inventoryRevision,
            long effectRevision,
            long journalRevision
    ) {
        int remainder = pre - quantity;
        return WorkstationEndpointObservationV2.create(
                instance(), key(), WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL, 0, quantity,
                state(pre, "lot-a"), state(quantity, "lot-a"), stateOrEmpty(remainder, "lot-a"),
                stateOrEmpty(remainder, "lot-a"), inventoryRevision, effectRevision, "butchercraft:test/idle",
                0L, journalRevision, 64, CONFIG
        );
    }

    private static WorkstationEndpointObservationV2 merge(
            WorkstationEndpointEffectKind kind,
            int pre,
            int quantity,
            int capacity
    ) {
        if (pre + quantity > capacity) throw new IllegalArgumentException("Destination capacity is insufficient");
        return WorkstationEndpointObservationV2.create(
                instance(), key(), kind, 0, quantity, stateOrEmpty(pre, "lot-a"), state(quantity, "lot-a"),
                WorkstationEndpointStackStateV2.empty(), state(pre + quantity, "lot-a"), 0L, 0L,
                "butchercraft:test/idle", 0L, 0L, capacity, CONFIG
        );
    }

    private static WorkstationEndpointStackStateV2 stateOrEmpty(int count, String components) {
        return count == 0 ? WorkstationEndpointStackStateV2.empty() : state(count, components);
    }

    private static WorkstationEndpointStackStateV2 state(int count, String components) {
        WorkstationEndpointStackPayload exact = WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test", "minecraft:stone", count,
                (components + ":" + count).getBytes(StandardCharsets.UTF_8));
        WorkstationEndpointStackPayload normalized = WorkstationEndpointStackPayload.create(
                "butchercraft:item_stack_codec/v1/test", "minecraft:stone", 1,
                (components + ":1").getBytes(StandardCharsets.UTF_8));
        return WorkstationEndpointStackStateV2.create(exact, normalized);
    }

    private static WorkstationInstanceId instance() {
        return WorkstationInstanceId.create(
                WORLD, key(), 1L, WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity());
    }

    private static WorkstationEndpointKey key() {
        return new WorkstationEndpointKey("butchercraft:test_stack_endpoint", "minecraft:overworld", 1, 64, 1);
    }
}
