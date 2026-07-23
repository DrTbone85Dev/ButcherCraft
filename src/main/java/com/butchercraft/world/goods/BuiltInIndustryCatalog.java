package com.butchercraft.world.goods;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BuiltInIndustryCatalog {
    public static final IndustryId CORE = IndustryId.of("butchercraft:core");
    public static final IndustryId MEAT_PROCESSING = IndustryId.of("butchercraft:meat_processing");
    public static final IndustryId AGRICULTURE = IndustryId.of("butchercraft:agriculture");
    public static final IndustryId DAIRY = IndustryId.of("butchercraft:dairy");
    public static final IndustryId MANUFACTURING = IndustryId.of("butchercraft:manufacturing");
    public static final IndustryId FORESTRY = IndustryId.of("butchercraft:forestry");
    public static final IndustryId TRANSPORTATION = IndustryId.of("butchercraft:transportation");
    public static final IndustryId RETAIL = IndustryId.of("butchercraft:retail");
    public static final IndustryId RESTAURANTS = IndustryId.of("butchercraft:restaurants");
    public static final IndustryId UTILITIES = IndustryId.of("butchercraft:utilities");

    private static final Set<IndustryId> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            CORE,
            MEAT_PROCESSING,
            AGRICULTURE,
            DAIRY,
            MANUFACTURING,
            FORESTRY,
            TRANSPORTATION,
            RETAIL,
            RESTAURANTS,
            UTILITIES
    )));

    private BuiltInIndustryCatalog() {
    }

    public static Set<IndustryId> all() {
        return ALL;
    }
}
