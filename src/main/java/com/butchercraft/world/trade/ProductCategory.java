package com.butchercraft.world.trade;

import java.util.Arrays;

public enum ProductCategory {
    FRESH_BEEF("fresh_beef"),
    FRESH_PORK("fresh_pork"),
    POULTRY("poultry"),
    LAMB("lamb"),
    WILD_GAME("wild_game"),
    SMOKED_PRODUCTS("smoked_products"),
    SAUSAGE("sausage"),
    JERKY("jerky"),
    FROZEN_FOODS("frozen_foods"),
    PACKAGING_SUPPLIES("packaging_supplies"),
    SEASONINGS("seasonings"),
    EQUIPMENT("equipment");

    private final String serializedName;

    ProductCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ProductCategory fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(category -> category.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown product category: " + serializedName));
    }
}
