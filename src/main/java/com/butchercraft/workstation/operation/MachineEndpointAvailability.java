package com.butchercraft.workstation.operation;

import java.util.Locale;

public enum MachineEndpointAvailability {
    AVAILABLE,
    UNAVAILABLE,
    REPLACED;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineEndpointAvailability fromSerializedName(String value) {
        String normalized = MachineOperatingValidation.text(value, "machine endpoint availability");
        for (MachineEndpointAvailability availability : values()) {
            if (availability.serializedName().equals(normalized)) return availability;
        }
        throw new IllegalArgumentException("Unknown machine endpoint availability: " + value);
    }
}
