package com.butchercraft.workstation.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.WorkstationProjectionCodec;
import com.butchercraft.workstation.projection.WorkstationProjectionSlot;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationCheckpointCompletenessTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world/root/checkpoint-test", 1, "sha256:" + "7".repeat(64));
    private static final String INSTANCE_CONFIGURATION =
            "butchercraft:workstation_instance_configuration/v1/checkpoint-test";

    @Test
    void activeAndRecoveryInstancesAreRequiredWhileUnreferencedRetiredIsExcluded() {
        WorkstationInstanceRecord active = instance(1, 1, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord recovery = instance(2, 2, WorkstationInstanceLifecycle.RECOVERY_REQUIRED);
        WorkstationInstanceRecord retired = instance(3, 3, WorkstationInstanceLifecycle.RETIRED);
        WorkstationInstanceRegistry registry = registry(List.of(retired, recovery, active));

        assertEquals(List.of(active.instanceId(), recovery.instanceId()).stream().sorted().toList(),
                WorkstationCheckpointProjectionService.requiredInstanceIds(registry, List.of()));
    }

    @Test
    void referencedRetiredInstanceParticipatesWithoutSubstitutingReplacement() {
        WorkstationInstanceRecord retired = instance(1, 4, WorkstationInstanceLifecycle.RETIRED);
        WorkstationInstanceRecord replacement = instance(2, 4, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationCheckpointDependency dependency = new WorkstationCheckpointDependency(
                retired.instanceId(), WorkstationCheckpointDependencyCategory.MATERIAL_HANDLING_SOURCE,
                "butchercraft:material_transfer/v2/test");

        assertEquals(List.of(retired.instanceId(), replacement.instanceId()).stream().sorted().toList(),
                WorkstationCheckpointProjectionService.requiredInstanceIds(
                        registry(List.of(replacement, retired)), List.of(dependency)));
    }

    @Test
    void dependencyClosureIsCanonicalForOneThousandRecords() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<WorkstationInstanceRecord> records = new ArrayList<>();
            List<WorkstationCheckpointDependency> dependencies = new ArrayList<>();
            for (int index = 0; index < 1_000; index++) {
                WorkstationInstanceLifecycle lifecycle = index < 661
                        ? WorkstationInstanceLifecycle.ACTIVE : WorkstationInstanceLifecycle.RETIRED;
                WorkstationInstanceRecord instance = instance(index + 1L, index, lifecycle);
                records.add(instance);
                if (index >= 661 && index % 3 == 0) {
                    dependencies.add(new WorkstationCheckpointDependency(
                            instance.instanceId(), WorkstationCheckpointDependencyCategory.EXECUTION_OPERATION,
                            "butchercraft:execution_operation/v1/" + index));
                }
            }
            WorkstationInstanceRegistry registry = registry(records);
            List<?> first = WorkstationCheckpointProjectionService.requiredInstanceIds(registry, dependencies);
            Collections.reverse(records);
            Collections.reverse(dependencies);
            List<?> second = WorkstationCheckpointProjectionService.requiredInstanceIds(registry(records), dependencies);

            assertEquals(774, first.size());
            assertEquals(first, second);
        });
    }

    @Test
    void thousandProjectionParticipantIncludesSixHundredSixtyOneUnloadedWithoutLoading() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            List<WorkstationInstanceRecord> records = new ArrayList<>();
            Map<String, DurableWorkstationProjection> projections = new HashMap<>();
            Map<String, byte[]> frozen = new HashMap<>();
            WorkstationProjectionCodec codec = new WorkstationProjectionCodec();
            for (int index = 0; index < 1_000; index++) {
                WorkstationInstanceRecord instance = instance(
                        index + 1L, index, WorkstationInstanceLifecycle.ACTIVE);
                DurableWorkstationProjection projection = projection(instance);
                records.add(instance);
                projections.put(instance.instanceId().value(), projection);
                frozen.put(instance.instanceId().value(), codec.freeze(projection));
            }
            long started = System.nanoTime();
            WorkstationCheckpointProjectionSnapshot snapshot = WorkstationCheckpointProjectionService.capture(
                    registry(records),
                    List.of(),
                    new WorkstationCheckpointProjectionService.ProjectionAccess() {
                        @Override
                        public com.butchercraft.workstation.projection.WorkstationProjectionReadResult read(
                                WorkstationInstanceRecord instance
                        ) {
                            return com.butchercraft.workstation.projection.WorkstationProjectionReadResult.available(
                                    projections.get(instance.instanceId().value()));
                        }

                        @Override
                        public com.butchercraft.workstation.projection.FrozenWorkstationProjectionSnapshot freeze(
                                WorkstationInstanceRecord instance
                        ) {
                            DurableWorkstationProjection projection = projections.get(instance.instanceId().value());
                            return new com.butchercraft.workstation.projection.FrozenWorkstationProjectionSnapshot(
                                    instance.instanceId(), projection.projectionRevision(), projection.stateDigest(),
                                    frozen.get(instance.instanceId().value()));
                        }

                        @Override
                        public boolean loaded(WorkstationInstanceRecord instance) {
                            return instance.endpointKey().x() < 339;
                        }
                    });
            long elapsed = System.nanoTime() - started;

            assertTrue(snapshot.restorable());
            assertEquals(1_000, snapshot.requiredProjectionCount());
            assertEquals(1_000, snapshot.availableProjectionCount());
            assertEquals(339, snapshot.loadedProjectionCount());
            assertEquals(661, snapshot.unloadedProjectionCount());
            assertTrue(snapshot.participantBytes() > 1_000_000L);
            System.out.printf("R3B scale: required=%d available=%d loaded=%d unloaded=%d bytes=%d "
                            + "requiredMs=%.3f freezeMs=%.3f serializeMs=%.3f totalMs=%.3f chunkLoads=0%n",
                    snapshot.requiredProjectionCount(), snapshot.availableProjectionCount(),
                    snapshot.loadedProjectionCount(), snapshot.unloadedProjectionCount(), snapshot.participantBytes(),
                    snapshot.requiredSetDurationNanos() / 1_000_000.0,
                    snapshot.projectionFreezeDurationNanos() / 1_000_000.0,
                    snapshot.serializationDurationNanos() / 1_000_000.0,
                    elapsed / 1_000_000.0);
        });
    }

    @Test
    void currentSelfContainedSnapshotIsRestorableAndPayloadTamperingFailsClosed() {
        WorkstationInstanceRecord active = instance(1, 9, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRegistry registry = registry(List.of(active));
        DurableWorkstationProjection projection = projection(active);
        byte[] payload = new WorkstationProjectionCodec().freeze(projection);
        JsonObject current = currentSnapshot(active, payload, projection);
        CheckpointOwnerFileSnapshot valid = ownerSnapshot(registry, json(current));

        WorkstationCheckpointRestorabilityReport accepted = WorkstationCheckpointRestorabilityVerifier.verify(valid);
        assertTrue(accepted.restorable());
        assertEquals(1, accepted.requiredProjectionCount());
        assertEquals(1, accepted.unloadedProjectionCount());

        current.getAsJsonArray("required_projections").get(0).getAsJsonObject()
                .addProperty("payload_base64", Base64.getEncoder().encodeToString("tampered".getBytes()));
        WorkstationCheckpointRestorabilityReport rejected = WorkstationCheckpointRestorabilityVerifier.verify(
                ownerSnapshot(registry, json(current)));
        assertFalse(rejected.restorable());
    }

    @Test
    void legacyChunkUnloadedEvidenceIsExplicitlyNonRestorable() {
        WorkstationInstanceRecord active = instance(1, 10, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRegistry registry = registry(List.of(active));
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", WorkstationCheckpointProjectionService.LEGACY_SCHEMA_VERSION);
        root.addProperty("workstation_instance_registry_revision", registry.ownerRevision());
        root.add("loaded_projections", new JsonArray());
        JsonArray unavailable = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("instance_id", active.instanceId().value());
        entry.addProperty("reason", "chunk_unloaded");
        unavailable.add(entry);
        root.add("unavailable_projections", unavailable);

        WorkstationCheckpointRestorabilityReport report = WorkstationCheckpointRestorabilityVerifier.verify(
                ownerSnapshot(registry, json(root)));
        assertEquals(WorkstationCheckpointCompletenessStatus
                .NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE, report.status());
        assertTrue(report.blockers().getFirst().contains("chunk_unloaded"));
    }

    @Test
    void liveCapturePathContainsNoChunkForceLoad() throws Exception {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/checkpoint/WorkstationCheckpointProjectionService.java"));
        String captureSection = source.substring(source.indexOf("public static WorkstationCheckpointProjectionSnapshot capture"),
                source.indexOf("public static void restore"));
        assertFalse(captureSection.contains("getChunk("));
        assertTrue(captureSection.contains("hasChunkAt("));
        assertFalse(captureSection.contains("chunk_unloaded"));
    }

    private static JsonObject currentSnapshot(
            WorkstationInstanceRecord instance,
            byte[] payload,
            DurableWorkstationProjection projection
    ) {
        JsonObject entry = new JsonObject();
        entry.addProperty("instance_id", instance.instanceId().value());
        entry.addProperty("projection_schema", projection.schemaVersion());
        entry.addProperty("projection_revision", projection.projectionRevision());
        entry.addProperty("projection_state_digest", projection.stateDigest());
        entry.addProperty("lifecycle", instance.lifecycle().name());
        entry.addProperty("payload_length", payload.length);
        entry.addProperty("payload_digest", CheckpointSnapshotDigest.sha256(payload));
        entry.addProperty("payload_base64", Base64.getEncoder().encodeToString(payload));
        entry.addProperty("loaded_at_freeze", false);
        entry.add("required_by", new JsonArray());
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", WorkstationCheckpointProjectionService.SCHEMA_VERSION);
        root.addProperty("workstation_restorable_status", "complete_restorable");
        root.addProperty("workstation_instance_registry_revision", instance.lastUpdateRevision());
        root.addProperty("required_projection_count", 1);
        root.addProperty("available_projection_count", 1);
        root.addProperty("loaded_projection_count", 0);
        root.addProperty("unloaded_projection_count", 1);
        root.addProperty("projection_collection_digest", CheckpointSnapshotDigest.sha256(
                StrictJsonPersistence.gson().toJson(entries).getBytes(StandardCharsets.UTF_8)));
        root.add("required_projections", entries);
        root.add("blockers", new JsonArray());
        return root;
    }

    private static CheckpointOwnerFileSnapshot ownerSnapshot(
            WorkstationInstanceRegistry registry,
            String projectionJson
    ) {
        return new CheckpointOwnerFileSnapshot(
                LegacySplitRecoveryParticipants.WORKSTATION, 3, registry.ownerRevision(), false,
                List.of(
                        new CheckpointOwnerFileSnapshot.FilePayload(
                                "workstation_instances.json",
                                new WorkstationInstanceStorage(Path.of("unused")).serialize(registry)
                                        .getBytes(StandardCharsets.UTF_8)),
                        new CheckpointOwnerFileSnapshot.FilePayload(
                                "workstation_projections.json", projectionJson.getBytes(StandardCharsets.UTF_8))
                ));
    }

    private static String json(JsonObject object) {
        return StrictJsonPersistence.gson().toJson(object) + "\n";
    }

    private static DurableWorkstationProjection projection(WorkstationInstanceRecord instance) {
        return DurableWorkstationProjection.active(
                WORLD, instance.instanceId(), instance.endpointKey(), instance.generation(),
                INSTANCE_CONFIGURATION, "butchercraft:grinder", instance.lastUpdateRevision(),
                1L, 0L, 0L, 0L,
                "butchercraft:workstation_slot_capacity/v1/checkpoint-test",
                List.of(new WorkstationProjectionSlot(0, 64, 64, Optional.empty())),
                new WorkstationProjectionCodec().encodeBlockEntityProjection(
                        new net.minecraft.nbt.CompoundTag()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private static WorkstationInstanceRegistry registry(List<WorkstationInstanceRecord> records) {
        long next = records.stream().mapToLong(WorkstationInstanceRecord::generation).max().orElse(0L) + 1L;
        long ownerRevision = records.stream().mapToLong(WorkstationInstanceRecord::lastUpdateRevision)
                .max().orElse(0L);
        return new WorkstationInstanceRegistry(
                1, ownerRevision, WORLD, next, INSTANCE_CONFIGURATION, records);
    }

    private static WorkstationInstanceRecord instance(
            long generation,
            int x,
            WorkstationInstanceLifecycle lifecycle
    ) {
        WorkstationInstanceRecord pending = WorkstationInstanceRecord.pending(
                WORLD,
                new WorkstationEndpointKey("butchercraft:grinder", "minecraft:overworld", x, 64, 0),
                generation,
                INSTANCE_CONFIGURATION,
                1L);
        if (lifecycle == WorkstationInstanceLifecycle.PENDING_BINDING) return pending;
        if (lifecycle == WorkstationInstanceLifecycle.RETIRED) {
            WorkstationInstanceRecord active = pending.transition(
                    WorkstationInstanceLifecycle.ACTIVE, 2L, Optional.empty(), List.of());
            return active.transition(
                    WorkstationInstanceLifecycle.RETIRED, 3L, Optional.of("retired"), List.of());
        }
        Optional<String> reason = lifecycle == WorkstationInstanceLifecycle.ACTIVE
                ? Optional.empty() : Optional.of(lifecycle.name().toLowerCase());
        return pending.transition(lifecycle, 2L, reason, List.of());
    }
}
