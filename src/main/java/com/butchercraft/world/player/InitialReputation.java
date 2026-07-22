package com.butchercraft.world.player;

import java.util.Arrays;

public enum InitialReputation {
    UNKNOWN_NEWCOMER("unknown_newcomer"),
    LOCAL_FAMILY_NAME("local_family_name"),
    TRUSTED_APPRENTICE("trusted_apprentice"),
    COUNTY_BACKED("county_backed"),
    COOPERATIVE_BACKED("cooperative_backed"),
    CORPORATE_PLACED("corporate_placed");

    private final String serializedName;

    InitialReputation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static InitialReputation fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(reputation -> reputation.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown initial reputation: " + serializedName));
    }
}
