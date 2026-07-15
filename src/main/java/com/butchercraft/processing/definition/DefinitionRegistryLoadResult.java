package com.butchercraft.processing.definition;

import java.util.Objects;

public record DefinitionRegistryLoadResult(
        boolean speciesRegistryAvailable,
        boolean processingProfileRegistryAvailable,
        boolean productRegistryAvailable,
        boolean processingOperationRegistryAvailable,
        DefinitionRegistryView view,
        DefinitionValidationReport report
) {
    public DefinitionRegistryLoadResult {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(report, "report");
    }

    public boolean allRegistriesAvailable() {
        return speciesRegistryAvailable
                && processingProfileRegistryAvailable
                && productRegistryAvailable
                && processingOperationRegistryAvailable;
    }
}
