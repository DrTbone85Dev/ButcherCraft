package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import net.minecraft.nbt.CompoundTag;

import java.nio.file.Path;
import java.util.Objects;

public record OfflineWorkstationBlockEntityEvidence(
        WorkstationInstanceId instanceId,
        long instanceGeneration,
        WorkstationEndpointKey endpointKey,
        String blockEntityTypeIdentity,
        int chunkX,
        int chunkZ,
        Path regionFile,
        String physicalEvidenceDigest,
        CompoundTag blockEntityNbt,
        CompoundTag projectionNbt
) {
    public OfflineWorkstationBlockEntityEvidence {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        if (instanceGeneration <= 0L) throw new IllegalArgumentException("Instance generation must be positive");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        blockEntityTypeIdentity = requireText(blockEntityTypeIdentity, "blockEntityTypeIdentity");
        regionFile = Objects.requireNonNull(regionFile, "regionFile").toAbsolutePath().normalize();
        physicalEvidenceDigest = requireText(physicalEvidenceDigest, "physicalEvidenceDigest");
        blockEntityNbt = Objects.requireNonNull(blockEntityNbt, "blockEntityNbt").copy();
        projectionNbt = Objects.requireNonNull(projectionNbt, "projectionNbt").copy();
    }

    @Override
    public CompoundTag blockEntityNbt() {
        return blockEntityNbt.copy();
    }

    @Override
    public CompoundTag projectionNbt() {
        return projectionNbt.copy();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
