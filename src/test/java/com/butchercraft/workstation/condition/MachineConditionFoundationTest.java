package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MachineConditionFoundationTest {
    @TempDir Path directory;

    @Test
    void inertPolicyDoesNotWearOrFault() {
        MachineConditionState state = healthy(MachineConditionPolicy.inert("butchercraft:grinder"));
        for (int i = 1; i <= 100; i++) state = WorkstationConditionEngine.successfulProcessing(state, "test:child/" + i, i);
        assertEquals(0, state.mechanicalLoss());
        assertEquals(0, state.serviceDebt());
        assertTrue(state.fault().isEmpty());
        assertEquals(101, state.revision());
        assertEquals(100, state.effectCount());
    }

    @Test
    void exactExposureIsPartitionIndependent() {
        MachineConditionState initial = opened(policy(Optional.empty()));
        MachineConditionState single = WorkstationConditionEngine.settle(initial, "test:settle/all", "test:loaded", 100);
        MachineConditionState split = initial;
        for (int tick = 10; tick <= 100; tick += 10) {
            split = WorkstationConditionEngine.settle(split, "test:settle/" + tick, "test:loaded", tick);
        }
        assertEquals(37, single.mechanicalLoss());
        assertEquals(single.mechanicalLoss(), split.mechanicalLoss());
        assertEquals(single.exposureAccounts(), split.exposureAccounts());
        assertEquals(new ConditionRemainder(3, 5), single.account(ConditionExposureType.DRY_RUNNING).remainder());
        assertEquals(100, single.account(ConditionExposureType.DRY_RUNNING).graceProgress());
        assertNotEquals(single.digest(), split.digest(), "Exact evidence partitions/revisions are intentionally distinct");
    }

    @Test
    void thresholdTickDoesNotDependOnPollingAndEndsMotion() {
        MachineConditionState initial = opened(policy(Optional.of(10L)));
        MachineConditionState single = WorkstationConditionEngine.settle(initial, "test:whole", "test:loaded", 100);
        MachineConditionState split = initial;
        for (int tick = 10; tick <= 100 && split.activeExposure().isPresent(); tick += 10) {
            split = WorkstationConditionEngine.settle(split, "test:split/" + tick, "test:loaded", tick);
        }
        assertEquals(31, single.lastAccountedTick());
        assertEquals(single.lastAccountedTick(), split.lastAccountedTick());
        assertEquals(10, single.mechanicalLoss());
        assertEquals(single.exposureAccounts(), split.exposureAccounts());
        assertTrue(single.activeExposure().isEmpty());
        assertEquals(MachineConditionState.Band.FAULTED, single.band());
        assertEquals(MachineConditionState.Eligibility.DENIED_BY_FAULT, single.eligibility());
        assertTrue(single.permitsStop());
    }

    @Test
    void stopAndReloadKeepGraceAndFractionButProcessingResetsGraceOnly() {
        MachineConditionState initial = opened(policy(Optional.empty()));
        MachineConditionState stopped = WorkstationConditionEngine.closeExposure(initial, "test:stop", "test:loaded", 7,
                MachineConditionState.Suspension.STOPPED);
        ConditionCodec codec = new ConditionCodec();
        MachineConditionState restored = codec.state(codec.state(stopped));
        assertEquals(stopped, restored);
        MachineConditionState resumed = WorkstationConditionEngine.openExposure(restored, "test:resume",
                ConditionExposureType.DRY_RUNNING, "test:operating/2", 2, "test:loaded/2", 1000, false);
        MachineConditionState ended = WorkstationConditionEngine.closeExposure(resumed, "test:end", "test:loaded/2", 1007,
                MachineConditionState.Suspension.STOPPED);
        assertEquals(14, ended.account(ConditionExposureType.DRY_RUNNING).eligibleTicks());
        assertEquals(3, ended.mechanicalLoss());
        MachineConditionState completed = WorkstationConditionEngine.successfulProcessing(ended, "test:child", 1008);
        assertEquals(0, completed.account(ConditionExposureType.DRY_RUNNING).graceProgress());
        assertEquals(6, completed.mechanicalLoss());
        assertEquals(ended.account(ConditionExposureType.DRY_RUNNING).remainder(),
                completed.account(ConditionExposureType.DRY_RUNNING).remainder());
    }

    @Test
    void restartAndDiscontinuityNeverAccountTheUnprovenTail() {
        MachineConditionState settled = WorkstationConditionEngine.settle(opened(policy(Optional.empty())),
                "test:settle", "test:loaded", 20);
        assertThrows(IllegalStateException.class, () -> WorkstationConditionEngine.settle(settled,
                "test:invalid", "test:new_clock_epoch", 10000));
        for (MachineConditionState.Suspension reason : List.of(MachineConditionState.Suspension.RESTART_REQUIRED,
                MachineConditionState.Suspension.DISCONTINUITY)) {
            MachineConditionState suspended = WorkstationConditionEngine.suspendAtDurableCutoff(settled, "test:suspend", reason);
            assertEquals(20, suspended.lastAccountedTick());
            assertEquals(settled.mechanicalLoss(), suspended.mechanicalLoss());
            assertEquals(settled.exposureAccounts(), suspended.exposureAccounts());
            assertTrue(suspended.activeExposure().isEmpty());
            assertTrue(suspended.unprovenTailDiscarded());
        }
    }

    @Test
    void frozenPolicyRequiresExplicitClosedBoundaryAndPreservesEarnedFraction() {
        MachineConditionPolicy original = policy(Optional.empty());
        MachineConditionState active = opened(original);
        MachineConditionPolicy next = MachineConditionPolicy.create(original.machineType(), "test:next", 100, 7, 1,
                5, 10, 80, Optional.empty(), List.of(new ConditionExposurePolicy(
                        ConditionExposureType.DRY_RUNNING, 6, 1, 3, 20, true)));
        var registry = new MachineConditionPolicyRegistry(List.of(original, next), Map.of(next.machineType(), next.identity()));
        assertEquals(original, registry.require(active.policy().identity(), original.machineType()));
        assertThrows(IllegalStateException.class, () -> WorkstationConditionEngine.changePolicy(active, "test:policy", next, 7));
        MachineConditionState closed = WorkstationConditionEngine.closeExposure(active, "test:closed", "test:loaded", 7,
                MachineConditionState.Suspension.STOPPED);
        MachineConditionState switched = WorkstationConditionEngine.changePolicy(closed, "test:policy", next, 7);
        assertEquals(closed.exposureAccounts(), switched.exposureAccounts());
        MachineConditionState reopened = WorkstationConditionEngine.openExposure(switched, "test:open/2",
                ConditionExposureType.DRY_RUNNING, "test:operating/2", 2, "test:loaded/2", 7, false);
        MachineConditionState result = WorkstationConditionEngine.settle(reopened, "test:settle/2", "test:loaded/2", 10);
        assertEquals(1, result.mechanicalLoss());
        assertEquals(new ConditionRemainder(2, 5), result.account(ConditionExposureType.DRY_RUNNING).remainder());
    }

    @Test
    void stagedReceiptDoesNotAuthorizeEffectAndDuplicatesRequireExactRequest() {
        MachineConditionState initial = healthy(policy(Optional.empty()));
        MachineConditionState after = WorkstationConditionEngine.successfulProcessing(initial, "test:child", 1);
        ConditionEffectReceipt receipt = ConditionEffectReceipt.prepare(initial, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                "test:request", Optional.empty(), "test:joint_candidate");
        ConditionReceiptStorage storage = new ConditionReceiptStorage(directory);
        storage.stage(receipt);
        ConditionProjection before = ConditionProjection.initial(initial);
        assertTrue(storage.observe(before, "test:child", "test:request").isEmpty());
        ConditionProjection committed = before.committed(receipt);
        assertEquals(receipt, storage.observe(committed, "test:child", "test:request").orElseThrow());
        storage.stage(receipt);
        assertEquals(1, storage.closure(committed).size());
        assertThrows(IllegalStateException.class, () -> storage.observe(committed, "test:child", "test:conflict"));
        assertThrows(IllegalArgumentException.class, () -> committed.committed(receipt));
        assertEquals(receipt, new ConditionReceiptStorage(directory).read(receipt.digest()));
    }

    @Test
    void serviceGateDoesNotGrantRunOrStopAuthority() {
        MachineConditionState state = healthy(policy(Optional.empty()));
        assertFalse(state.permitsService(true, true, false));
        assertFalse(state.permitsService(false, false, false));
        assertTrue(state.permitsService(false, true, false));
        assertTrue(state.permitsService(false, false, true));
        assertTrue(state.permitsStop());
    }

    @Test
    void malformedDigestsAndArithmeticFailBeforePublication() {
        MachineConditionState state = healthy(policy(Optional.empty()));
        ConditionCodec codec = new ConditionCodec();
        var json = codec.state(state);
        json.addProperty("mechanical_loss", 1);
        assertThrows(IllegalArgumentException.class, () -> codec.state(json));
        assertThrows(IllegalArgumentException.class, () -> new ConditionRemainder(2, 4));
        assertThrows(ArithmeticException.class, () -> ConditionRemainder.ZERO.accumulate(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> WorkstationConditionEngine.settle(opened(policy(Optional.empty())),
                "test:backward", "test:loaded", -1));
    }

    @Test
    void activeIndexTracksOnlyDueSubsetAndCanBeRebuilt() {
        ConditionDueIndex index = new ConditionDueIndex();
        MachineConditionState active = opened(policy(Optional.empty()));
        index.observe(active);
        for (int i = 0; i < 1000; i++) index.observe(healthy(new WorkstationInstanceId(
                "butchercraft:workstation_instance/v1/fixture_" + i), policy(Optional.empty())));
        assertEquals(1, index.size());
        assertTrue(index.due(9, 100).isEmpty());
        assertEquals(1, index.due(10, 100).size());
        assertEquals(active.instanceId(), index.due(10, 100).getFirst().instanceId());
        index.clear();
        index.observe(active);
        assertEquals(1, index.due(10, 100).size());
        MachineConditionState suspended = WorkstationConditionEngine.suspendAtDurableCutoff(active, "test:restart",
                MachineConditionState.Suspension.RESTART_REQUIRED);
        index.observe(suspended);
        assertEquals(0, index.size());
    }

    @Test
    void unsupportedStatePolicyAndReceiptSchemasFailVisibly() {
        var codec = new ConditionCodec();
        var state = healthy(policy(Optional.empty()));
        var futureState = codec.state(state);
        futureState.addProperty("schema_version", 2);
        assertThrows(UnsupportedConditionSchemaException.class, () -> codec.state(futureState));
        var futurePolicy = codec.state(state);
        futurePolicy.getAsJsonObject("policy").addProperty("schema_version", 2);
        assertThrows(UnsupportedConditionSchemaException.class, () -> codec.state(futurePolicy));
        var receipt = ConditionEffectReceipt.prepare(state,
                WorkstationConditionEngine.successfulProcessing(state, "test:future", 1),
                ConditionEffectKind.SUCCESSFUL_PROCESSING, "test:future", Optional.empty(), "test:candidate");
        var futureReceipt = com.google.gson.JsonParser.parseString(new String(codec.freeze(receipt),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        futureReceipt.addProperty("schema_version", 2);
        assertThrows(UnsupportedConditionSchemaException.class, () -> codec.receipt(
                futureReceipt.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void staleSettlementCannotFollowStopSupplyOrReplacement() {
        var initial = healthy(policy(Optional.empty()));
        var active = opened(policy(Optional.empty()));
        var openedReceipt = ConditionEffectReceipt.prepare(initial, active, ConditionEffectKind.EXPOSURE_OPENED,
                "test:open", Optional.empty(), "test:open_candidate");
        var activeProjection = ConditionProjection.initial(initial).committed(openedReceipt);
        var settled = WorkstationConditionEngine.settle(active, "test:settle", "test:loaded", 20);
        var stale = ConditionEffectReceipt.prepare(active, settled, ConditionEffectKind.EXPOSURE_SETTLED,
                "test:settle", activeProjection.receiptHead(), "test:candidate");
        var stopped = WorkstationConditionEngine.closeExposure(active, "test:stop", "test:loaded", 10,
                MachineConditionState.Suspension.STOPPED);
        var stopReceipt = ConditionEffectReceipt.prepare(active, stopped, ConditionEffectKind.EXPOSURE_CLOSED,
                "test:stop", activeProjection.receiptHead(), "test:stop_candidate");
        var stoppedProjection = activeProjection.committed(stopReceipt);
        assertThrows(IllegalArgumentException.class, () -> stoppedProjection.committed(stale));
        var nextInstance = healthy(new WorkstationInstanceId("butchercraft:workstation_instance/v1/replacement"), active.policy());
        assertThrows(IllegalArgumentException.class, () -> ConditionProjection.initial(nextInstance).committed(stale));
        var due = new ConditionDueIndex();
        due.observe(active);
        due.remove(active.instanceId());
        due.observe(nextInstance);
        assertTrue(due.due(Long.MAX_VALUE, 64).isEmpty());
    }

    @Test
    void processingFreezesConsequenceAndRejectsConcurrentConditionChange() {
        var state = healthy(policy(Optional.empty()));
        var plan = ConditionProcessingPreparation.of(state);
        var after = plan.complete("test:child", 20);
        assertEquals(3, after.mechanicalLoss());
        assertEquals(1, after.serviceDebt());
        assertEquals(plan, ConditionProcessingPreparation.of(state));
        assertFalse(state.permitsService(true, false, false));
        var changed = WorkstationConditionEngine.bindOperatingTransition(state, "test:transition", 1);
        assertNotEquals(plan.freshnessIdentity(), ConditionProcessingPreparation.of(changed).freshnessIdentity());
    }

    static MachineConditionPolicy policy(Optional<Long> threshold) {
        return MachineConditionPolicy.create("butchercraft:grinder", "test:nonzero_foundation", 100, 3, 1, 5, 10, 80,
                threshold, List.of(new ConditionExposurePolicy(ConditionExposureType.DRY_RUNNING, 6, 2, 5, 10, true)));
    }

    static MachineConditionState healthy(MachineConditionPolicy policy) {
        return healthy(new WorkstationInstanceId("butchercraft:workstation_instance/v1/fixture"), policy);
    }

    static MachineConditionState healthy(WorkstationInstanceId instance, MachineConditionPolicy policy) {
        return MachineConditionState.healthy(instance, policy,
                ConditionInitializationEvidence.create(instance, policy.identity(), Optional.empty()), 0);
    }

    static MachineConditionState opened(MachineConditionPolicy policy) {
        return WorkstationConditionEngine.openExposure(healthy(policy), "test:open", ConditionExposureType.DRY_RUNNING,
                "test:operating/1", 1, "test:loaded", 0, false);
    }
}
