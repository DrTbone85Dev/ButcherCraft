package com.butchercraft.world.manufacturer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record ManufacturerBranding(
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String visualIdentity
) {
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public ManufacturerBranding {
        primaryColor = requireColor(primaryColor, "primaryColor");
        secondaryColor = requireColor(secondaryColor, "secondaryColor");
        accentColor = requireColor(accentColor, "accentColor");
        visualIdentity = requireNonBlank(visualIdentity, "visualIdentity");
    }

    public List<String> colors() {
        return List.of(primaryColor, secondaryColor, accentColor);
    }

    private static String requireColor(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("Manufacturer branding " + fieldName + " must be a #RRGGBB color");
        }
        return value.toUpperCase();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Manufacturer branding " + fieldName + " must not be blank");
        }
        return value;
    }
}
