package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum SubstitutionPolicy {
    EXACT_ONLY("exact_only"), EQUIVALENT_ALLOWED("equivalent_allowed"),
    REQUESTER_APPROVAL_REQUIRED("requester_approval_required"), NO_SUBSTITUTION("no_substitution");

    private final String serializedName;

    SubstitutionPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static SubstitutionPolicy fromSerializedName(String value) {
        return Arrays.stream(values()).filter(policy -> policy.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown substitution policy: " + value));
    }
}
