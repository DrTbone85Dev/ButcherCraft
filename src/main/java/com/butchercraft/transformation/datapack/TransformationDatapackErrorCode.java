package com.butchercraft.transformation.datapack;

/**
 * Stable validation codes for datapack transformation loading.
 */
public enum TransformationDatapackErrorCode {
    MALFORMED_JSON,
    MALFORMED_DEFINITION,
    DUPLICATE_ID,
    UNKNOWN_PRODUCT,
    UNKNOWN_CAPABILITY,
    UNSUPPORTED_SCHEMA_VERSION
}
