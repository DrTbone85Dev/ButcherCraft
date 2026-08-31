package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineWorkstationChunkEvidenceReaderTest {
    private static final String CONFIGURATION =
            "butchercraft:workstation_instance_configuration/v1/offline-evidence-test";
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world_identity/offline_evidence_test", 1, "sha256:" + "4".repeat(64));

    @TempDir
    private Path temporaryDirectory;

    @Test
    void exactSavedBlockEntityMatchesIdentityAndIsCopyPathIndependent() throws Exception {
        WorkstationInstanceRecord instance = instance(-3, -60, -3, 5L);
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("renamed-copy");
        writeChunk(first, List.of(blockEntity(instance, instance.instanceId().value())));
        Files.createDirectories(second.resolve("region"));
        Files.copy(first.resolve("region/r.-1.-1.mca"), second.resolve("region/r.-1.-1.mca"));

        OfflineWorkstationBlockEntityEvidence firstEvidence = new OfflineWorkstationChunkEvidenceReader()
                .read(first, instance).orElseThrow();
        OfflineWorkstationBlockEntityEvidence secondEvidence = new OfflineWorkstationChunkEvidenceReader()
                .read(second, instance).orElseThrow();

        assertEquals(instance.instanceId(), firstEvidence.instanceId());
        assertEquals(instance.generation(), firstEvidence.instanceGeneration());
        assertEquals(instance.endpointKey(), firstEvidence.endpointKey());
        assertEquals(firstEvidence.physicalEvidenceDigest(), secondEvidence.physicalEvidenceDigest());
        assertFalse(firstEvidence.projectionNbt().contains("x"));
        assertFalse(firstEvidence.projectionNbt().contains("DurableProjectionReference"));
    }

    @Test
    void physicalIdentityConflictIsExposedWithoutInference() throws Exception {
        WorkstationInstanceRecord expected = instance(2, 70, 2, 1L);
        WorkstationInstanceRecord replacement = instance(2, 70, 2, 2L);
        writeChunk(temporaryDirectory, List.of(blockEntity(expected, replacement.instanceId().value())));

        OfflineWorkstationBlockEntityEvidence evidence = new OfflineWorkstationChunkEvidenceReader()
                .read(temporaryDirectory, expected).orElseThrow();

        assertEquals(replacement.instanceId(), evidence.instanceId());
        assertFalse(expected.instanceId().equals(evidence.instanceId()));
    }

    @Test
    void missingExactPositionReturnsNoEvidenceAndDuplicatePositionFailsClosed() throws Exception {
        WorkstationInstanceRecord expected = instance(3, 70, 3, 1L);
        WorkstationInstanceRecord elsewhere = instance(4, 70, 3, 2L);
        writeChunk(temporaryDirectory, List.of(blockEntity(elsewhere, elsewhere.instanceId().value())));
        assertTrue(new OfflineWorkstationChunkEvidenceReader().read(temporaryDirectory, expected).isEmpty());

        Path duplicateRoot = temporaryDirectory.resolve("duplicate");
        CompoundTag exact = blockEntity(expected, expected.instanceId().value());
        writeChunk(duplicateRoot, List.of(exact, exact.copy()));
        assertThrows(IllegalArgumentException.class,
                () -> new OfflineWorkstationChunkEvidenceReader().read(duplicateRoot, expected));
    }

    @Test
    void missingPhysicalIdentityFailsClosed() throws Exception {
        WorkstationInstanceRecord expected = instance(5, 70, 5, 1L);
        CompoundTag blockEntity = blockEntity(expected, expected.instanceId().value());
        blockEntity.getCompound("TransferEndpointProjection").remove("InstanceIdentity");
        writeChunk(temporaryDirectory, List.of(blockEntity));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OfflineWorkstationChunkEvidenceReader().read(temporaryDirectory, expected));
        assertTrue(failure.getMessage().contains("omits exact instance identity"));
    }

    private WorkstationInstanceRecord instance(int x, int y, int z, long generation) {
        WorkstationEndpointKey endpoint = new WorkstationEndpointKey(
                "butchercraft:grinder", "minecraft:overworld", x, y, z);
        return WorkstationInstanceRecord.pending(WORLD, endpoint, generation, CONFIGURATION, generation)
                .transition(WorkstationInstanceLifecycle.ACTIVE, generation + 10L, Optional.empty(), List.of());
    }

    private CompoundTag blockEntity(WorkstationInstanceRecord instance, String physicalIdentity) {
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", instance.endpointKey().workstationTypeIdentity());
        blockEntity.putInt("x", instance.endpointKey().x());
        blockEntity.putInt("y", instance.endpointKey().y());
        blockEntity.putInt("z", instance.endpointKey().z());
        CompoundTag endpoint = new CompoundTag();
        endpoint.putString("InstanceIdentity", physicalIdentity);
        endpoint.putLong("InstanceGeneration", instance.generation());
        blockEntity.put("TransferEndpointProjection", endpoint);
        blockEntity.putString("DurableProjectionReference", "must_not_be_projected");
        return blockEntity;
    }

    private void writeChunk(Path world, List<CompoundTag> blockEntities) throws Exception {
        int chunkX = Math.floorDiv(blockEntities.getFirst().getInt("x"), 16);
        int chunkZ = Math.floorDiv(blockEntities.getFirst().getInt("z"), 16);
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        Path directory = world.resolve("region");
        Files.createDirectories(directory);
        Path regionPath = directory.resolve("r." + regionX + "." + regionZ + ".mca");
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
        RegionStorageInfo info = new RegionStorageInfo("r3c_offline_test", dimension, "chunk");
        CompoundTag chunk = new CompoundTag();
        ListTag values = new ListTag();
        blockEntities.forEach(value -> values.add(value.copy()));
        chunk.put("block_entities", values);
        try (RegionFile region = new RegionFile(info, regionPath, directory, true);
             DataOutputStream output = region.getChunkDataOutputStream(new ChunkPos(chunkX, chunkZ))) {
            NbtIo.write(chunk, output);
        }
    }
}
