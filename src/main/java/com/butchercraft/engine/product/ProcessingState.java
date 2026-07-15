package com.butchercraft.engine.product;

import com.butchercraft.engine.EngineId;

import java.util.Arrays;

/**
 * Coarse processing state for an immutable product snapshot.
 *
 * <p>The engine only needs enough state to validate narrow operations. Minecraft items, blocks,
 * stations, and recipes can translate their richer state into these values at the integration
 * boundary.</p>
 */
public enum ProcessingState {
    RAW("butchercraft:trim"),
    PREPARED("butchercraft:ground");

    private final EngineId id;

    ProcessingState(String id) {
        this.id = EngineId.of(id);
    }

    public EngineId id() {
        return id;
    }

    public static ProcessingState fromId(EngineId id) {
        return Arrays.stream(values())
                .filter(state -> state.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported processing state id: " + id));
    }
}
