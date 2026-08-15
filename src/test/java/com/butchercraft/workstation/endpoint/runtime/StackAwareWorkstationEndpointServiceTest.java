package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointPreparationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackAwareWorkstationEndpointServiceTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/endpoint_service_test", 1, "sha256:" + "b".repeat(64));
    private static final String CONFIG = WorkstationSlotCapacityPolicy.uniform(1, 64).configurationIdentity();

    @TempDir
    Path tempDir;

    @Test
    void committedWithdrawalSplitsOnceAndDuplicateReplayObservesResult() {
        Harness harness = harness(new ItemStack(Items.STONE, 64));
        WorkstationEndpointObservationV2 observation = observeWithdrawal(harness, 10);
        WorkstationEndpointPreparationV2 preparation = prepare(
                harness, "butchercraft:test_invocation/withdraw", observation);

        StackAwareEndpointEffectResult first = harness.service.commit(
                RegistryAccess.EMPTY, harness.endpoint, preparation);
        StackAwareEndpointEffectResult duplicate = harness.service.commit(
                RegistryAccess.EMPTY, harness.endpoint, preparation);

        assertEquals(WorkstationEndpointResultCode.APPLIED, first.code());
        assertEquals(WorkstationEndpointResultCode.DUPLICATE_OBSERVED, duplicate.code());
        assertEquals(54, harness.endpoint.stack.getCount());
        assertEquals(1L, harness.endpoint.inventoryRevision);
        assertEquals(1L, harness.endpoint.effectRevision);
        assertEquals(WorkstationEndpointJournalState.RECONCILED,
                harness.service.journalSnapshot().records().getFirst().state());
    }

    @Test
    void competingObservationsCannotBothCommitAgainstOneRevision() {
        Harness harness = harness(new ItemStack(Items.STONE, 64));
        WorkstationEndpointObservationV2 first = observeWithdrawal(harness, 10);
        WorkstationEndpointObservationV2 second = observeWithdrawal(harness, 10);
        WorkstationEndpointPreparationV2 accepted = prepare(
                harness, "butchercraft:test_invocation/first", first);

        assertEquals(WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT, harness.service.prepare(
                harness.endpoint, "butchercraft:test_invocation/second", second).code());
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                harness.service.commit(RegistryAccess.EMPTY, harness.endpoint, accepted).code());
        assertEquals(WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT, harness.service.prepare(
                harness.endpoint, "butchercraft:test_invocation/second", second).code());
        assertEquals(54, harness.endpoint.stack.getCount());
        assertEquals(1, harness.service.journalSnapshot().records().size());
    }

    @Test
    void competingDepositsCannotBothCommitAgainstOneRevision() {
        Harness harness = harness(new ItemStack(Items.STONE, 10));
        WorkstationEndpointObservationV2 first = harness.service.observeDeposit(
                RegistryAccess.EMPTY, harness.endpoint, 0, new ItemStack(Items.STONE, 2))
                .observation().orElseThrow();
        WorkstationEndpointObservationV2 second = harness.service.observeDeposit(
                RegistryAccess.EMPTY, harness.endpoint, 0, new ItemStack(Items.STONE, 3))
                .observation().orElseThrow();
        WorkstationEndpointPreparationV2 accepted = prepare(
                harness, "butchercraft:test_invocation/deposit_first", first);

        assertEquals(WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT, harness.service.prepare(
                harness.endpoint, "butchercraft:test_invocation/deposit_second", second).code());
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                harness.service.commit(RegistryAccess.EMPTY, harness.endpoint, accepted).code());
        assertEquals(12, harness.endpoint.stack.getCount());
        assertEquals(1, harness.service.journalSnapshot().records().size());
    }

    @Test
    void withdrawalAndReturnCannotRaceOnOneSlot() {
        Harness harness = harness(new ItemStack(Items.STONE, 10));
        WorkstationEndpointObservationV2 withdrawal = observeWithdrawal(harness, 2);
        WorkstationEndpointObservationV2 returned = harness.service.observeReturn(
                RegistryAccess.EMPTY, harness.endpoint, 0, new ItemStack(Items.STONE, 2))
                .observation().orElseThrow();
        WorkstationEndpointPreparationV2 accepted = prepare(
                harness, "butchercraft:test_invocation/withdraw_first", withdrawal);

        assertEquals(WorkstationEndpointResultCode.INVENTORY_FRESHNESS_CONFLICT, harness.service.prepare(
                harness.endpoint, "butchercraft:test_invocation/return_second", returned).code());
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                harness.service.commit(RegistryAccess.EMPTY, harness.endpoint, accepted).code());
        assertEquals(8, harness.endpoint.stack.getCount());
        assertEquals(1, harness.service.journalSnapshot().records().size());
    }

    @Test
    void laterEffectAgainstLaterRevisionMayCommit() {
        Harness harness = harness(new ItemStack(Items.STONE, 8));
        commitWithdrawal(harness, "butchercraft:test_invocation/first", 2);
        commitWithdrawal(harness, "butchercraft:test_invocation/second", 3);

        assertEquals(3, harness.endpoint.stack.getCount());
        assertEquals(2L, harness.endpoint.inventoryRevision);
        assertEquals(2, harness.service.journalSnapshot().records().size());
    }

    @Test
    void compatibleDepositAndReturnMergeAllOrNothing() {
        Harness deposit = harness(new ItemStack(Items.STONE, 60));
        WorkstationEndpointObservationV2 depositObservation = deposit.service.observeDeposit(
                RegistryAccess.EMPTY, deposit.endpoint, 0, new ItemStack(Items.STONE, 4)).observation().orElseThrow();
        WorkstationEndpointPreparationV2 depositPreparation = prepare(
                deposit, "butchercraft:test_invocation/deposit", depositObservation);
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                deposit.service.commit(RegistryAccess.EMPTY, deposit.endpoint, depositPreparation).code());
        assertEquals(64, deposit.endpoint.stack.getCount());

        Harness returned = harness(new ItemStack(Items.STONE, 63));
        WorkstationEndpointObservationV2 returnObservation = returned.service.observeReturn(
                RegistryAccess.EMPTY, returned.endpoint, 0, new ItemStack(Items.STONE, 1)).observation().orElseThrow();
        WorkstationEndpointPreparationV2 returnPreparation = prepare(
                returned, "butchercraft:test_invocation/return", returnObservation);
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                returned.service.commit(RegistryAccess.EMPTY, returned.endpoint, returnPreparation).code());
        assertEquals(64, returned.endpoint.stack.getCount());
    }

    @Test
    void sourceReturnToEmptySlotRestoresCompletePayload() {
        Harness harness = harness(ItemStack.EMPTY);
        WorkstationEndpointObservationV2 observation = harness.service.observeReturn(
                RegistryAccess.EMPTY, harness.endpoint, 0, new ItemStack(Items.STONE, 10)).observation().orElseThrow();
        WorkstationEndpointPreparationV2 preparation = prepare(
                harness, "butchercraft:test_invocation/return_empty", observation);

        assertEquals(WorkstationEndpointResultCode.APPLIED,
                harness.service.commit(RegistryAccess.EMPTY, harness.endpoint, preparation).code());
        assertEquals(10, harness.endpoint.stack.getCount());
    }

    @Test
    void blockedAndIncompatibleDestinationNeverMutates() {
        Harness full = harness(new ItemStack(Items.STONE, 63));
        assertEquals(WorkstationEndpointResultCode.DESTINATION_CAPACITY_EXCEEDED, full.service.observeDeposit(
                RegistryAccess.EMPTY, full.endpoint, 0, new ItemStack(Items.STONE, 2)).code());
        assertEquals(63, full.endpoint.stack.getCount());
        assertTrue(full.service.journalSnapshot().records().isEmpty());

        Harness incompatible = harness(new ItemStack(Items.STONE, 1));
        assertEquals(WorkstationEndpointResultCode.INCOMPATIBLE_STACK, incompatible.service.observeDeposit(
                RegistryAccess.EMPTY, incompatible.endpoint, 0, new ItemStack(Items.DIRT, 1)).code());
        assertEquals(1, incompatible.endpoint.stack.getCount());
    }

    @Test
    void normalObservationFailuresUseTypedResultsWithoutMutation() {
        Harness empty = harness(ItemStack.EMPTY);
        assertEquals(WorkstationEndpointResultCode.SOURCE_EMPTY,
                empty.service.observeWithdrawal(RegistryAccess.EMPTY, empty.endpoint, 0, 1).code());

        Harness source = harness(new ItemStack(Items.STONE, 4));
        assertEquals(WorkstationEndpointResultCode.INVALID_QUANTITY,
                source.service.observeWithdrawal(RegistryAccess.EMPTY, source.endpoint, 0, 0).code());
        assertEquals(WorkstationEndpointResultCode.INSUFFICIENT_SOURCE_QUANTITY,
                source.service.observeWithdrawal(RegistryAccess.EMPTY, source.endpoint, 0, 5).code());
        assertEquals(4, source.endpoint.stack.getCount());
        assertTrue(source.service.journalSnapshot().records().isEmpty());
    }

    @Test
    void componentMismatchNeverMerges() {
        ItemStack destination = new ItemStack(Items.STONE, 1);
        destination.set(DataComponents.CUSTOM_NAME, Component.literal("Lot A"));
        ItemStack payload = new ItemStack(Items.STONE, 1);
        payload.set(DataComponents.CUSTOM_NAME, Component.literal("Lot B"));
        Harness harness = harness(destination);

        assertEquals(WorkstationEndpointResultCode.INCOMPATIBLE_STACK,
                harness.service.observeDeposit(RegistryAccess.EMPTY, harness.endpoint, 0, payload).code());
        assertEquals(Component.literal("Lot A"), harness.endpoint.stack.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void committedEffectReconcilesStaleProjectionFromExactPreState() {
        Harness harness = harness(new ItemStack(Items.STONE, 4));
        WorkstationEndpointObservationV2 observation = observeWithdrawal(harness, 1);
        WorkstationEndpointPreparationV2 preparation = prepare(
                harness, "butchercraft:test_invocation/recovery", observation);
        WorkstationEndpointJournalV2 committed = harness.service.journalSnapshot().update(
                preparation.effectId(), com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecordV2::commit);
        harness.storage.save(committed);
        StackAwareWorkstationEndpointService restarted = new StackAwareWorkstationEndpointService(
                committed, harness.storage, new ExactItemStackCodec());

        StackAwareEndpointEffectResult recovered = restarted.reconcile(
                RegistryAccess.EMPTY, harness.endpoint, committed.records().getFirst(), false);

        assertEquals(WorkstationEndpointResultCode.APPLIED, recovered.code());
        assertEquals(3, harness.endpoint.stack.getCount());
        assertEquals(WorkstationEndpointJournalState.RECONCILED,
                restarted.journalSnapshot().records().getFirst().state());
    }

    @Test
    void divergentProjectionAndReplacementEndpointFailVisibly() {
        Harness harness = harness(new ItemStack(Items.STONE, 4));
        WorkstationEndpointObservationV2 observation = observeWithdrawal(harness, 1);
        WorkstationEndpointPreparationV2 preparation = prepare(
                harness, "butchercraft:test_invocation/unknown", observation);
        WorkstationEndpointJournalV2 committed = harness.service.journalSnapshot().update(
                preparation.effectId(), com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecordV2::commit);
        harness.storage.save(committed);
        StackAwareWorkstationEndpointService restarted = new StackAwareWorkstationEndpointService(
                committed, harness.storage, new ExactItemStackCodec());
        harness.endpoint.stack = new ItemStack(Items.DIRT, 1);

        assertEquals(WorkstationEndpointResultCode.UNKNOWN_OUTCOME,
                restarted.reconcile(RegistryAccess.EMPTY, harness.endpoint, committed.records().getFirst(), false).code());

        FakeEndpoint replacement = new FakeEndpoint(new ItemStack(Items.STONE, 4), 64, 2L);
        assertEquals(WorkstationEndpointResultCode.ENDPOINT_IDENTITY_CONFLICT,
                restarted.reconcile(RegistryAccess.EMPTY, replacement, committed.records().getFirst(), false).code());
    }

    @Test
    void exactCodecRoundTripsCountsAndComponents() {
        ExactItemStackCodec codec = new ExactItemStackCodec();
        for (int count : new int[]{1, 2, 63, 64}) {
            ItemStack stack = new ItemStack(Items.STONE, count);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Lot " + count));

            ItemStack decoded = codec.decodeState(RegistryAccess.EMPTY, codec.encodeState(RegistryAccess.EMPTY, stack));

            assertEquals(count, decoded.getCount());
            assertTrue(ItemStack.isSameItemSameComponents(stack, decoded));
        }
    }

    private static void commitWithdrawal(Harness harness, String invocation, int quantity) {
        WorkstationEndpointObservationV2 observation = observeWithdrawal(harness, quantity);
        WorkstationEndpointPreparationV2 preparation = prepare(harness, invocation, observation);
        assertEquals(WorkstationEndpointResultCode.APPLIED,
                harness.service.commit(RegistryAccess.EMPTY, harness.endpoint, preparation).code());
    }

    private static WorkstationEndpointObservationV2 observeWithdrawal(Harness harness, int quantity) {
        return harness.service.observeWithdrawal(
                RegistryAccess.EMPTY, harness.endpoint, 0, quantity).observation().orElseThrow();
    }

    private static WorkstationEndpointPreparationV2 prepare(
            Harness harness,
            String invocation,
            WorkstationEndpointObservationV2 observation
    ) {
        return harness.service.prepare(harness.endpoint, invocation, observation).preparation().orElseThrow();
    }

    private Harness harness(ItemStack stack) {
        WorkstationEndpointJournalV2Storage storage = new WorkstationEndpointJournalV2Storage(
                tempDir.resolve("journal-" + System.nanoTime() + ".json"));
        FakeEndpoint endpoint = new FakeEndpoint(stack, 64, 1L);
        StackAwareWorkstationEndpointService service = new StackAwareWorkstationEndpointService(
                WorkstationEndpointJournalV2.empty(WORLD, CONFIG), storage, new ExactItemStackCodec());
        return new Harness(service, storage, endpoint);
    }

    private record Harness(
            StackAwareWorkstationEndpointService service,
            WorkstationEndpointJournalV2Storage storage,
            FakeEndpoint endpoint
    ) {}

    private static final class FakeEndpoint implements StackAwareWorkstationTransferEndpoint {
        private final WorkstationEndpointKey key = new WorkstationEndpointKey(
                "butchercraft:test_stack_endpoint", "minecraft:overworld", 3, 64, 3);
        private final WorkstationInstanceId instanceId;
        private final int capacity;
        private ItemStack stack;
        private long inventoryRevision;
        private long effectRevision;
        private long lastJournalSequence;
        private Optional<WorkstationEndpointEffectIdV2> lock = Optional.empty();
        private Optional<WorkstationEndpointEffectIdV2> lastEffect = Optional.empty();
        private Optional<String> lastOwnerResult = Optional.empty();

        private FakeEndpoint(ItemStack stack, int capacity, long generation) {
            this.stack = stack.copy();
            this.capacity = capacity;
            this.instanceId = WorkstationInstanceId.create(
                    WORLD, key, generation,
                    WorkstationEndpointConfiguration.standard().instanceAllocationConfigurationIdentity());
        }

        @Override public void activateStackAwareEndpoint() {}
        @Override public WorkstationInstanceId endpointInstanceId() { return instanceId; }
        @Override public WorkstationEndpointKey endpointKey() { return key; }
        @Override public String endpointOperationStateIdentity() { return "butchercraft:test/idle"; }
        @Override public String endpointPostOperationStateIdentity(WorkstationEndpointObservationV2 observation) {
            return "butchercraft:test/idle";
        }
        @Override public String endpointConfigurationIdentity() { return CONFIG; }
        @Override public int endpointSlotIndex(WorkstationEndpointEffectKind kind) { return 0; }
        @Override public ItemStack endpointStackSnapshot(int slotIndex) { return stack.copy(); }
        @Override public int endpointEffectiveCapacity(int slotIndex, ItemStack stack) {
            return Math.min(capacity, stack.getMaxStackSize());
        }
        @Override public long endpointInventoryRevision() { return inventoryRevision; }
        @Override public long endpointEffectRevision() { return effectRevision; }
        @Override public long endpointLastAppliedJournalSequence() { return lastJournalSequence; }
        @Override public Optional<WorkstationEndpointEffectIdV2> endpointPreparedEffectId() { return lock; }
        @Override public Optional<WorkstationEndpointEffectIdV2> endpointLastEffectId() { return lastEffect; }
        @Override public Optional<String> endpointLastOwnerResultIdentity() { return lastOwnerResult; }
        @Override public boolean endpointAcceptsCandidate(
                WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactPreStack, ItemStack exactPostStack) {
            return exact(stack, exactPreStack) && exactPostStack.getCount() <= capacity;
        }
        @Override public void lockPreparedEndpointEffect(
                WorkstationEndpointEffectIdV2 effectId, int slotIndex, long expectedInventoryRevision) {
            if (inventoryRevision != expectedInventoryRevision || lock.isPresent()) {
                throw new IllegalStateException("stale or locked");
            }
            lock = Optional.of(effectId);
        }
        @Override public void releasePreparedEndpointEffect(WorkstationEndpointEffectIdV2 effectId) {
            if (lock.filter(effectId::equals).isPresent()) lock = Optional.empty();
        }
        @Override public void applyCommittedEndpointEffect(
                WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactPreStack, ItemStack exactPostStack,
                long expectedInventoryRevision, long postInventoryRevision, long endpointEffectRevision,
                long journalSequence, WorkstationEndpointEffectIdV2 effectId, String ownerResultIdentity) {
            if (inventoryRevision != expectedInventoryRevision || !exact(stack, exactPreStack)
                    || lock.filter(effectId::equals).isEmpty()) {
                throw new IllegalStateException("projection freshness mismatch");
            }
            stack = exactPostStack.copy();
            inventoryRevision = postInventoryRevision;
            effectRevision = endpointEffectRevision;
            lastJournalSequence = journalSequence;
            lastEffect = Optional.of(effectId);
            lastOwnerResult = Optional.of(ownerResultIdentity);
            lock = Optional.empty();
        }

        private static boolean exact(ItemStack left, ItemStack right) {
            if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
            return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
        }
    }
}
