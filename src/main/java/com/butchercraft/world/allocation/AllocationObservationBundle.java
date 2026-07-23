package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.List;

public record AllocationObservationBundle(
        long simulationTick,
        List<AllocationProviderId> providerIds,
        List<AllocationObservationResult> providerResults,
        List<ObservedResourceSnapshot> resources,
        List<ObservedCapacitySnapshot> capacities,
        List<AllocationProviderFailure> failures,
        List<AllocationProviderWarning> warnings,
        AllocationObservationBundleStatus status,
        AllocationObservationReport report,
        AllocationMetadata scopeMetadata,
        int schemaVersion
) {
    public AllocationObservationBundle {
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        providerIds = AllocationProviderValidation.canonical(
                providerIds,
                AllocationSchema.MAXIMUM_PROVIDERS,
                "providerIds"
        );
        providerResults = AllocationProviderValidation.canonical(
                providerResults,
                AllocationObservationResult::providerId,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_PROVIDERS,
                "providerResults"
        );
        resources = AllocationProviderValidation.canonical(
                resources,
                ObservedResourceSnapshot::resourceId,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_OBSERVATION_RESOURCES,
                "resources"
        );
        capacities = AllocationProviderValidation.canonical(
                capacities,
                ObservedCapacitySnapshot::capacityId,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_OBSERVATION_CAPACITIES,
                "capacities"
        );
        failures = AllocationProviderValidation.canonical(
                failures,
                AllocationSchema.MAXIMUM_PROVIDER_FAILURES,
                "failures"
        );
        warnings = AllocationProviderValidation.canonical(
                warnings,
                AllocationSchema.MAXIMUM_PROVIDER_WARNINGS,
                "warnings"
        );
        status = AllocationValidation.required(status, "status");
        report = AllocationValidation.required(report, "report");
        scopeMetadata = AllocationValidation.required(scopeMetadata, "scopeMetadata");
        schemaVersion = AllocationValidation.schema(schemaVersion);
        validateAssociations(
                simulationTick,
                providerIds,
                providerResults,
                failures,
                status,
                report
        );
    }

    public boolean usableForAllocationCycle() {
        return status == AllocationObservationBundleStatus.COMPLETE;
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.bundle(this);
    }

    private static void validateAssociations(
            long simulationTick,
            List<AllocationProviderId> providerIds,
            List<AllocationObservationResult> results,
            List<AllocationProviderFailure> failures,
            AllocationObservationBundleStatus status,
            AllocationObservationReport report
    ) {
        List<AllocationProviderId> resultIds = results.stream()
                .map(AllocationObservationResult::providerId)
                .toList();
        if (!providerIds.equals(resultIds)) {
            throw new IllegalArgumentException(
                    "Observation bundle provider ids and results must match"
            );
        }
        if (results.stream().anyMatch(result -> result.simulationTick() != simulationTick)
                || report.simulationTick() != simulationTick
                || !report.providerIds().equals(providerIds)
                || !report.failures().equals(failures)
                || !report.warnings().equals(expectedWarnings(results))
                || report.summary().resourceCount() != results.stream()
                        .mapToInt(result -> result.resources().size())
                        .sum()
                || report.summary().capacityCount() != results.stream()
                        .mapToInt(result -> result.capacities().size())
                        .sum()) {
            throw new IllegalArgumentException(
                    "Observation bundle tick, provider, and report evidence must match"
            );
        }
        boolean globalFailure = failures.stream()
                .anyMatch(failure -> failure.scope() == AllocationProviderFailureScope.BUNDLE);
        AllocationObservationBundleStatus expected = failures.isEmpty()
                ? AllocationObservationBundleStatus.COMPLETE
                : globalFailure
                        ? AllocationObservationBundleStatus.UNUSABLE
                        : AllocationObservationBundleStatus.INCOMPLETE;
        if (status != expected) {
            throw new IllegalArgumentException(
                    "Observation bundle status does not match failure evidence"
            );
        }
    }

    private static List<AllocationProviderWarning> expectedWarnings(
            List<AllocationObservationResult> results
    ) {
        List<AllocationProviderWarning> warnings = results.stream()
                .flatMap(result -> result.warnings().stream())
                .sorted()
                .distinct()
                .toList();
        return warnings.size() <= AllocationSchema.MAXIMUM_PROVIDER_WARNINGS
                ? warnings
                : List.copyOf(warnings.subList(
                        0,
                        AllocationSchema.MAXIMUM_PROVIDER_WARNINGS
                ));
    }
}
