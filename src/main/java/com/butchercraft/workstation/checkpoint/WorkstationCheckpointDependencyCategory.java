package com.butchercraft.workstation.checkpoint;

import java.util.Locale;

public enum WorkstationCheckpointDependencyCategory {
    INSTANCE_LIFECYCLE,
    EXECUTION_OPERATION,
    MACHINE_RUN,
    MATERIAL_HANDLING_SOURCE,
    MATERIAL_HANDLING_DESTINATION,
    ENDPOINT_EFFECT,
    ENDPOINT_OWNER_RESULT,
    OWNER_RECOVERY_REFERENCE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
