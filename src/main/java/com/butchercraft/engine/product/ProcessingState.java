package com.butchercraft.engine.product;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Immutable processing-state id for a product snapshot.
 *
 * <p>The engine keeps states open so datapack definitions can introduce states such as
 * forequarter, primal, fat, or bone without adding engine switches. The RAW and PREPARED
 * constants are convenience ids used by existing trim-to-ground tests and fixtures.</p>
 */
public record ProcessingState(EngineId id) {
    public static final ProcessingState RAW = fromId(EngineId.of("butchercraft:trim"));
    public static final ProcessingState PREPARED = fromId(EngineId.of("butchercraft:ground"));

    public ProcessingState {
        Objects.requireNonNull(id, "id");
    }

    public static ProcessingState fromId(EngineId id) {
        return new ProcessingState(id);
    }
}
