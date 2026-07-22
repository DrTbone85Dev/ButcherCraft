package com.butchercraft.world.player;

public record PlayerBackground(
        String fullNamePlaceholder,
        String backgroundSummary,
        String legacySummary
) {
    public PlayerBackground {
        fullNamePlaceholder = PlayerValidation.requireNonBlank(fullNamePlaceholder, "player background fullNamePlaceholder");
        backgroundSummary = PlayerValidation.requireNonBlank(backgroundSummary, "player background backgroundSummary");
        legacySummary = PlayerValidation.requireNonBlank(legacySummary, "player background legacySummary");
    }
}
