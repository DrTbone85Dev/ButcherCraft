package com.butchercraft.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("world_time_v1").optional();
        registrar.playToClient(
                WorldTimeClientSnapshotPayload.TYPE,
                WorldTimeClientSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> WorldTimeClientState.accept(payload.snapshot()))
        );
    }
}
