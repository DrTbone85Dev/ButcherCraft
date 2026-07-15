package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProcessingGraph {
    private final Map<ResourceLocation, List<ProcessingGraphEdge>> edgesByInput;
    private final DefinitionValidationReport validationReport;

    private ProcessingGraph(
            Map<ResourceLocation, List<ProcessingGraphEdge>> edgesByInput,
            DefinitionValidationReport validationReport
    ) {
        this.edgesByInput = immutableEdges(edgesByInput);
        this.validationReport = Objects.requireNonNull(validationReport, "validationReport");
    }

    public static ProcessingGraph fromDefinitions(DefinitionRegistryView definitions) {
        List<OperationDefinitionEntry> entries = definitions.operations().entrySet().stream()
                .map(entry -> new OperationDefinitionEntry(entry.getKey(), entry.getValue()))
                .toList();
        return fromOperationEntries(definitions, entries);
    }

    public static ProcessingGraph fromOperationEntries(
            DefinitionRegistryView definitions,
            Collection<OperationDefinitionEntry> operationEntries
    ) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(operationEntries, "operationEntries");

        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(definitions);
        DefinitionValidationReport report = DefinitionValidationReport.EMPTY;
        Map<ResourceLocation, ProcessingOperationDefinition> seenOperations = new LinkedHashMap<>();
        Map<ResourceLocation, List<ProcessingGraphEdge>> edges = new LinkedHashMap<>();

        List<OperationDefinitionEntry> sortedEntries = operationEntries.stream()
                .map(entry -> Objects.requireNonNull(entry, "entry"))
                .sorted(Comparator.comparing(entry -> entry.id().toString()))
                .toList();

        for (OperationDefinitionEntry entry : sortedEntries) {
            ProcessingOperationDefinition previous = seenOperations.putIfAbsent(entry.id(), entry.definition());
            if (previous != null && !previous.equals(entry.definition())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "duplicate_operation_id",
                        entry.id(),
                        "Multiple conflicting operation definitions use the same id"
                ));
                continue;
            }
            DefinitionValidationReport operationReport = resolver.validateOperation(entry.id(), entry.definition());
            report = report.plus(operationReport);
            if (!operationReport.hasErrors()) {
                ProcessingGraphEdge edge = new ProcessingGraphEdge(
                        entry.id(),
                        entry.definition().inputProduct(),
                        entry.definition().outputProduct()
                );
                edges.computeIfAbsent(edge.inputProduct(), ignored -> new ArrayList<>()).add(edge);
            }
        }

        ProcessingGraph graph = new ProcessingGraph(edges, report);
        return new ProcessingGraph(graph.edgesByInput, graph.validationReport.plus(graph.detectCycles()));
    }

    public List<ProcessingGraphEdge> operationsAvailableFor(ResourceLocation inputProduct) {
        Objects.requireNonNull(inputProduct, "inputProduct");
        return edgesByInput.getOrDefault(inputProduct, List.of());
    }

    public List<ResourceLocation> outputsReachableThroughOneOperation(ResourceLocation inputProduct) {
        return operationsAvailableFor(inputProduct).stream()
                .map(ProcessingGraphEdge::outputProduct)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public boolean hasDirectTransformation(ResourceLocation inputProduct, ResourceLocation outputProduct) {
        Objects.requireNonNull(outputProduct, "outputProduct");
        return operationsAvailableFor(inputProduct).stream()
                .anyMatch(edge -> edge.outputProduct().equals(outputProduct));
    }

    public Map<ResourceLocation, List<ProcessingGraphEdge>> edgesByInput() {
        return edgesByInput;
    }

    public DefinitionValidationReport validationReport() {
        return validationReport;
    }

    private DefinitionValidationReport detectCycles() {
        DefinitionValidationReport report = DefinitionValidationReport.EMPTY;
        Set<ResourceLocation> visited = new HashSet<>();
        Set<ResourceLocation> visiting = new HashSet<>();
        ArrayDeque<ResourceLocation> path = new ArrayDeque<>();

        List<ResourceLocation> starts = edgesByInput.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        for (ResourceLocation start : starts) {
            report = report.plus(detectCycles(start, visited, visiting, path));
        }
        return report;
    }

    private DefinitionValidationReport detectCycles(
            ResourceLocation product,
            Set<ResourceLocation> visited,
            Set<ResourceLocation> visiting,
            ArrayDeque<ResourceLocation> path
    ) {
        if (visited.contains(product)) {
            return DefinitionValidationReport.EMPTY;
        }
        if (visiting.contains(product)) {
            return DefinitionValidationReport.of(DefinitionValidationIssue.warning(
                    "cycle_detected",
                    product,
                    "Processing graph contains a reachable cycle involving " + product
            ));
        }

        visiting.add(product);
        path.addLast(product);
        DefinitionValidationReport report = DefinitionValidationReport.EMPTY;
        for (ProcessingGraphEdge edge : edgesByInput.getOrDefault(product, List.of())) {
            report = report.plus(detectCycles(edge.outputProduct(), visited, visiting, path));
        }
        path.removeLast();
        visiting.remove(product);
        visited.add(product);
        return report;
    }

    private static Map<ResourceLocation, List<ProcessingGraphEdge>> immutableEdges(
            Map<ResourceLocation, List<ProcessingGraphEdge>> edges
    ) {
        Map<ResourceLocation, List<ProcessingGraphEdge>> sorted = edges.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().sorted().toList(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        Map<ResourceLocation, List<ProcessingGraphEdge>> immutable = new LinkedHashMap<>();
        for (var entry : sorted.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        Map<ResourceLocation, List<ProcessingGraphEdge>> ordered = immutable.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return Collections.unmodifiableMap(ordered);
    }

    public Set<ResourceLocation> knownInputProducts() {
        return edgesByInput.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
