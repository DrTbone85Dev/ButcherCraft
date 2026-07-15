package com.butchercraft.engine.validation;

/**
 * Explicit validation outcome status.
 *
 * <p>Validation rules return this status with inspectable reasons or warnings instead of booleans
 * or null. It is pure engine data with no Minecraft dependency.</p>
 */
public enum ValidationStatus {
    ACCEPTED,
    REJECTED
}
