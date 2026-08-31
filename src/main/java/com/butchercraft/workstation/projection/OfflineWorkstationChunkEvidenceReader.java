package com.butchercraft.workstation.projection;

import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads exact saved block-entity evidence without opening a mutable Minecraft level. */
public final class OfflineWorkstationChunkEvidenceReader {
    private static final String ENDPOINT_TAG = "TransferEndpointProjection";
    private static final WorkstationProjectionCodec PROJECTION_CODEC = new WorkstationProjectionCodec();

    public Optional<OfflineWorkstationBlockEntityEvidence> read(
            Path worldRoot,
            WorkstationInstanceRecord instance
    ) {
        Objects.requireNonNull(instance, "instance");
        WorkstationEndpointKey endpoint = instance.endpointKey();
        Path regionDirectory = regionDirectory(worldRoot, endpoint.dimensionIdentity());
        int chunkX = Math.floorDiv(endpoint.x(), 16);
        int chunkZ = Math.floorDiv(endpoint.z(), 16);
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        Path regionPath = regionDirectory.resolve("r." + regionX + "." + regionZ + ".mca")
                .toAbsolutePath().normalize();
        if (!regionPath.startsWith(Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Region path escaped the analyzed world root");
        }
        if (!Files.isRegularFile(regionPath)) return Optional.empty();

        CompoundTag chunk = readChunk(regionPath, regionDirectory, endpoint.dimensionIdentity(), chunkX, chunkZ);
        if (chunk == null) return Optional.empty();
        List<CompoundTag> matches = new ArrayList<>();
        ListTag blockEntities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < blockEntities.size(); index++) {
            CompoundTag candidate = blockEntities.getCompound(index);
            if (candidate.getInt("x") == endpoint.x()
                    && candidate.getInt("y") == endpoint.y()
                    && candidate.getInt("z") == endpoint.z()) {
                matches.add(candidate.copy());
            }
        }
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Multiple block entities occupy the exact Workstation endpoint");
        }
        CompoundTag blockEntity = matches.getFirst();
        CompoundTag endpointTag = blockEntity.getCompound(ENDPOINT_TAG);
        if (!endpointTag.contains("InstanceIdentity", Tag.TAG_STRING)
                || !endpointTag.contains("InstanceGeneration", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Physical Workstation evidence omits exact instance identity");
        }
        WorkstationInstanceId physicalId = new WorkstationInstanceId(endpointTag.getString("InstanceIdentity"));
        long generation = endpointTag.getLong("InstanceGeneration");
        CompoundTag projection = blockEntity.copy();
        projection.remove("id");
        projection.remove("x");
        projection.remove("y");
        projection.remove("z");
        projection.remove("keepPacked");
        projection.remove("DurableProjectionReference");
        String canonical = PROJECTION_CODEC.encodeBlockEntityProjection(blockEntity);
        return Optional.of(new OfflineWorkstationBlockEntityEvidence(
                physicalId,
                generation,
                endpoint,
                blockEntity.getString("id"),
                chunkX,
                chunkZ,
                regionPath,
                CheckpointSnapshotDigest.sha256(canonical.getBytes(StandardCharsets.UTF_8)),
                blockEntity,
                projection
        ));
    }

    private static CompoundTag readChunk(
            Path regionPath,
            Path regionDirectory,
            String dimensionIdentity,
            int chunkX,
            int chunkZ
    ) {
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionIdentity);
        if (dimension == null) throw new IllegalArgumentException("Invalid Workstation dimension identity");
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimension);
        RegionStorageInfo info = new RegionStorageInfo("butchercraft_legacy_workstation_projection", dimensionKey, "chunk");
        try (RegionFile region = new RegionFile(info, regionPath, regionDirectory, true);
             DataInputStream input = region.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ))) {
            return input == null ? null : NbtIo.read(input, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Saved Workstation chunk evidence could not be read", exception);
        }
    }

    private static Path regionDirectory(Path worldRoot, String dimensionIdentity) {
        Path root = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        return switch (dimensionIdentity) {
            case "minecraft:overworld" -> root.resolve("region");
            case "minecraft:the_nether" -> root.resolve("DIM-1").resolve("region");
            case "minecraft:the_end" -> root.resolve("DIM1").resolve("region");
            default -> throw new IllegalArgumentException(
                    "Unsupported offline Workstation dimension layout: " + dimensionIdentity);
        };
    }
}
