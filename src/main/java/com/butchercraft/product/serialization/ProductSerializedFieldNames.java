package com.butchercraft.product.serialization;

/**
 * Stable external field names for product definition serialization.
 */
public final class ProductSerializedFieldNames {
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String ID = "id";
    public static final String DISPLAY_NAME = "display_name";
    public static final String CATEGORY = "category";
    public static final String DEFAULT_QUANTITY_UNIT = "default_quantity_unit";
    public static final String TAGS = "tags";
    public static final String PACKAGING = "packaging";
    public static final String PACKAGING_DEFINITION = "definition";
    public static final String PACKAGING_SOURCE_PRODUCT = "source_product";
    public static final String METADATA = "metadata";

    private ProductSerializedFieldNames() {
    }
}
