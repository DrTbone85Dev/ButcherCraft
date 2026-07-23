package com.butchercraft.world.allocation;

import java.util.List;

public final class ResourceCategories {
    public static final ResourceCategory WORKFORCE = ResourceCategory.of("butchercraft:workforce");
    public static final ResourceCategory STORAGE = ResourceCategory.of("butchercraft:storage");
    public static final ResourceCategory PRODUCTION = ResourceCategory.of("butchercraft:production");
    public static final ResourceCategory TRANSPORT = ResourceCategory.of("butchercraft:transport");
    public static final ResourceCategory UTILITY = ResourceCategory.of("butchercraft:utility");
    public static final ResourceCategory INSPECTION = ResourceCategory.of("butchercraft:inspection");
    public static final ResourceCategory INFRASTRUCTURE = ResourceCategory.of("butchercraft:infrastructure");

    private ResourceCategories() {
    }

    public static List<ResourceCategory> schemaOne() {
        return List.of(
                WORKFORCE,
                STORAGE,
                PRODUCTION,
                TRANSPORT,
                UTILITY,
                INSPECTION,
                INFRASTRUCTURE
        ).stream().sorted().toList();
    }
}
