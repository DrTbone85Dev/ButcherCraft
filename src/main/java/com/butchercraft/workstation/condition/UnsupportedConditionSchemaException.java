package com.butchercraft.workstation.condition;

public final class UnsupportedConditionSchemaException extends IllegalArgumentException {
    public UnsupportedConditionSchemaException(String record, int schema) {
        super("Unsupported " + record + " schema: " + schema);
    }
}
