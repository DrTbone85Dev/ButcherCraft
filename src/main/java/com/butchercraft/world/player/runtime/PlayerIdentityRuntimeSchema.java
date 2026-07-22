package com.butchercraft.world.player.runtime;

/**
 * Stable runtime persistence contract for player identity records.
 */
public final class PlayerIdentityRuntimeSchema {
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String FILE_NAME = "player_identities.json";

    private PlayerIdentityRuntimeSchema() {
    }
}
