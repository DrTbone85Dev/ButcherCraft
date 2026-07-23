package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Map;

final class AllocationProviderDigestSupport {
    private AllocationProviderDigestSupport() {
    }

    static String descriptor(AllocationProviderDescriptor descriptor) {
        AllocationProviderDescriptor value = AllocationValidation.required(
                descriptor,
                "descriptor"
        );
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_provider_descriptor_v1")
                        .add(value.providerId().value())
                        .add(value.schemaVersion());
        value.authoritativeSubsystemIds().forEach(id -> digest.add("owner").add(id));
        value.resourceCategories().forEach(
                category -> digest.add("category").add(category.value())
        );
        value.capacityTypeIds().forEach(
                type -> digest.add("capacity_type").add(type.value())
        );
        value.capacityUnitIds().forEach(
                unit -> digest.add("capacity_unit").add(unit.value())
        );
        metadata(digest, value.metadata());
        return digest.finish();
    }

    static String registry(List<AllocationProviderDescriptor> descriptors) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_provider_registry_v1");
        AllocationValidation.required(descriptors, "descriptors").stream()
                .sorted()
                .forEach(descriptor -> digest
                        .add(descriptor.providerId().value())
                        .add(descriptor(descriptor)));
        return digest.finish();
    }

    static String context(AllocationObservationContext context) {
        AllocationObservationContext value = AllocationValidation.required(context, "context");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_observation_context_v1")
                        .add(value.simulationTick())
                        .add(value.maximumResourcesPerProvider())
                        .add(value.maximumCapacitiesPerProvider())
                        .add(value.schemaVersion());
        value.requestedResourceCategories().forEach(
                category -> digest.add("category").add(category.value())
        );
        value.requestedCapacityTypeIds().forEach(
                type -> digest.add("capacity_type").add(type.value())
        );
        metadata(digest, value.scopeMetadata());
        return digest.finish();
    }

    static String request(AllocationObservationRequest request) {
        AllocationObservationRequest value = AllocationValidation.required(request, "request");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_observation_request_v1")
                        .add(context(value.context()))
                        .add(value.maximumTotalResources())
                        .add(value.maximumTotalCapacities())
                        .add(value.schemaVersion());
        value.selectedProviderIds().forEach(
                providerId -> digest.add("provider").add(providerId.value())
        );
        return digest.finish();
    }

    static String resource(ObservedResourceSnapshot resource) {
        ObservedResourceSnapshot value = AllocationValidation.required(resource, "resource");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_resource_observation_v1")
                        .add(value.resourceId().value())
                        .add(value.resourceCategory().value())
                        .add(value.authoritativeProviderId().value())
                        .add(value.authoritativeExternalReference().canonicalKey())
                        .add(value.availability().serializedName())
                        .add(value.exclusivityMode().serializedName())
                        .add(value.observationSimulationTick())
                        .add(value.schemaVersion());
        metadata(digest, value.metadata());
        return digest.finish();
    }

    static String capacity(ObservedCapacitySnapshot capacity) {
        ObservedCapacitySnapshot value = AllocationValidation.required(capacity, "capacity");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_capacity_observation_v1")
                        .add(value.capacityId().value())
                        .add(value.resourceId().value())
                        .add(value.capacityTypeId().value())
                        .add(value.observedAmount().canonicalAmount())
                        .add(value.capacityUnitId().value())
                        .add(value.observationSimulationTick())
                        .add(value.authoritativeExternalReference().canonicalKey())
                        .add(value.schemaVersion());
        metadata(digest, value.metadata());
        return digest.finish();
    }

    static String failure(AllocationProviderFailure failure) {
        AllocationProviderFailure value = AllocationValidation.required(failure, "failure");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_provider_failure_v1")
                        .add(value.code().name())
                        .add(value.scope().name())
                        .add(value.providerId().isPresent())
                        .add(value.subject())
                        .add(value.message())
                        .add(value.simulationTick().isPresent())
                        .add(value.schemaVersion());
        value.providerId().ifPresent(providerId -> digest.add(providerId.value()));
        if (value.simulationTick().isPresent()) {
            digest.add(value.simulationTick().getAsLong());
        }
        return digest.finish();
    }

    static String warning(AllocationProviderWarning warning) {
        AllocationProviderWarning value = AllocationValidation.required(warning, "warning");
        return AllocationCanonicalDigest.create("butchercraft:allocation_provider_warning_v1")
                .add(value.code())
                .add(value.providerId().value())
                .add(value.subject())
                .add(value.message())
                .add(value.simulationTick())
                .add(value.schemaVersion())
                .finish();
    }

    static String result(AllocationObservationResult result) {
        AllocationObservationResult value = AllocationValidation.required(result, "result");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_observation_result_v1")
                        .add(value.providerId().value())
                        .add(value.simulationTick())
                        .add(value.successful())
                        .add(value.schemaVersion());
        value.resources().forEach(resource -> digest.add(resource(resource)));
        value.capacities().forEach(capacity -> digest.add(capacity(capacity)));
        value.failures().forEach(failure -> digest.add(failure(failure)));
        value.warnings().forEach(warning -> digest.add(warning(warning)));
        return digest.finish();
    }

    static String report(AllocationObservationReport report) {
        AllocationObservationReport value = AllocationValidation.required(report, "report");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_observation_report_v1")
                        .add(value.simulationTick())
                        .add(value.registryDigest())
                        .add(value.requestDigest())
                        .add(value.schemaVersion());
        value.providerIds().forEach(id -> digest.add("provider").add(id.value()));
        value.providerResultDigests().forEach(
                (id, resultDigest) -> digest
                        .add("result")
                        .add(id.value())
                        .add(resultDigest)
        );
        value.failures().forEach(failure -> digest.add(failure(failure)));
        value.warnings().forEach(warning -> digest.add(warning(warning)));
        summary(digest, value.summary());
        return digest.finish();
    }

    static String bundle(AllocationObservationBundle bundle) {
        AllocationObservationBundle value = AllocationValidation.required(bundle, "bundle");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_observation_bundle_v1")
                        .add(value.simulationTick())
                        .add(value.status().name())
                        .add(value.report().canonicalDigest())
                        .add(value.schemaVersion());
        value.providerIds().forEach(id -> digest.add("provider").add(id.value()));
        value.providerResults().forEach(result -> digest.add(result.canonicalDigest()));
        value.resources().forEach(resource -> digest.add(resource.canonicalDigest()));
        value.capacities().forEach(capacity -> digest.add(capacity.canonicalDigest()));
        value.failures().forEach(failure -> digest.add(failure.canonicalDigest()));
        value.warnings().forEach(warning -> digest.add(warning.canonicalDigest()));
        metadata(digest, value.scopeMetadata());
        return digest.finish();
    }

    static String validateDigest(String value, String field) {
        String digest = AllocationValidation.required(value, field);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_CONTEXT,
                    AllocationProviderFailureScope.REQUEST,
                    field,
                    field + " must be a lowercase SHA-256 digest"
            );
        }
        return digest;
    }

    private static void summary(
            AllocationCanonicalDigest digest,
            AllocationObservationSummary summary
    ) {
        digest.add(summary.providerCount())
                .add(summary.successfulProviderCount())
                .add(summary.failedProviderCount())
                .add(summary.resourceCount())
                .add(summary.capacityCount())
                .add(summary.warningCount())
                .add(summary.failureCount())
                .add(summary.deterministicOperationCount());
    }

    private static void metadata(
            AllocationCanonicalDigest digest,
            AllocationMetadata metadata
    ) {
        Map<String, AllocationMetadataValue> values =
                AllocationValidation.required(metadata, "metadata").values();
        digest.add(values.size());
        values.forEach((key, value) -> digest
                .add(key)
                .add(value.type().serializedName())
                .add(value.canonicalValue()));
    }
}
