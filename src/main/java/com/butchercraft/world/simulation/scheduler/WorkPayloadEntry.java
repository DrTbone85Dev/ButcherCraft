package com.butchercraft.world.simulation.scheduler;

import java.math.BigDecimal;
import java.util.Objects;

public record WorkPayloadEntry(String key, WorkPayloadValueType type, String canonicalValue)
        implements Comparable<WorkPayloadEntry> {
    public WorkPayloadEntry {
        key = SchedulerValidation.requireId(key, "Payload key");
        type = Objects.requireNonNull(type, "type");
        canonicalValue = normalize(type, canonicalValue);
    }

    public static WorkPayloadEntry string(String key, String value) {
        return new WorkPayloadEntry(key, WorkPayloadValueType.STRING, value);
    }
    public static WorkPayloadEntry longValue(String key, long value) {
        return new WorkPayloadEntry(key, WorkPayloadValueType.LONG, Long.toString(value));
    }
    public static WorkPayloadEntry booleanValue(String key, boolean value) {
        return new WorkPayloadEntry(key, WorkPayloadValueType.BOOLEAN, Boolean.toString(value));
    }
    public static WorkPayloadEntry decimal(String key, String value) {
        return new WorkPayloadEntry(key, WorkPayloadValueType.DECIMAL, value);
    }
    public static WorkPayloadEntry identifier(String key, String value) {
        return new WorkPayloadEntry(key, WorkPayloadValueType.IDENTIFIER, value);
    }

    @Override public int compareTo(WorkPayloadEntry other) {
        return key.compareTo(Objects.requireNonNull(other, "other").key);
    }

    private static String normalize(WorkPayloadValueType type, String source) {
        String value = Objects.requireNonNull(source, "canonicalValue");
        if (value.length() > 2_048) throw new IllegalArgumentException("Payload value exceeds 2048 characters");
        return switch (type) {
            case STRING -> value;
            case LONG -> Long.toString(Long.parseLong(value));
            case BOOLEAN -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new IllegalArgumentException("Boolean payload must be true or false");
                }
                yield value;
            }
            case DECIMAL -> {
                BigDecimal decimal = new BigDecimal(value).stripTrailingZeros();
                if (decimal.scale() < 0) decimal = decimal.setScale(0);
                if (decimal.scale() > 9 || decimal.precision() > 38) {
                    throw new IllegalArgumentException("Decimal payload exceeds canonical limits");
                }
                yield decimal.toPlainString();
            }
            case IDENTIFIER -> SchedulerValidation.requireId(value, "Identifier payload");
        };
    }
}
