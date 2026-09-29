package com.butchercraft.workstation.checkpoint;

import com.butchercraft.workstation.condition.*;
import com.butchercraft.workstation.endpoint.*;
import com.butchercraft.workstation.projection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.butchercraft.workstation.projection.ConditionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionScaleCheckpointTest {
    @TempDir Path directory;

    @Test
    void thousandMachinesEvaluateOnlyTenActiveExposuresAndFreezeExactClosure() throws Exception {
        var grinder = policy("butchercraft:grinder");
        var patty = policy("butchercraft:patty_former");
        var policies = new MachineConditionPolicyRegistry(List.of(grinder, patty),
                Map.of(grinder.machineType(), grinder.identity(), patty.machineType(), patty.identity()));
        var storage = new WorkstationProjectionStorage(directory.resolve("projections"));
        var receipts = new ConditionReceiptStorage(directory.resolve("receipts"));
        var codec = new WorkstationProjectionCodec();
        var index = new ConditionDueIndex();
        List<WorkstationInstanceRecord> records = new ArrayList<>();
        Map<WorkstationInstanceId, DurableWorkstationProjection> states = new HashMap<>();
        long addedBytes = 0;
        long enabledBytes = 0;
        int enabled = 0;
        for (int i = 0; i < 1_000; i++) {
            String type = i % 3 == 0 ? "butchercraft:cutting_table"
                    : i % 3 == 1 ? grinder.machineType() : patty.machineType();
            var instance = instance(type, i + 1, i);
            records.add(instance);
            var legacy = legacy(instance);
            var projection = ConditionInitialization.candidate(legacy, policies, 0, true, true);
            if (projection.condition().orElseThrow().state().isPresent()) {
                enabled++;
                addedBytes += codec.freeze(projection).length - codec.freeze(legacy).length;
                enabledBytes += codec.freeze(projection).length;
                var condition = projection.condition().orElseThrow();
                var before = condition.state().orElseThrow();
                if (i < 16) {
                    var after = WorkstationConditionEngine.openExposure(before, "test:open/" + i,
                            ConditionExposureType.DRY_RUNNING, "test:operating/" + i, 1, "test:loaded/" + i, 0, false);
                    var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.EXPOSURE_OPENED,
                            "test:open/" + i, condition.receiptHead(), "test:owner/" + i);
                    receipts.stage(receipt);
                    projection = projection.withCondition(3, condition.committed(receipt));
                    index.observe(after);
                } else if (i < 160) {
                    var after = WorkstationConditionEngine.successfulProcessing(before, "test:child/" + i, 0);
                    var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.SUCCESSFUL_PROCESSING,
                            "test:child/" + i, condition.receiptHead(), "test:owner/" + i);
                    receipts.stage(receipt);
                    projection = projection.withCondition(3, condition.committed(receipt));
                }
            }
            storage.save(projection);
            states.put(instance.instanceId(), projection);
        }
        assertEquals(666, enabled);
        assertEquals(10, index.size());
        long periodicReads = 0;
        long publications = 0;
        long evaluationNanos = 0;
        long mutationNanos = 0;
        Map<WorkstationInstanceId, Long> initialRevisions = new HashMap<>();
        states.forEach((id, projection) -> initialRevisions.put(id, projection.projectionRevision()));
        for (long tick = 1; tick <= 1_000; tick++) {
            long started = System.nanoTime();
            var due = index.due(tick, 64);
            evaluationNanos += System.nanoTime() - started;
            for (var entry : due) {
                periodicReads++;
                var beforeProjection = states.get(entry.instanceId());
                var before = beforeProjection.condition().orElseThrow().state().orElseThrow();
                assertEquals(entry.conditionDigest(), before.digest());
                var after = WorkstationConditionEngine.settle(before, "test:settle/" + tick + "/" + entry.instanceId().value(),
                        before.activeExposure().orElseThrow().availabilityProofIdentity(), tick);
                var receipt = ConditionEffectReceipt.prepare(before, after, ConditionEffectKind.EXPOSURE_SETTLED,
                        after.lastEffectIdentity().orElseThrow(), beforeProjection.condition().orElseThrow().receiptHead(), "test:owner");
                var candidate = beforeProjection.withCondition(beforeProjection.projectionRevision() + 1,
                        beforeProjection.condition().orElseThrow().committed(receipt));
                started = System.nanoTime();
                receipts.stage(receipt);
                storage.save(candidate);
                mutationNanos += System.nanoTime() - started;
                states.put(entry.instanceId(), candidate);
                index.observe(after);
                publications++;
            }
        }
        assertEquals(500, publications);
        assertEquals(500, periodicReads);
        assertEquals(990, states.entrySet().stream().filter(entry -> entry.getValue().projectionRevision()
                == initialRevisions.get(entry.getKey())).count());
        var rebuilt = new ConditionDueIndex();
        states.values().forEach(value -> value.condition().orElseThrow().state().ifPresent(rebuilt::observe));
        assertEquals(index.due(1_020, 64), rebuilt.due(1_020, 64));
        long started = System.nanoTime();
        assertEquals(states.get(records.get(100).instanceId()), storage.read(records.get(100)).projection().orElseThrow());
        long lookupNanos = System.nanoTime() - started;
        var registry = new WorkstationInstanceRegistry(1, 2, WORLD, 1_001, ALLOCATION, records);
        started = System.nanoTime();
        var enumeration = storage.enumerate(registry);
        long enumerationNanos = System.nanoTime() - started;
        assertEquals(1_000, enumeration.size());
        var snapshot = WorkstationCheckpointProjectionService.capture(registry, List.of(),
                new WorkstationCheckpointProjectionService.ProjectionAccess() {
                    public WorkstationProjectionReadResult read(WorkstationInstanceRecord instance) { return storage.read(instance); }
                    public FrozenWorkstationProjectionSnapshot freeze(WorkstationInstanceRecord instance) { return storage.freezeForCheckpoint(instance); }
                    public boolean loaded(WorkstationInstanceRecord instance) { return instance.endpointKey().x() < 250; }
                }, receipts::frozen);
        assertTrue(snapshot.restorable(), snapshot.blockers().toString());
        assertEquals(1_000, snapshot.availableProjectionCount());
        assertEquals(750, snapshot.unloadedProjectionCount());
        var root = com.google.gson.JsonParser.parseString(snapshot.json()).getAsJsonObject();
        assertEquals(3, root.get("schema_version").getAsInt());
        var closure = ConditionCheckpointClosure.decode(root.getAsJsonArray("condition_receipts"), List.copyOf(states.values()));
        assertEquals(606, closure.nativeFiles(directory.resolve("restored")).size());
        for (var entry : states.values()) {
            assertEquals(entry, codec.decode(codec.freeze(entry)), "Frozen condition/product state is not replayed");
        }
        long projectionBytes = bytes(storage.rootDirectory());
        long receiptBytes = bytes(directory.resolve("receipts"));
        System.out.printf("IM-033A scale: registered=1000 enabled=%d active=10 worn=96 unloaded=750 "
                        + "durationTicks=1000 cadence=20 publications=%d periodicOwnerReads=%d inactiveWrites=0 "
                        + "lookupMs=%.3f mutationTotalMs=%.3f dueEvaluationTotalMs=%.3f enumerationMs=%.3f "
                        + "healthyEnabledAverageBytes=%d addedAverageBytes=%d projectionBytes=%d receiptBytes=%d "
                        + "checkpointBytes=%d checkpointFreezeMs=%.3f receiptCount=606 chunkLoads=0%n",
                enabled, publications, periodicReads, lookupNanos / 1e6, mutationNanos / 1e6,
                evaluationNanos / 1e6, enumerationNanos / 1e6, enabledBytes / enabled, addedBytes / enabled,
                projectionBytes, receiptBytes, snapshot.participantBytes(), snapshot.projectionFreezeDurationNanos() / 1e6);
    }

    private static MachineConditionPolicy policy(String type) {
        return MachineConditionPolicy.create(type, "test:condition_scale", 10_000, 3, 1, 5, 10, 8_000,
                Optional.empty(), List.of(new ConditionExposurePolicy(ConditionExposureType.DRY_RUNNING, 6, 2, 5, 20, true)));
    }

    private static long bytes(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }).sum();
        }
    }
}
