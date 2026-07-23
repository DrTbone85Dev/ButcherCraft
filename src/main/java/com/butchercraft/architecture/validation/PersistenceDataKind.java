package com.butchercraft.architecture.validation;

public enum PersistenceDataKind {
    IMMUTABLE_DEFINITIONS,
    IMMUTABLE_HISTORY,
    MUTABLE_RUNTIME,
    SEPARATED_DEFINITIONS_AND_RUNTIME,
    MIXED_AUTHORITY
}
