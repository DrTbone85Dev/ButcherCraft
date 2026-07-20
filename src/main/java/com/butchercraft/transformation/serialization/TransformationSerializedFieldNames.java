package com.butchercraft.transformation.serialization;

/**
 * Stable external field names for transformation definition serialization.
 */
public final class TransformationSerializedFieldNames {
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String ID = "id";
    public static final String DISPLAY_NAME = "display_name";
    public static final String REQUIRED_CAPABILITY = "required_capability";
    public static final String INPUTS = "inputs";
    public static final String OUTPUTS = "outputs";
    public static final String DURATION = "duration";
    public static final String YIELD = "yield";
    public static final String METADATA = "metadata";

    public static final String PRODUCT_ID = "product_id";
    public static final String QUANTITY = "quantity";
    public static final String UNIT = "unit";
    public static final String CLASSIFICATION = "classification";

    public static final String MILLISECONDS = "milliseconds";

    public static final String NUMERATOR = "numerator";
    public static final String DENOMINATOR = "denominator";

    private TransformationSerializedFieldNames() {
    }
}
