package com.butchercraft.world.materialhandling;

public final class MaterialHandlingSchema {
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int STACK_AWARE_SCHEMA_VERSION = 2;

    /** Active exact-one runtime remains schema 1 until IM-030B activation. */
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String FILE_NAME = "material_handling.json";

    private MaterialHandlingSchema() {
    }
}
