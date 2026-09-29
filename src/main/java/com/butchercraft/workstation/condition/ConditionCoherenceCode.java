package com.butchercraft.workstation.condition;

public enum ConditionCoherenceCode {
    COHERENT,
    LEGACY_INITIALIZATION_REQUIRED,
    RECOVERY_REQUIRED,
    UNSUPPORTED_SCHEMA,
    IDENTITY_CONFLICT,
    POLICY_CONFLICT,
    EFFECT_EVIDENCE_MISSING,
    PROJECTION_STALE
}
