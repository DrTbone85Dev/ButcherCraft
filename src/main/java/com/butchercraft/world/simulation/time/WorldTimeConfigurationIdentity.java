package com.butchercraft.world.simulation.time;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record WorldTimeConfigurationIdentity(String value) {
    public WorldTimeConfigurationIdentity {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("World time configuration identity must not be blank");
        }
    }

    public static WorldTimeConfigurationIdentity from(WorldTimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        String canonical = "schema_version=" + WorldTimeSchema.CURRENT_VERSION + "\n"
                + "enabled=" + configuration.enabled() + "\n"
                + "day_length_minutes=" + configuration.dayLengthMinutes() + "\n"
                + "dimension_policy=" + configuration.dimensionPolicy().serializedName() + "\n"
                + "business_epoch=minecraft_day_0_monday_visible_midnight_offset_6000\n";
        return new WorldTimeConfigurationIdentity("butchercraft:world_time_config/v1/"
                + sha256(canonical));
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
