package com.butchercraft.integration.machine.grinder;

public enum GrinderRunControlCode {
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
