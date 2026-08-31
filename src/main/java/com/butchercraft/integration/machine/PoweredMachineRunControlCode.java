package com.butchercraft.integration.machine;

public enum PoweredMachineRunControlCode {
    STARTED,
    STOP_REQUESTED,
    STOPPED,
    RESUMED,
    EXISTING_RESULT,
    BUSY,
    RESTART_REQUIRED,
    RECOVERY_REQUIRED,
    REJECTED
}
