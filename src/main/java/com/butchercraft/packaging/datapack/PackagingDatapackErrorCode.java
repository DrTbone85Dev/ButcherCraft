package com.butchercraft.packaging.datapack;

/**
 * Stable validation codes for datapack packaging loading.
 */
public enum PackagingDatapackErrorCode {
    MALFORMED_JSON,
    MALFORMED_DEFINITION,
    DUPLICATE_ID,
    MISSING_ID,
    MISSING_DISPLAY_NAME,
    UNKNOWN_FORMAT,
    UNKNOWN_CATEGORY,
    UNKNOWN_QUANTITY_UNIT,
    UNSUPPORTED_SCHEMA_VERSION,
    MALFORMED_CATEGORIES,
    MALFORMED_TAGS,
    MALFORMED_METADATA
}
