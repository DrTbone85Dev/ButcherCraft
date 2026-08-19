package com.butchercraft.workstation.operation;

public record MachineOperatingConfiguration(
        int maximumRecords,
        String configurationIdentity
) {
    public MachineOperatingConfiguration {
        if (maximumRecords <= 0) throw new IllegalArgumentException("Machine operating record limit must be positive");
        configurationIdentity = MachineOperatingValidation.id(
                configurationIdentity,
                "Machine operating configuration identity"
        );
    }

    public static MachineOperatingConfiguration standard() {
        return new MachineOperatingConfiguration(
                4_096,
                "butchercraft:machine_operating_configuration/schema_1"
        );
    }
}
