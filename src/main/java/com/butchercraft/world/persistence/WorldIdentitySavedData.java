package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;

public final class WorldIdentitySavedData extends SavedData {
    public static final String DATA_NAME = "butchercraft_world_identity";

    private final WorldIdentity identity;

    public WorldIdentitySavedData(WorldIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    public static WorldIdentitySavedData getOrCreate(ServerLevel overworld, WorldIdentityGenerator generator) {
        Objects.requireNonNull(overworld, "overworld");
        Objects.requireNonNull(generator, "generator");
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> generated(generator.generate(overworld.getSeed())),
                        WorldIdentitySavedData::load
                ),
                DATA_NAME
        );
    }

    public static WorldIdentitySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldIdentitySavedData savedData = new WorldIdentitySavedData(WorldIdentityNbtSerializer.load(tag));
        if (WorldIdentityNbtSerializer.requiresMigration(tag)) {
            savedData.setDirty();
        }
        return savedData;
    }

    public static WorldIdentitySavedData generated(WorldIdentity identity) {
        WorldIdentitySavedData savedData = new WorldIdentitySavedData(identity);
        savedData.setDirty();
        return savedData;
    }

    public WorldIdentity identity() {
        return identity;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(WorldIdentityNbtSerializer.save(identity));
        return tag;
    }
}
