package com.butchercraft.network;

import com.butchercraft.ButcherCraft;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeStatusSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record WorldTimeClientSnapshotPayload(WorldTimeClientSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<WorldTimeClientSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "world_time_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldTimeClientSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(WorldTimeClientSnapshotPayload::write, WorldTimeClientSnapshotPayload::read);

    public WorldTimeClientSnapshotPayload {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static WorldTimeClientSnapshotPayload from(WorldTimeStatusSnapshot snapshot) {
        return new WorldTimeClientSnapshotPayload(new WorldTimeClientSnapshot(
                snapshot.schemaVersion(),
                snapshot.scalingEnabled(),
                snapshot.configuredDayLengthMinutes(),
                snapshot.configurationIdentity().value(),
                snapshot.sourceDimensionIdentity(),
                snapshot.gameTime(),
                snapshot.dayTime(),
                snapshot.businessCalendar().businessDayIndex(),
                snapshot.businessCalendar().dayOfWeek(),
                snapshot.businessCalendar().timeOfDay(),
                snapshot.businessCalendar().worldDayIdentity(),
                snapshot.movementClassification(),
                snapshot.externalConflictDetected()
        ));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WorldTimeClientSnapshotPayload payload) {
        WorldTimeClientSnapshot snapshot = payload.snapshot();
        buffer.writeVarInt(snapshot.schemaVersion());
        buffer.writeBoolean(snapshot.scalingEnabled());
        buffer.writeVarInt(snapshot.configuredDayLengthMinutes());
        buffer.writeUtf(snapshot.configurationIdentity());
        buffer.writeUtf(snapshot.sourceDimensionIdentity());
        buffer.writeVarLong(snapshot.gameTime());
        buffer.writeVarLong(snapshot.dayTime());
        buffer.writeVarLong(snapshot.businessDayIndex());
        buffer.writeUtf(snapshot.dayOfWeek().serializedName());
        buffer.writeVarInt(snapshot.timeOfDay().hour());
        buffer.writeVarInt(snapshot.timeOfDay().minute());
        buffer.writeUtf(snapshot.worldDayIdentity());
        buffer.writeUtf(snapshot.movementClassification().serializedName());
        buffer.writeBoolean(snapshot.externalConflictDetected());
    }

    private static WorldTimeClientSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        return new WorldTimeClientSnapshotPayload(new WorldTimeClientSnapshot(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                BusinessDayOfWeek.fromSerializedName(buffer.readUtf()),
                new BusinessTimeOfDay(buffer.readVarInt(), buffer.readVarInt()),
                buffer.readUtf(),
                WorldTimeMovementClassification.fromSerializedName(buffer.readUtf()),
                buffer.readBoolean()
        ));
    }
}
