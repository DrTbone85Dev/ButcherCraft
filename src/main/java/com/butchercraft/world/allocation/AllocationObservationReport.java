package com.butchercraft.world.allocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record AllocationObservationReport(
        long simulationTick,
        List<AllocationProviderId> providerIds,
        Map<AllocationProviderId, String> providerResultDigests,
        List<AllocationProviderFailure> failures,
        List<AllocationProviderWarning> warnings,
        AllocationObservationSummary summary,
        String registryDigest,
        String requestDigest,
        int schemaVersion
) {
    public AllocationObservationReport {
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        providerIds = AllocationProviderValidation.canonical(
                providerIds,
                AllocationSchema.MAXIMUM_PROVIDERS,
                "providerIds"
        );
        providerResultDigests = canonicalDigests(providerResultDigests);
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
        summary = AllocationValidation.required(summary, "summary");
        registryDigest = AllocationProviderDigestSupport.validateDigest(
                registryDigest,
                "registryDigest"
        );
        requestDigest = AllocationProviderDigestSupport.validateDigest(
                requestDigest,
                "requestDigest"
        );
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (!providerIds.equals(providerResultDigests.keySet().stream().toList())) {
            throw new IllegalArgumentException(
                    "Provider report ids and result digests must match"
            );
        }
        if (summary.providerCount() != providerIds.size()
                || summary.failureCount() != failures.size()
                || summary.warningCount() != warnings.size()) {
            throw new IllegalArgumentException(
                    "Provider report summary does not match report evidence"
            );
        }
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.report(this);
    }

    private static Map<AllocationProviderId, String> canonicalDigests(
            Map<AllocationProviderId, String> source
    ) {
        Map<AllocationProviderId, String> input = AllocationValidation.required(
                source,
                "providerResultDigests"
        );
        if (input.size() > AllocationSchema.MAXIMUM_PROVIDERS) {
            throw new IllegalArgumentException("Provider result digest map exceeds schema bound");
        }
        TreeMap<AllocationProviderId, String> ordered = new TreeMap<>();
        input.forEach((providerId, digest) -> ordered.put(
                AllocationValidation.required(providerId, "providerId"),
                AllocationProviderDigestSupport.validateDigest(
                        digest,
                        "providerResultDigest"
                )
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
    }
}
