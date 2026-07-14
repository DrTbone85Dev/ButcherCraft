package com.butchercraft.engine.product;

/**
 * Coarse processing state for an immutable product snapshot.
 *
 * <p>The engine only needs enough state to validate narrow operations. Minecraft items, blocks,
 * stations, and recipes can translate their richer state into these values at the integration
 * boundary.</p>
 */
public enum ProcessingState {
    RAW,
    PREPARED
}
