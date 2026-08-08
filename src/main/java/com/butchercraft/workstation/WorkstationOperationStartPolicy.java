package com.butchercraft.workstation;

/**
 * Defines whether recipe readiness is sufficient to request operation authority.
 */
public enum WorkstationOperationStartPolicy {
    AUTOMATIC_WHEN_READY,
    EXPLICIT_REQUEST
}
