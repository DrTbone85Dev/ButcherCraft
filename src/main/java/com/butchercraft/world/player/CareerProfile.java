package com.butchercraft.world.player;

import java.util.Arrays;

public enum CareerProfile {
    FAMILY_SUCCESSOR("family_successor"),
    INDEPENDENT_ENTREPRENEUR("independent_entrepreneur"),
    COOPERATIVE_MANAGER("cooperative_manager"),
    CORPORATE_MANAGER("corporate_manager"),
    COUNTY_PROCESSOR("county_processor"),
    APPRENTICE_BUTCHER("apprentice_butcher");

    private final String serializedName;

    CareerProfile(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CareerProfile fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(profile -> profile.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown career profile: " + serializedName));
    }
}
