package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public record DefinitionValidationIssue(
        DefinitionIssueSeverity severity,
        String reasonCode,
        Optional<ResourceLocation> definitionId,
        String explanation
) implements Comparable<DefinitionValidationIssue> {
    private static final Comparator<DefinitionValidationIssue> ORDERING = Comparator
            .comparing(DefinitionValidationIssue::severity)
            .thenComparing(issue -> issue.definitionId().map(ResourceLocation::toString).orElse(""))
            .thenComparing(DefinitionValidationIssue::reasonCode)
            .thenComparing(DefinitionValidationIssue::explanation);

    public DefinitionValidationIssue {
        Objects.requireNonNull(severity, "severity");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode").strip();
        if (reasonCode.isEmpty()) {
            throw new IllegalArgumentException("Validation reason code cannot be blank");
        }
        definitionId = Objects.requireNonNull(definitionId, "definitionId");
        explanation = Objects.requireNonNull(explanation, "explanation").strip();
        if (explanation.isEmpty()) {
            throw new IllegalArgumentException("Validation explanation cannot be blank");
        }
    }

    public static DefinitionValidationIssue error(String reasonCode, ResourceLocation definitionId, String explanation) {
        return new DefinitionValidationIssue(
                DefinitionIssueSeverity.ERROR,
                reasonCode,
                Optional.of(Objects.requireNonNull(definitionId, "definitionId")),
                explanation
        );
    }

    public static DefinitionValidationIssue error(String reasonCode, String explanation) {
        return new DefinitionValidationIssue(
                DefinitionIssueSeverity.ERROR,
                reasonCode,
                Optional.empty(),
                explanation
        );
    }

    public static DefinitionValidationIssue warning(String reasonCode, ResourceLocation definitionId, String explanation) {
        return new DefinitionValidationIssue(
                DefinitionIssueSeverity.WARNING,
                reasonCode,
                Optional.of(Objects.requireNonNull(definitionId, "definitionId")),
                explanation
        );
    }

    @Override
    public int compareTo(DefinitionValidationIssue other) {
        return ORDERING.compare(this, other);
    }
}
