package com.butchercraft.workstation.checkpoint;

import java.util.Locale;

public enum WorkstationCheckpointCompletenessStatus {
    COMPLETE_RESTORABLE,
    INCOMPLETE_REQUIRED_PROJECTION,
    CONFLICT,
    UNSUPPORTED,
    RECOVERY_REQUIRED,
    NON_RESTORABLE_WORKSTATION_PROJECTION_INCOMPLETE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
