package com.butchercraft.product.integration;

import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.DevelopmentProductItemMapping;

/**
 * Development-only bridge from registered product fixture items to output ItemStacks.
 *
 * <p>This remains separate from generic workstation logic. Future product item creation still
 * needs a deliberate data-driven design before final content depends on it.</p>
 */
public final class DevelopmentProductItemMappings {
    private DevelopmentProductItemMappings() {
    }

    public static DevelopmentProductItemMapping fixtureMapping() {
        return DevelopmentProductItemMapping.fromFixtureItems(
                ModItems.BEEF_TRIM_TEST,
                ModItems.GROUND_BEEF_TEST,
                ModItems.PORK_TRIM_TEST,
                ModItems.GROUND_PORK_TEST,
                ModItems.BISON_TRIM_TEST,
                ModItems.GROUND_BISON_TEST,
                ModItems.BEEF_FOREQUARTER_TEST,
                ModItems.BEEF_CHUCK_TEST,
                ModItems.BEEF_RIB_TEST,
                ModItems.BEEF_PACKER_BRISKET_TEST,
                ModItems.BEEF_PLATE_TEST,
                ModItems.BEEF_SHANK_TEST,
                ModItems.BEEF_FAT_TEST,
                ModItems.BEEF_BONE_TEST
        );
    }
}
