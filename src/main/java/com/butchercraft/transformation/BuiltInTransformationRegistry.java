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
            "data/butchercraft/butchercraft/transformation/grind_chicken.json",
            "data/butchercraft/butchercraft/transformation/grind_bison.json",
            "data/butchercraft/butchercraft/transformation/grind_lamb.json",
            "data/butchercraft/butchercraft/transformation/grind_venison.json",
            "data/butchercraft/butchercraft/transformation/form_beef_patties.json",
            "data/butchercraft/butchercraft/transformation/break_beef_forequarter.json",
            "data/butchercraft/butchercraft/transformation/break_beef_hindquarter.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_short_loin.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_round.json",
            "data/butchercraft/butchercraft/transformation/cut_beef_sirloin.json",
            "data/butchercraft/butchercraft/transformation/fabricate_t_bone_steak.json"
    );

    public static final EngineId WORKSTATION_CAPABILITY_GRINDING = EngineId.of("butchercraft:grinding");
    public static final EngineId WORKSTATION_CAPABILITY_PATTY_FORMING = EngineId.of("butchercraft:patty_forming");
    public static final EngineId WORKSTATION_CAPABILITY_BANDSAW = EngineId.of("butchercraft:bandsaw");
    public static final EngineId WORKSTATION_CAPABILITY_CUTTING_TABLE = EngineId.of("butchercraft:cutting_table");

    private BuiltInTransformationRegistry() {
    }

    public static TransformationRegistry builtInRegistry() {
        return TransformationRegistryService.loadBundledRegistry();
    }
}
