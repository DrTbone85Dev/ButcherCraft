package com.butchercraft.integration.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.WorkstationProjectionCodec;
import com.butchercraft.workstation.projection.WorkstationProjectionSlot;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointGenerationManifest;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.CheckpointPublicationOutcome;
import com.butchercraft.world.checkpoint.CheckpointPublicationRequest;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.CheckpointSnapshotParticipation;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.OwnerSnapshotDescriptor;
import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupCheckpointCandidateSelectorTest {
    private static final WorldIdentityRootReference WORLD = new WorldIdentityRootReference(
            "butchercraft:world/root/startup-selector", 1, digest("world"));
    private static final WorldIdentityRootIdentity WORKSTATION_WORLD = new WorldIdentityRootIdentity(
            WORLD.identity(), WORLD.schemaVersion(), WORLD.rootDigest());
    private static final PlatformDeterminismManifestReference PLATFORM =
            new PlatformDeterminismManifestReference(
                    "butchercraft:platform_determinism/startup-selector", 1, digest("platform"));
    private static final String INSTANCE_CONFIGURATION =
            "butchercraft:workstation_instance_configuration/v1/startup-selector";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void historicalIncompleteGenerationIsRejectedAndCompleteSuccessorIsAccepted() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("successor"));
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty(), false));
        CheckpointGenerationManifest second = publish(store, request(2L, 25L, Optional.of(first), true));

        var firstRecovered = store.loadCommittedGenerationReadOnly(
                recoveryRequest(), first.generationId(), first.manifestDigest()).recoveredGeneration().orElseThrow();
        assertFalse(WorkstationCheckpointRestorabilityVerifier.verify(firstRecovered).restorable());

        StartupCheckpointCandidateSelection selection =
                new StartupCheckpointCandidateSelector().select(store, recoveryRequest());

        assertTrue(selection.successful());
        assertEquals(second.generationId(), selection.generation().orElseThrow().manifest().generationId());
        assertTrue(selection.previousValidGeneration().isEmpty());
        assertTrue(selection.workstationRestorability().orElseThrow().restorable());
    }

    @Test
    void reportsOnlyACompleteRestorablePredecessorAsPreviousValidGeneration() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(
                temporaryDirectory.resolve("previous-valid"));
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty(), true));
        CheckpointGenerationManifest second = publish(store, request(2L, 40L, Optional.of(first), true));

        StartupCheckpointCandidateSelection selection =
                new StartupCheckpointCandidateSelector().select(store, recoveryRequest());

        assertEquals(second.generationId(), selection.generation().orElseThrow().manifest().generationId());
        assertEquals(Optional.of(first.generationId()), selection.previousValidGeneration());
    }

    @Test
    void newerWorkstationIncompleteHeadFallsBackToPriorCompleteHead() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("fallback"));
        CheckpointGenerationManifest first = publish(store, request(1L, 25L, Optional.empty(), false));
        CheckpointGenerationManifest second = publish(store, request(2L, 40L, Optional.of(first), true));
        CheckpointGenerationManifest third = publish(store, request(3L, 55L, Optional.of(second), false));

        StartupCheckpointCandidateSelection selection =
                new StartupCheckpointCandidateSelector().select(store, recoveryRequest());

        assertTrue(selection.successful());
        assertEquals(second.generationId(), selection.generation().orElseThrow().manifest().generationId());
        assertTrue(selection.rejectedCandidates().stream()
                .anyMatch(value -> value.contains(third.generationId().canonicalValue())
                        && value.contains("Workstation restorability")));
    }

    @Test
    void onlyWorkstationIncompleteGenerationFailsWithTypedReason() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("blocked"));
        publish(store, request(1L, 25L, Optional.empty(), false));

        StartupCheckpointCandidateSelection selection =
                new StartupCheckpointCandidateSelector().select(store, recoveryRequest());

        assertFalse(selection.successful());
        assertEquals(StartupRecoveryFailureCode.WORKSTATION_PROJECTION_INCOMPLETE,
                selection.failureCode());
    }

    private static CheckpointGenerationManifest publish(
            CheckpointFilesystemStore store,
            CheckpointPublicationRequest request
    ) {
        var report = store.publish(request);
        assertEquals(CheckpointPublicationOutcome.PUBLISHED, report.outcome());
        return report.generationManifest().orElseThrow();
    }

    private static CheckpointPublicationRequest request(
            long sequence,
            long tick,
            Optional<CheckpointGenerationManifest> predecessor,
            boolean restorable
    ) {
        CheckpointGenerationId generation = CheckpointGenerationId.of(sequence, tick);
        WorkstationInstanceRecord instance = activeInstance();
        WorkstationInstanceRegistry instances = new WorkstationInstanceRegistry(
                1, instance.lastUpdateRevision(), WORKSTATION_WORLD, 2L,
                INSTANCE_CONFIGURATION, List.of(instance));
        List<CheckpointOwnerSnapshotPayload> snapshots = LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.stream()
                .map(owner -> owner.equals(LegacySplitRecoveryParticipants.WORKSTATION)
                        ? workstationSnapshot(generation, tick, owner, instances, instance, restorable)
                        : opaqueSnapshot(generation, tick, owner))
                .toList();
        return new CheckpointPublicationRequest(
                generation,
                predecessor.map(CheckpointGenerationManifest::generationId),
                predecessor.map(CheckpointGenerationManifest::manifestDigest),
                tick,
                snapshots,
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                PLATFORM,
                WORLD
        );
    }

    private static CheckpointOwnerSnapshotPayload workstationSnapshot(
            CheckpointGenerationId generation,
            long tick,
            CheckpointOwnerId owner,
            WorkstationInstanceRegistry instances,
            WorkstationInstanceRecord instance,
            boolean restorable
    ) {
        byte[] projection = restorable
                ? completeProjection(instance)
                : incompleteProjection(instance, instances.ownerRevision());
        CheckpointOwnerFileSnapshot bundle = new CheckpointOwnerFileSnapshot(
                owner,
                3,
                generation.committedSequence(),
                false,
                List.of(
                        new CheckpointOwnerFileSnapshot.FilePayload(
                                "workstation_instances.json",
                                new WorkstationInstanceStorage(Path.of("unused"))
                                        .serialize(instances).getBytes(StandardCharsets.UTF_8)),
                        new CheckpointOwnerFileSnapshot.FilePayload(
                                "workstation_projections.json", projection)
                )
        );
        byte[] payload = CheckpointOwnerFileBundleCodec.encode(bundle);
        return payload(generation, tick, owner, payload);
    }

    private static CheckpointOwnerSnapshotPayload opaqueSnapshot(
            CheckpointGenerationId generation,
            long tick,
            CheckpointOwnerId owner
    ) {
        return payload(generation, tick, owner,
                (owner.value() + ":" + generation.canonicalValue()).getBytes(StandardCharsets.UTF_8));
    }

    private static CheckpointOwnerSnapshotPayload payload(
            CheckpointGenerationId generation,
            long tick,
            CheckpointOwnerId owner,
            byte[] bytes
    ) {
        String contentDigest = CheckpointSnapshotDigest.sha256(bytes);
        String suffix = contentDigest.substring("sha256:".length());
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                owner,
                1,
                "butchercraft:test_snapshot/" + suffix,
                contentDigest,
                CheckpointSnapshotParticipation.REQUIRED,
                "butchercraft:test_configuration/" + owner.value().substring(owner.value().indexOf(':') + 1),
                WORLD,
                generation,
                tick,
                generation.committedSequence()
        );
        return CheckpointOwnerSnapshotPayload.of(descriptor, bytes);
    }

    private static byte[] incompleteProjection(WorkstationInstanceRecord instance, long revision) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", WorkstationCheckpointProjectionService.LEGACY_SCHEMA_VERSION);
        root.addProperty("workstation_instance_registry_revision", revision);
        root.add("loaded_projections", new JsonArray());
        JsonArray unavailable = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("instance_id", instance.instanceId().value());
        entry.addProperty("reason", "chunk_unloaded");
        unavailable.add(entry);
        root.add("unavailable_projections", unavailable);
        return json(root);
    }

    private static byte[] completeProjection(WorkstationInstanceRecord instance) {
        WorkstationProjectionCodec codec = new WorkstationProjectionCodec();
        DurableWorkstationProjection projection = DurableWorkstationProjection.active(
                WORKSTATION_WORLD,
                instance.instanceId(),
                instance.endpointKey(),
                instance.generation(),
                INSTANCE_CONFIGURATION,
                "butchercraft:grinder",
                instance.lastUpdateRevision(),
                1L, 0L, 0L, 0L,
                "butchercraft:workstation_slot_capacity/v1/startup-selector",
                List.of(new WorkstationProjectionSlot(0, 64, 64, Optional.empty())),
                codec.encodeBlockEntityProjection(new CompoundTag()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty()
        );
        byte[] frozen = codec.freeze(projection);
        JsonObject entry = new JsonObject();
        entry.addProperty("instance_id", instance.instanceId().value());
        entry.addProperty("instance_generation", instance.generation());
        entry.addProperty("workstation_type", instance.endpointKey().workstationTypeIdentity());
        entry.addProperty("dimension", instance.endpointKey().dimensionIdentity());
        entry.addProperty("x", instance.endpointKey().x());
        entry.addProperty("y", instance.endpointKey().y());
        entry.addProperty("z", instance.endpointKey().z());
        entry.addProperty("lifecycle", instance.lifecycle().name());
        entry.addProperty("projection_schema", projection.schemaVersion());
        entry.addProperty("projection_revision", projection.projectionRevision());
        entry.addProperty("projection_state_digest", projection.stateDigest());
        entry.addProperty("payload_length", frozen.length);
        entry.addProperty("payload_digest", CheckpointSnapshotDigest.sha256(frozen));
        entry.addProperty("payload_base64", Base64.getEncoder().encodeToString(frozen));
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
        return json(root);
    }

    private static WorkstationInstanceRecord activeInstance() {
        return WorkstationInstanceRecord.pending(
                        WORKSTATION_WORLD,
                        new WorkstationEndpointKey(
                                "butchercraft:grinder", "minecraft:overworld", 4, 64, 8),
                        1L,
                        INSTANCE_CONFIGURATION,
                        1L)
                .transition(WorkstationInstanceLifecycle.ACTIVE, 2L, Optional.empty(), List.of());
    }

    private static byte[] json(JsonObject object) {
        return (StrictJsonPersistence.gson().toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static CheckpointFilesystemRecoveryRequest recoveryRequest() {
        return new CheckpointFilesystemRecoveryRequest(
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS, WORLD, PLATFORM);
    }

    private static String digest(String value) {
        return CheckpointSnapshotDigest.sha256(
                ("butchercraft:test/startup_selector:" + value).getBytes(StandardCharsets.UTF_8));
    }
}
