package com.butchercraft.world.workforce;

import java.util.Arrays;

public enum WorkforceSkillLevel {
    UNTRAINED("untrained"),
    TRAINEE("trainee"),
    QUALIFIED("qualified"),
    EXPERIENCED("experienced"),
    EXPERT("expert"),
    MASTER("master");

    private final String serializedName;

    WorkforceSkillLevel(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkforceSkillLevel fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(level -> level.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown workforce skill level: " + serializedName));
    }
}
