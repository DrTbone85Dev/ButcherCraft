package com.butchercraft.packaging.definition;

import java.util.Arrays;

/**
 * Stable package-definition format category.
 */
public enum PackagingFormat {
    RETAIL("retail"),
    TRAY_WRAP("tray_wrap"),
    VACUUM("vacuum"),
    BUTCHER_PAPER("butcher_paper"),
    FREEZER_PAPER("freezer_paper");

    private final String id;

    PackagingFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PackagingFormat fromId(String id) {
        return Arrays.stream(values())
                .filter(format -> format.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported packaging format id: " + id));
    }
}
