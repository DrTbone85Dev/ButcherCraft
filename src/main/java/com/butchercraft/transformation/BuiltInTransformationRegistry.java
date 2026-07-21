package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.transformation.datapack.TransformationRegistryService;

import java.util.List;

/**
 * Built-in transformation datapack resource locations and stable capability ids.
 */
public final class BuiltInTransformationRegistry {
    public static final String DATAPACK_DIRECTORY = "butchercraft/transformation";
    public static final List<String> BUILT_IN_RESOURCE_PATHS = List.of(
            "data/butchercraft/butchercraft/transformation/grind_beef.json",
            "data/butchercraft/butchercraft/transformation/grind_pork.json",
            "data/butchercraft/butchercraft/transformation/grind_bison.json",
            "data/butchercraft/butchercraft/transformation/break_beef_forequarter.json",
            "data/butchercraft/butchercraft/transformation/break_beef_hindquarter.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_short_loin.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_round.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_sirloin.json"
    );

    public static final EngineId WORKSTATION_CAPABILITY_GRINDING = EngineId.of("butchercraft:grinding");
    public static final EngineId WORKSTATION_CAPABILITY_BANDSAW = EngineId.of("butchercraft:bandsaw");

    private BuiltInTransformationRegistry() {
    }

    public static TransformationRegistry builtInRegistry() {
        return TransformationRegistryService.loadBundledRegistry();
    }
}
