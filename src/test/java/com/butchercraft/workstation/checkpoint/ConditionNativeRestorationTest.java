package com.butchercraft.workstation.checkpoint;

import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.condition.*;
import com.butchercraft.workstation.endpoint.*;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.*;
import com.butchercraft.world.checkpoint.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.butchercraft.workstation.projection.ConditionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionNativeRestorationTest {
    @TempDir Path directory;

    @Test
    void restoresExactFaultExposureAndReceiptBytesWithoutLoadingOrEffectReplay() throws Exception {
        var instance = instance("butchercraft:grinder", 1, 0);
        var policy = MachineConditionPolicy.create("butchercraft:grinder", "test:native_condition", 100,
                3, 1, 5, 10, 80, Optional.of(10L),
                List.of(new ConditionExposurePolicy(ConditionExposureType.DRY_RUNNING, 6, 2, 5, 20, true)));
        var policies = new MachineConditionPolicyRegistry(List.of(policy), Map.of(policy.machineType(), policy.identity()));
        var receipts = new ConditionReceiptStorage(directory.resolve("source_receipts"));
        var current = ConditionInitialization.candidate(legacy(instance), policies, 0, true, true);
        var before = current.condition().orElseThrow().state().orElseThrow();
        var opened = WorkstationConditionEngine.openExposure(before, "test:open", ConditionExposureType.DRY_RUNNING,
                "test:operating", 1, "test:loaded", 0, false);
        var open = ConditionEffectReceipt.prepare(before, opened, ConditionEffectKind.EXPOSURE_OPENED,
                "test:open", Optional.empty(), "test:operating");
        receipts.stage(open);
        current = current.withCondition(3, current.condition().orElseThrow().committed(open));
        var settled = WorkstationConditionEngine.settle(opened, "test:fault", "test:loaded", 100);
        var fault = ConditionEffectReceipt.prepare(opened, settled, ConditionEffectKind.EXPOSURE_SETTLED,
                "test:fault", Optional.of(open.digest()), "test:operating");
        receipts.stage(fault);
        current = current.withCondition(4, current.condition().orElseThrow().committed(fault));
        assertEquals(31, settled.fault().orElseThrow().authoritativeTick());
        assertTrue(ConditionCoherenceValidator.validate(current, policies, receipts::read).coherent());
        assertTrue(settled.permitsStop());
        var registry = new WorkstationInstanceRegistry(1, 2, WORLD, 2, ALLOCATION, List.of(instance));
        var frozen = current;
        var codec = new WorkstationProjectionCodec();
        var snapshot = WorkstationCheckpointProjectionService.capture(registry, List.of(),
                access(frozen, codec), receipts::frozen);
        assertTrue(snapshot.restorable(), snapshot.blockers().toString());
        assertEquals(1, snapshot.unloadedProjectionCount());
        byte[] evidence = snapshot.json().getBytes(StandardCharsets.UTF_8);
        var context = context();
        var files = new ArrayList<>(WorkstationCheckpointProjectionService.prepareRestorationProjectionFiles(
                context, registry, evidence));
        files.add(OwnerNativeRestorationPlan.NativeFile.of("workstation_instances.json", "workstation_instances.json",
                new WorkstationInstanceStorage(directory.resolve("unused")).serialize(registry).getBytes(StandardCharsets.UTF_8)));
        var descriptor = new OwnerSnapshotDescriptor(LegacySplitRecoveryParticipants.WORKSTATION, 5,
                "test:workstation_snapshot", CheckpointSnapshotDigest.sha256(evidence), CheckpointSnapshotParticipation.REQUIRED,
                "test:configuration", context.worldIdentityRoot(), context.generationManifest().generationId(), 100, 2);
        var plan = OwnerNativeRestorationPlan.create(descriptor, files, Optional.of(evidence));
        for (var file : files) AtomicFilePublication.publishBytes(context.ownerRoot().resolve(file.targetRelativePath()),
                file.bytes(), "condition restoration fixture");
        WorkstationCheckpointProjectionService.verifyRestored(context, plan);
        WorkstationCheckpointProjectionService.verifyRestored(context, plan);
        var restoredStorage = new WorkstationProjectionStorage(context.ownerRoot().resolve("workstations/projections/v1"));
        var restored = restoredStorage.read(instance).projection().orElseThrow();
        assertEquals(frozen, restored);
        assertArrayEquals(codec.freeze(frozen), Files.readAllBytes(restoredStorage.pathFor(instance.instanceId())));
        var restoredReceipts = new ConditionReceiptStorage(context.ownerRoot().resolve("workstations/condition_effects/v1"));
        assertEquals(2, restoredReceipts.closure(restored.condition().orElseThrow()).size());
        assertTrue(ConditionCoherenceValidator.validate(restored, policies, restoredReceipts::read).coherent());
        assertEquals(settled, restored.condition().orElseThrow().state().orElseThrow());
        var missing = JsonParser.parseString(snapshot.json()).getAsJsonObject();
        missing.getAsJsonArray("condition_receipts").remove(0);
        assertThrows(IllegalArgumentException.class, () -> WorkstationCheckpointProjectionService.prepareRestorationProjectionFiles(
                context, registry, missing.toString().getBytes(StandardCharsets.UTF_8)));
        System.out.println("IM-033A native restoration: generation=1 tick=100 Workstation entries=1 unloaded=1 receipts=2; "
                + "fault_tick=31 condition_revision=" + settled.revision() + " digest=" + settled.digest() + "; exact bytes, no replay");
    }

    @Test
    void historicalSnapshotRestoresBeforeExplicitHealthyMigrationAndRemainsImmutable() throws Exception {
        var instance = instance("butchercraft:grinder", 1, 0);
        var historical = legacy(instance);
        var registry = new WorkstationInstanceRegistry(1, 2, WORLD, 2, ALLOCATION, List.of(instance));
        var codec = new WorkstationProjectionCodec();
        var snapshot = WorkstationCheckpointProjectionService.capture(registry, List.of(), access(historical, codec));
        byte[] evidence = snapshot.json().getBytes(StandardCharsets.UTF_8);
        var context = context();
        var files = WorkstationCheckpointProjectionService.prepareRestorationProjectionFiles(context, registry, evidence);
        assertEquals(1, files.size());
        var restored = codec.decode(files.getFirst().bytes());
        assertEquals(historical, restored);
        var policies = com.butchercraft.integration.machine.MachineConditionPolicies.standard();
        var successor = ConditionInitialization.candidate(restored, policies, 100, false, true);
        assertEquals(0, successor.condition().orElseThrow().state().orElseThrow().mechanicalLoss());
        assertEquals(successor, ConditionInitialization.candidate(successor, policies, 101, false, true));
        assertArrayEquals(evidence, snapshot.json().getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(codec.freeze(historical), files.getFirst().bytes());
        assertTrue(successor.condition().orElseThrow().state().orElseThrow().initializationEvidence().legacyProjectionDigest().isPresent());
    }

    private OwnerNativeRestorationContext context() {
        var world = new WorldIdentityRootReference(WORLD.identity(), WORLD.schemaVersion(), WORLD.rootDigest());
        var platform = new PlatformDeterminismManifestReference("test:condition_platform", 1, "sha256:" + "4".repeat(64));
        var manifest = new CheckpointGenerationManifest(1, CheckpointGenerationId.of(1, 100), Optional.empty(), Optional.empty(),
                100, List.of(), platform, world, "sha256:" + "0".repeat(64)).withCalculatedDigest();
        return new OwnerNativeRestorationContext(directory.resolve("restored"), directory.resolve("restored/butchercraft"),
                world, manifest, RestorationSource.CHECKPOINT, Optional.empty());
    }

    private static WorkstationCheckpointProjectionService.ProjectionAccess access(DurableWorkstationProjection projection,
            WorkstationProjectionCodec codec) {
        return new WorkstationCheckpointProjectionService.ProjectionAccess() {
            public WorkstationProjectionReadResult read(WorkstationInstanceRecord ignored) { return WorkstationProjectionReadResult.available(projection); }
            public FrozenWorkstationProjectionSnapshot freeze(WorkstationInstanceRecord ignored) {
                return new FrozenWorkstationProjectionSnapshot(projection.instanceId(), projection.projectionRevision(), projection.stateDigest(), codec.freeze(projection));
            }
            public boolean loaded(WorkstationInstanceRecord ignored) { return false; }
        };
    }
}
