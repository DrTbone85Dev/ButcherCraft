package com.butchercraft.workstation.condition;

import com.butchercraft.integration.machine.MachineConditionPolicies;
import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationInventoryCommitPlan;
import com.butchercraft.workstation.checkpoint.ConditionCheckpointClosure;
import com.butchercraft.workstation.projection.*;
import com.butchercraft.world.execution.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import static com.butchercraft.workstation.projection.ConditionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionPersistenceTest {
    @TempDir Path directory;

    @Test
    void historicalMachineMigrationIsExplicitIdempotentAndPreservesSourceBytes() throws Exception {
        var policies = MachineConditionPolicies.standard();
        for (String type : List.of("butchercraft:grinder", "butchercraft:patty_former", "butchercraft:cutting_table")) {
            var instance = instance(type, 1, type.hashCode());
            var old = legacy(instance);
            var storage = storage();
            byte[] historical = storage.save(old).frozenBytes();
            var initialized = ConditionInitialization.candidate(old, policies, 200, false, true);
            storage.save(initialized);
            assertEquals(initialized, storage.read(instance).projection().orElseThrow());
            assertEquals(initialized, ConditionInitialization.candidate(initialized, policies, 1000, false, true));
            if (type.endsWith("cutting_table")) assertEquals(ConditionProjection.notApplicable(), initialized.condition().orElseThrow());
            else {
                var state = initialized.condition().orElseThrow().state().orElseThrow();
                assertEquals(0, state.mechanicalLoss());
                assertEquals(1, state.revision());
                assertEquals(0, state.effectCount());
                assertEquals(old.stateDigest(), state.initializationEvidence().legacyProjectionDigest().orElseThrow());
            }
            try (var paths = Files.walk(storage.rootDirectory().resolve("legacy_sources"))) {
                assertTrue(paths.filter(Files::isRegularFile).anyMatch(path -> {
                    try { return java.util.Arrays.equals(historical, Files.readAllBytes(path)); }
                    catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                }));
            }
            assertThrows(IllegalStateException.class, () -> storage.save(old));
            assertThrows(IllegalStateException.class, () -> ConditionInitialization.candidate(old, policies, 0, false, false));
        }
    }

    @Test
    void sixtyFourJointChildrenPreserveProductsConditionAndExactReceiptClosure() {
        var instance = instance("butchercraft:grinder", 1, 1);
        var policy = MachineConditionFoundationTest.policy(Optional.empty());
        var registry = new MachineConditionPolicyRegistry(List.of(policy), Map.of(policy.machineType(), policy.identity()));
        var inventory = new WorkstationInventory(GrinderWorkstation.capability(), GrinderWorkstation.slotCapacityPolicy(), () -> { });
        inventory.setInputInternal(new ItemStack(Items.STONE, 64));
        var current = ConditionInitialization.candidate(projection(instance, 1, 0,
                List.of(inventory.input(), inventory.output()), Optional.empty(), Optional.empty()), registry, 0, true, true);
        var storage = storage();
        var receipts = receipts();
        storage.save(current);
        for (int child = 1; child <= 64; child++) {
            var plan = new WorkstationInventoryCommitPlan(inventory, List.of(0), List.of(new ItemStack(Items.DIRT)));
            var operation = ExecutionOperationId.of("butchercraft:execution_operation/v1/condition_child_" + child);
            var result = ExecutionOwnerResultEvidence.of("butchercraft:workstation", "test:product_result/" + child,
                    new ExecutionDomainEffectIdentity("butchercraft:execution_domain_effect/v1/condition_child_" + child), "sha256:" + "9".repeat(64));
            var prepared = ConditionProcessingCandidates.prepare(current.condition().orElseThrow(),
                    ConditionProcessingPreparation.of(current.condition().orElseThrow().state().orElseThrow()), operation,
                    new ConditionDigest("test:inventory_post").add(child).finish(), result, child,
                    current.projectionRevision() + 1);
            receipts.stage(prepared.receipt());
            assertEquals(current, storage.read(instance).projection().orElseThrow(), "Staging grants no authority");
            plan.commit();
            current = projection(instance, current.projectionRevision() + 1, child,
                    List.of(inventory.input(), inventory.output()), Optional.of(operation.value()),
                    Optional.of(prepared.ownerResult().ownerResultIdentity())).withCondition(current.projectionRevision() + 1, prepared.projection());
            storage.save(current);
            assertTrue(ConditionCoherenceValidator.validate(current, registry, receipts::read).coherent());
            assertEquals(prepared.receipt(), receipts.observe(current.condition().orElseThrow(), prepared.receipt().effectIdentity(),
                    prepared.receipt().requestDigest()).orElseThrow());
        }
        var restored = storage().read(instance).projection().orElseThrow();
        assertEquals(current, restored);
        assertTrue(restored.slots().getFirst().exactStack().isEmpty());
        assertEquals(64, new ExactItemStackCodec().decode(RegistryAccess.EMPTY, restored.slots().get(1).exactStack().orElseThrow()).getCount());
        assertEquals(100, restored.condition().orElseThrow().state().orElseThrow().mechanicalLoss());
        assertEquals(65, restored.condition().orElseThrow().state().orElseThrow().revision());
        assertEquals(64, receipts.closure(restored.condition().orElseThrow()).size());
        System.out.println("IM-033A joint fixture: 64 input -> 64 children -> 64 output; loss=100 (saturated), revision=65, effects=64; exact reload");
    }

    @Test
    void failedPreparationStalePlanAndStagedReceiptNeverChangeDurableCondition() {
        var instance = instance("butchercraft:grinder", 1, 2);
        var policy = MachineConditionFoundationTest.policy(Optional.empty());
        var state = MachineConditionFoundationTest.healthy(instance.instanceId(), policy);
        var current = legacy(instance).withCondition(2, ConditionProjection.initial(state));
        storage().save(current);
        var prep = ConditionProcessingPreparation.of(state);
        var after = WorkstationConditionEngine.successfulProcessing(state, "test:other", 1);
        var receipt = ConditionEffectReceipt.prepare(state, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:request", Optional.empty(), "test:candidate");
        receipts().stage(receipt);
        assertEquals(current, storage().read(instance).projection().orElseThrow());
        assertThrows(IllegalStateException.class, () -> ConditionProcessingCandidates.prepare(current.condition().orElseThrow().committed(receipt),
                prep, ExecutionOperationId.of("butchercraft:execution_operation/v1/child"), "test:post", ExecutionOwnerResultEvidence.of("test:owner", "test:result",
                        new ExecutionDomainEffectIdentity("butchercraft:execution_domain_effect/v1/child"), "sha256:" + "9".repeat(64)), 2));
        assertEquals(current, storage().read(instance).projection().orElseThrow());
    }

    @Test
    void newerProjectionCannotRegressOrForkConditionRevision() {
        var instance = instance("butchercraft:grinder", 1, 3);
        var before = MachineConditionFoundationTest.healthy(instance.instanceId(), MachineConditionFoundationTest.policy(Optional.empty()));
        var after = WorkstationConditionEngine.successfulProcessing(before, "test:one", 1);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:one", Optional.empty(), "test:candidate");
        var initial = legacy(instance).withCondition(2, ConditionProjection.initial(before));
        storage().save(initial);
        storage().save(initial.withCondition(3, initial.condition().orElseThrow().committed(receipt)));
        assertThrows(IllegalStateException.class, () -> storage().save(initial.withCondition(4, initial.condition().orElseThrow())));
        var fork = WorkstationConditionEngine.successfulProcessing(before, "test:different", 1);
        var forkReceipt = ConditionEffectReceipt.prepare(before, fork, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:fork", Optional.empty(), "test:fork_candidate");
        assertThrows(IllegalStateException.class, () -> storage().save(initial.withCondition(4, initial.condition().orElseThrow().committed(forkReceipt))));
    }

    @Test
    void faultIsCoherentButMissingReceiptIsRecoveryFailureAndCheckpointRejectsIt() {
        var instance = instance("butchercraft:grinder", 1, 4);
        var policy = MachineConditionFoundationTest.policy(Optional.of(3L));
        var before = MachineConditionFoundationTest.healthy(instance.instanceId(), policy);
        var after = WorkstationConditionEngine.successfulProcessing(before, "test:fault", 1);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:fault", Optional.empty(), "test:candidate");
        var projection = legacy(instance).withCondition(2, ConditionProjection.initial(before).committed(receipt));
        var registry = new MachineConditionPolicyRegistry(List.of(policy), Map.of(policy.machineType(), policy.identity()));
        assertEquals(ConditionCoherenceCode.EFFECT_EVIDENCE_MISSING,
                ConditionCoherenceValidator.validate(projection, registry, ignored -> null).code());
        receipts().stage(receipt);
        assertTrue(ConditionCoherenceValidator.validate(projection, registry, receipts()::read).coherent());
        ConditionCheckpointClosure frozen = new ConditionCheckpointClosure();
        frozen.include(projection, receipts()::frozen);
        var restored = ConditionCheckpointClosure.decode(frozen.encode(), List.of(projection));
        assertEquals(1, restored.nativeFiles(directory.resolve("restore")).size());
        assertThrows(RuntimeException.class, () -> ConditionCheckpointClosure.decode(new com.google.gson.JsonArray(), List.of(projection)));
        assertTrue(after.permitsStop());
        assertEquals(MachineConditionState.Band.FAULTED, after.band());
    }

    @Test
    void pendingOperatingTransitionBlocksCheckpointAndRoundTripsExactBinding() {
        var instance = instance("butchercraft:grinder", 1, 5);
        var before = MachineConditionFoundationTest.healthy(instance.instanceId(), MachineConditionFoundationTest.policy(Optional.empty()));
        var next = new WorkstationOperatingStateReference(instance.instanceId().value(), 2, "OFF", "sha256:" + "5".repeat(64));
        var binding = new ConditionTransitionBinding(Optional.empty(), next, 10);
        var after = WorkstationConditionEngine.bindOperatingTransition(before, "test:transition", 10);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.OPERATING_TRANSITION,
                binding.identity(), Optional.empty(), binding.identity(), Optional.of(binding));
        receipts().stage(receipt);
        var committed = ConditionProjection.initial(before).committed(receipt);
        var pending = new ConditionProjection(committed.applicability(), committed.state(), committed.receiptHead(), Optional.of(receipt.digest()));
        assertEquals(receipt, receipts().read(receipt.digest()));
        assertThrows(IllegalStateException.class, () -> ConditionEvidenceClosure.verify(pending, receipts()::read));
        assertEquals(pending, new ConditionCodec().projection(new ConditionCodec().projection(pending)));
    }

    @Test
    void replacementNeverInheritsConditionAndOldReceiptCannotApply() {
        var first = instance("butchercraft:grinder", 1, 6);
        var second = instance("butchercraft:grinder", 2, 6);
        var policy = MachineConditionFoundationTest.policy(Optional.empty());
        var before = MachineConditionFoundationTest.healthy(first.instanceId(), policy);
        var after = WorkstationConditionEngine.successfulProcessing(before, "test:old", 1);
        var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:old", Optional.empty(), "test:old_candidate");
        var replacement = ConditionProjection.initial(MachineConditionFoundationTest.healthy(second.instanceId(), policy));
        assertNotEquals(first.instanceId(), second.instanceId());
        assertThrows(IllegalArgumentException.class, () -> replacement.committed(receipt));
        assertEquals(0, replacement.state().orElseThrow().mechanicalLoss());
    }

    @Test
    void repeatedIndependentWindowsPublicationsLeaveNoAttemptDebris() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                int index = i;
                tasks.add(executor.submit(() -> {
                    var instance = instance("butchercraft:grinder", index, index);
                    var state = MachineConditionFoundationTest.healthy(instance.instanceId(), MachineConditionFoundationTest.policy(Optional.empty()));
                    var current = legacy(instance).withCondition(2, ConditionProjection.initial(state));
                    storage().save(current);
                    for (int tick = 1; tick <= 40; tick++) {
                        var next = WorkstationConditionEngine.successfulProcessing(state, "test:stress/" + index + "/" + tick, tick);
                        var receipt = ConditionEffectReceipt.prepare(state, next, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                                "test:stress/" + tick, current.condition().orElseThrow().receiptHead(), "test:stress_candidate/" + tick);
                        receipts().stage(receipt);
                        current = current.withCondition(current.projectionRevision() + 1, current.condition().orElseThrow().committed(receipt));
                        storage().save(current);
                        state = next;
                    }
                    assertEquals(current, storage().read(instance).projection().orElseThrow());
                    assertEquals(40, receipts().closure(current.condition().orElseThrow()).size());
                }));
            }
            for (var task : tasks) task.get();
        }
        try (var files = Files.walk(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp-") || path.getFileName().toString().endsWith(".attempt")));
        }
    }

    private WorkstationProjectionStorage storage() { return new WorkstationProjectionStorage(directory.resolve("workstations/projections/v1")); }
    private ConditionReceiptStorage receipts() { return new ConditionReceiptStorage(directory.resolve("workstations/condition_effects/v1")); }
}
