package com.butchercraft.workstation.projection;

public final class UnsupportedWorkstationProjectionSchemaException extends IllegalArgumentException {
    public UnsupportedWorkstationProjectionSchemaException(int schemaVersion) {
        super("Unsupported durable Workstation projection schema: " + schemaVersion);
    }
}
