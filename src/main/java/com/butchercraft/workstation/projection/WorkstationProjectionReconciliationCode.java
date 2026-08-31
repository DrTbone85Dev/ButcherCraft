package com.butchercraft.workstation.projection;

public enum WorkstationProjectionReconciliationCode {
    EQUAL,
    DEFERRED_BY_STARTUP_GATE,
    INITIALIZED,
    LEGACY_BOOTSTRAPPED,
    DURABLE_APPLIED_TO_LOADED_BLOCK_ENTITY,
    OWNER_EVIDENCE_REPAIRED_DURABLE_PROJECTION,
    RETIRED,
    RECOVERY_REQUIRED,
    IDENTITY_CONFLICT
}
