package com.butchercraft.world.allocation;

import java.math.BigDecimal;
import java.util.Comparator;

public record AllocationMetadataValue(
        AllocationMetadataValueType type,
        String canonicalValue
) implements Comparable<AllocationMetadataValue> {
    private static final Comparator<AllocationMetadataValue> ORDER = Comparator
            .<AllocationMetadataValue, String>comparing(
                    value -> value.type().serializedName()
            )
            .thenComparing(AllocationMetadataValue::canonicalValue);

    public AllocationMetadataValue {
        type = AllocationValidation.required(type, "type");
        canonicalValue = validate(type, canonicalValue);
    }

    public static AllocationMetadataValue text(String value) {
        return new AllocationMetadataValue(AllocationMetadataValueType.TEXT, value);
    }

    public static AllocationMetadataValue identifier(String value) {
        return new AllocationMetadataValue(AllocationMetadataValueType.IDENTIFIER, value);
    }

    public static AllocationMetadataValue integer(long value) {
        return new AllocationMetadataValue(AllocationMetadataValueType.INTEGER, Long.toString(value));
    }

    public static AllocationMetadataValue decimal(String value) {
        return new AllocationMetadataValue(AllocationMetadataValueType.DECIMAL, value);
    }

    public static AllocationMetadataValue bool(boolean value) {
        return new AllocationMetadataValue(AllocationMetadataValueType.BOOLEAN, Boolean.toString(value));
    }

    @Override
    public int compareTo(AllocationMetadataValue other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    private static String validate(AllocationMetadataValueType type, String value) {
        return switch (type) {
            case TEXT -> AllocationValidation.text(
                    value,
                    "metadataValue",
                    AllocationSchema.MAXIMUM_METADATA_VALUE_LENGTH
            );
            case IDENTIFIER -> AllocationValidation.id(value, "metadataValue");
            case INTEGER -> {
                try {
                    yield Long.toString(Long.parseLong(
                            AllocationValidation.required(value, "metadataValue")
                    ));
                } catch (NumberFormatException exception) {
                    throw AllocationValidation.failure(
                            AllocationValidationFailureCode.INVALID_METADATA,
                            "metadataValue",
                            "Metadata integer is invalid: " + value
                    );
                }
            }
            case DECIMAL -> canonicalDecimal(value);
            case BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw AllocationValidation.failure(
                            AllocationValidationFailureCode.INVALID_METADATA,
                            "metadataValue",
                            "Metadata boolean must be true or false"
                    );
                }
                yield value;
            }
        };
    }

    private static String canonicalDecimal(String value) {
        if (value == null || value.contains("e") || value.contains("E")) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INVALID_METADATA,
                    "metadataValue",
                    "Metadata decimal must be a non-scientific exact decimal"
            );
        }
        try {
            BigDecimal normalized = new BigDecimal(value).stripTrailingZeros();
            if (normalized.scale() < 0) {
                normalized = normalized.setScale(0);
            }
            if (normalized.scale() > AllocationSchema.MAXIMUM_DECIMAL_SCALE
                    || normalized.precision() > AllocationSchema.MAXIMUM_DECIMAL_PRECISION) {
                throw AllocationValidation.failure(
                        AllocationValidationFailureCode.INVALID_METADATA,
                        "metadataValue",
                        "Metadata decimal exceeds Allocation schema bounds"
                );
            }
            return normalized.toPlainString();
        } catch (NumberFormatException exception) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INVALID_METADATA,
                    "metadataValue",
                    "Metadata decimal is invalid: " + value
            );
        }
    }
}
