package com.butchercraft.processing.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record DefinitionValidationReport(List<DefinitionValidationIssue> issues) {
    public static final DefinitionValidationReport EMPTY = new DefinitionValidationReport(List.of());

    public DefinitionValidationReport {
        issues = issues.stream()
                .map(issue -> Objects.requireNonNull(issue, "issue"))
                .sorted()
                .toList();
    }

    public static DefinitionValidationReport of(DefinitionValidationIssue issue) {
        return new DefinitionValidationReport(List.of(issue));
    }

    public static DefinitionValidationReport combine(Collection<DefinitionValidationReport> reports) {
        List<DefinitionValidationIssue> combined = new ArrayList<>();
        for (DefinitionValidationReport report : reports) {
            combined.addAll(Objects.requireNonNull(report, "report").issues());
        }
        return new DefinitionValidationReport(combined);
    }

    public DefinitionValidationReport plus(DefinitionValidationIssue issue) {
        List<DefinitionValidationIssue> combined = new ArrayList<>(issues);
        combined.add(Objects.requireNonNull(issue, "issue"));
        return new DefinitionValidationReport(combined);
    }

    public DefinitionValidationReport plus(DefinitionValidationReport report) {
        List<DefinitionValidationIssue> combined = new ArrayList<>(issues);
        combined.addAll(Objects.requireNonNull(report, "report").issues());
        return new DefinitionValidationReport(combined);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == DefinitionIssueSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == DefinitionIssueSeverity.WARNING);
    }
}
