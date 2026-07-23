package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

public final class AllocationObservationService {
    private static final String PROVIDER_EXCEPTION_MESSAGE =
            "Provider threw an unexpected exception during observation";

    private final AllocationProviderRegistry registry;

    public AllocationObservationService(AllocationProviderRegistry registry) {
        this.registry = AllocationValidation.required(registry, "registry");
    }

    public AllocationProviderRegistry registry() {
        return registry;
    }

    public AllocationObservationOperationResult observe(
            AllocationObservationRequest request
    ) {
        if (request == null) {
            return AllocationObservationOperationResult.rejected(List.of(
                    AllocationProviderFailure.global(
                            AllocationProviderFailureCode.INVALID_REQUEST,
                            AllocationProviderFailureScope.REQUEST,
                            "request",
                            "Observation request is required",
                            OptionalLong.empty()
                    )
            ));
        }

        List<AllocationProviderId> providerIds = selectedProviders(request);
        List<AllocationProviderFailure> selectionFailures =
                validateProviderSelection(providerIds);
        if (!selectionFailures.isEmpty()) {
            return AllocationObservationOperationResult.rejected(selectionFailures);
        }

        List<AllocationObservationResult> results = new ArrayList<>(providerIds.size());
        List<ObservedResourceSnapshot> resources = new ArrayList<>();
        List<ObservedCapacitySnapshot> capacities = new ArrayList<>();
        List<AllocationProviderWarning> warnings = new ArrayList<>();
        List<AllocationProviderFailure> failures = new ArrayList<>();

        for (AllocationProviderId providerId : providerIds) {
            AllocationResourceProvider provider = registry.find(providerId).orElseThrow();
            AllocationProviderDescriptor descriptor =
                    registry.findDescriptor(providerId).orElseThrow();
            AllocationObservationResult result = invoke(
                    providerId,
                    provider,
                    request.context()
            );
            List<AllocationProviderFailure> resultFailures =
                    validateResult(result, descriptor, request.context());
            if (!resultFailures.isEmpty()) {
                result = AllocationObservationResult.failure(
                        providerId,
                        request.context().simulationTick(),
                        resultFailures
                );
            } else if (result.successful()
                    && exceedsTotalBounds(request, resources, capacities, result)) {
                AllocationProviderFailure failure = providerFailure(
                        AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                        AllocationProviderFailureScope.PROVIDER,
                        providerId,
                        providerId.value(),
                        "Provider result would exceed the observation request total bound",
                        request.context().simulationTick()
                );
                result = AllocationObservationResult.failure(
                        providerId,
                        request.context().simulationTick(),
                        List.of(failure)
                );
            }

            results.add(result);
            failures.addAll(result.failures());
            if (result.successful()) {
                resources.addAll(result.resources());
                capacities.addAll(result.capacities());
                warnings.addAll(result.warnings());
            }
        }

        failures.addAll(validateAggregation(results, request.context().simulationTick()));
        warnings = warnings.stream().sorted().distinct().toList();
        if (warnings.size() > AllocationSchema.MAXIMUM_PROVIDER_WARNINGS) {
            warnings = List.copyOf(warnings.subList(
                    0,
                    AllocationSchema.MAXIMUM_PROVIDER_WARNINGS
            ));
            failures.add(AllocationProviderFailure.global(
                    AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.BUNDLE,
                    "warnings",
                    "Observation warning evidence exceeded the retained schema bound",
                    OptionalLong.of(request.context().simulationTick())
            ));
        }
        failures = boundedFailures(
                failures,
                request.context().simulationTick()
        );

        AllocationObservationBundleStatus status = bundleStatus(failures);
        int successfulProviders = (int) results.stream()
                .filter(AllocationObservationResult::successful)
                .count();
        long operationCount = operationCount(
                results.size(),
                resources.size(),
                capacities.size(),
                failures.size(),
                warnings.size()
        );
        AllocationObservationSummary summary = new AllocationObservationSummary(
                results.size(),
                successfulProviders,
                results.size() - successfulProviders,
                resources.size(),
                capacities.size(),
                warnings.size(),
                failures.size(),
                operationCount
        );
        Map<AllocationProviderId, String> resultDigests = new LinkedHashMap<>();
        results.forEach(result ->
                resultDigests.put(result.providerId(), result.canonicalDigest()));
        AllocationObservationReport report = new AllocationObservationReport(
                request.context().simulationTick(),
                providerIds,
                resultDigests,
                failures,
                warnings,
                summary,
                registry.canonicalDigest(),
                request.canonicalDigest(),
                AllocationSchema.CURRENT_VERSION
        );
        AllocationObservationBundle bundle = new AllocationObservationBundle(
                request.context().simulationTick(),
                providerIds,
                results,
                resources,
                capacities,
                failures,
                warnings,
                status,
                report,
                request.context().scopeMetadata(),
                AllocationSchema.CURRENT_VERSION
        );
        return AllocationObservationOperationResult.accepted(bundle);
    }

    private List<AllocationProviderId> selectedProviders(
            AllocationObservationRequest request
    ) {
        if (request.selectedProviderIds().isEmpty()) {
            return registry.descriptors().stream()
                    .map(AllocationProviderDescriptor::providerId)
                    .toList();
        }
        return request.selectedProviderIds();
    }

    private List<AllocationProviderFailure> validateProviderSelection(
            List<AllocationProviderId> providerIds
    ) {
        List<AllocationProviderFailure> failures = new ArrayList<>();
        for (AllocationProviderId providerId : providerIds) {
            if (!registry.contains(providerId)) {
                failures.add(AllocationProviderFailure.global(
                        AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                        AllocationProviderFailureScope.REQUEST,
                        providerId.value(),
                        "Observation request selects an unknown provider",
                        OptionalLong.empty()
                ));
            }
        }
        return failures.stream().sorted().distinct().toList();
    }

    private AllocationObservationResult invoke(
            AllocationProviderId providerId,
            AllocationResourceProvider provider,
            AllocationObservationContext context
    ) {
        try {
            AllocationObservationResult result = provider.observe(context);
            if (result == null) {
                return AllocationObservationResult.failure(
                        providerId,
                        context.simulationTick(),
                        List.of(providerFailure(
                                AllocationProviderFailureCode.PROVIDER_EXCEPTION,
                                AllocationProviderFailureScope.PROVIDER,
                                providerId,
                                providerId.value(),
                                "Provider returned no observation result",
                                context.simulationTick()
                        ))
                );
            }
            return result;
        } catch (AllocationProviderValidationException exception) {
            AllocationProviderFailureCode code = exception.failures().stream()
                    .map(AllocationProviderFailure::code)
                    .sorted()
                    .findFirst()
                    .orElse(AllocationProviderFailureCode.INVALID_RESOURCE_SNAPSHOT);
            return invalidConstructionResult(
                    providerId,
                    context,
                    code,
                    "Provider constructed an invalid observation result"
            );
        } catch (AllocationValidationException exception) {
            AllocationProviderFailureCode code = exception.failures().stream()
                    .map(AllocationValidationFailure::code)
                    .map(AllocationObservationService::providerCode)
                    .sorted()
                    .findFirst()
                    .orElse(AllocationProviderFailureCode.INVALID_RESOURCE_SNAPSHOT);
            return invalidConstructionResult(
                    providerId,
                    context,
                    code,
                    "Provider constructed an invalid observation snapshot"
            );
        } catch (RuntimeException exception) {
            return AllocationObservationResult.failure(
                    providerId,
                    context.simulationTick(),
                    List.of(providerFailure(
                            AllocationProviderFailureCode.PROVIDER_EXCEPTION,
                            AllocationProviderFailureScope.PROVIDER,
                            providerId,
                            providerId.value(),
                            PROVIDER_EXCEPTION_MESSAGE,
                            context.simulationTick()
                    ))
            );
        }
    }

    private List<AllocationProviderFailure> validateResult(
            AllocationObservationResult result,
            AllocationProviderDescriptor descriptor,
            AllocationObservationContext context
    ) {
        List<AllocationProviderFailure> failures = new ArrayList<>();
        AllocationProviderId expectedProviderId = descriptor.providerId();
        if (!result.providerId().equals(expectedProviderId)) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.PROVIDER_ID_MISMATCH,
                    AllocationProviderFailureScope.PROVIDER,
                    expectedProviderId,
                    result.providerId().value(),
                    "Provider result identity does not match its registry identity",
                    context.simulationTick()
            ));
        }
        if (result.simulationTick() != context.simulationTick()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.TICK_MISMATCH,
                    AllocationProviderFailureScope.PROVIDER,
                    expectedProviderId,
                    expectedProviderId.value(),
                    "Provider result observation tick does not match the request",
                    context.simulationTick()
            ));
        }
        if (result.schemaVersion() != context.schemaVersion()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.SCHEMA_MISMATCH,
                    AllocationProviderFailureScope.PROVIDER,
                    expectedProviderId,
                    expectedProviderId.value(),
                    "Provider result schema does not match the request",
                    context.simulationTick()
            ));
        }
        validateFailureResult(result, descriptor, context, failures);
        if (result.successful()) {
            validateSuccessfulResult(result, descriptor, context, failures);
        }
        return failures.stream().sorted().distinct().toList();
    }

    private void validateFailureResult(
            AllocationObservationResult result,
            AllocationProviderDescriptor descriptor,
            AllocationObservationContext context,
            List<AllocationProviderFailure> failures
    ) {
        for (AllocationProviderFailure failure : result.failures()) {
            if (failure.providerId().isEmpty()
                    || !failure.providerId().get().equals(descriptor.providerId())) {
                failures.add(providerFailure(
                        AllocationProviderFailureCode.PROVIDER_ID_MISMATCH,
                        AllocationProviderFailureScope.PROVIDER,
                        descriptor.providerId(),
                        failure.subject(),
                        "Provider failure evidence does not identify its provider",
                        context.simulationTick()
                ));
            }
            if (failure.simulationTick().isEmpty()
                    || failure.simulationTick().getAsLong() != context.simulationTick()) {
                failures.add(providerFailure(
                        AllocationProviderFailureCode.TICK_MISMATCH,
                        AllocationProviderFailureScope.PROVIDER,
                        descriptor.providerId(),
                        failure.subject(),
                        "Provider failure evidence does not match the request tick",
                        context.simulationTick()
                ));
            }
            if (failure.scope() == AllocationProviderFailureScope.REGISTRY
                    || failure.scope() == AllocationProviderFailureScope.REQUEST
                    || failure.scope() == AllocationProviderFailureScope.BUNDLE) {
                failures.add(providerFailure(
                        AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                        AllocationProviderFailureScope.PROVIDER,
                        descriptor.providerId(),
                        failure.subject(),
                        "A provider cannot emit registry, request, or bundle-scoped failure",
                        context.simulationTick()
                ));
            }
        }
    }

    private void validateSuccessfulResult(
            AllocationObservationResult result,
            AllocationProviderDescriptor descriptor,
            AllocationObservationContext context,
            List<AllocationProviderFailure> failures
    ) {
        if (result.resources().size() > context.maximumResourcesPerProvider()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.PROVIDER,
                    descriptor.providerId(),
                    "resources",
                    "Provider Resource result exceeds the request bound",
                    context.simulationTick()
            ));
        }
        if (result.capacities().size() > context.maximumCapacitiesPerProvider()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.PROVIDER,
                    descriptor.providerId(),
                    "capacities",
                    "Provider Capacity result exceeds the request bound",
                    context.simulationTick()
            ));
        }

        Map<ResourceId, ObservedResourceSnapshot> resourceById = new TreeMap<>();
        for (ObservedResourceSnapshot resource : result.resources()) {
            validateResource(resource, descriptor, context, failures);
            ObservedResourceSnapshot previous = resourceById.putIfAbsent(
                    resource.resourceId(),
                    resource
            );
            if (previous != null) {
                failures.add(providerFailure(
                        previous.equals(resource)
                                ? AllocationProviderFailureCode.DUPLICATE_RESOURCE
                                : AllocationProviderFailureCode.CONFLICTING_OBSERVATION,
                        AllocationProviderFailureScope.RESOURCE,
                        descriptor.providerId(),
                        resource.resourceId().value(),
                        "Provider returned duplicate or conflicting Resource identity",
                        context.simulationTick()
                ));
            }
        }

        Map<CapacityId, ObservedCapacitySnapshot> capacityById = new TreeMap<>();
        Map<CapacityKey, ObservedCapacitySnapshot> capacityByKey = new TreeMap<>();
        for (ObservedCapacitySnapshot capacity : result.capacities()) {
            validateCapacity(
                    capacity,
                    descriptor,
                    context,
                    resourceById,
                    failures
            );
            ObservedCapacitySnapshot previousById = capacityById.putIfAbsent(
                    capacity.capacityId(),
                    capacity
            );
            ObservedCapacitySnapshot previousByKey = capacityByKey.putIfAbsent(
                    capacity.capacityKey(),
                    capacity
            );
            if (previousById != null || previousByKey != null) {
                ObservedCapacitySnapshot previous =
                        previousById != null ? previousById : previousByKey;
                failures.add(providerFailure(
                        previous.equals(capacity)
                                ? AllocationProviderFailureCode.DUPLICATE_CAPACITY
                                : AllocationProviderFailureCode.CONFLICTING_OBSERVATION,
                        AllocationProviderFailureScope.CAPACITY,
                        descriptor.providerId(),
                        capacity.capacityId().value(),
                        "Provider returned duplicate or conflicting Capacity identity",
                        context.simulationTick()
                ));
            }
        }

        for (AllocationProviderWarning warning : result.warnings()) {
            if (!warning.providerId().equals(descriptor.providerId())) {
                failures.add(providerFailure(
                        AllocationProviderFailureCode.PROVIDER_ID_MISMATCH,
                        AllocationProviderFailureScope.PROVIDER,
                        descriptor.providerId(),
                        warning.subject(),
                        "Provider warning identity does not match its provider",
                        context.simulationTick()
                ));
            }
            if (warning.simulationTick() != context.simulationTick()) {
                failures.add(providerFailure(
                        AllocationProviderFailureCode.TICK_MISMATCH,
                        AllocationProviderFailureScope.PROVIDER,
                        descriptor.providerId(),
                        warning.subject(),
                        "Provider warning tick does not match the request",
                        context.simulationTick()
                ));
            }
        }
    }

    private void validateResource(
            ObservedResourceSnapshot resource,
            AllocationProviderDescriptor descriptor,
            AllocationObservationContext context,
            List<AllocationProviderFailure> failures
    ) {
        if (!resource.authoritativeProviderId().equals(descriptor.providerId())) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.PROVIDER_ID_MISMATCH,
                    AllocationProviderFailureScope.RESOURCE,
                    descriptor.providerId(),
                    resource.resourceId().value(),
                    "Resource observation provider does not match its registry provider",
                    context.simulationTick()
            ));
        }
        if (resource.observationSimulationTick() != context.simulationTick()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.TICK_MISMATCH,
                    AllocationProviderFailureScope.RESOURCE,
                    descriptor.providerId(),
                    resource.resourceId().value(),
                    "Resource observation tick does not match the request",
                    context.simulationTick()
            ));
        }
        if (resource.schemaVersion() != context.schemaVersion()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.SCHEMA_MISMATCH,
                    AllocationProviderFailureScope.RESOURCE,
                    descriptor.providerId(),
                    resource.resourceId().value(),
                    "Resource observation schema does not match the request",
                    context.simulationTick()
            ));
        }
        if (!descriptor.supports(resource.resourceCategory())
                || !context.requests(resource.resourceCategory())) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.UNDECLARED_CAPABILITY,
                    AllocationProviderFailureScope.RESOURCE,
                    descriptor.providerId(),
                    resource.resourceId().value(),
                    "Resource category is not declared or requested",
                    context.simulationTick()
            ));
        }
        if (!descriptor.authorizesOwner(
                resource.authoritativeExternalReference().authoritativeSubsystemId()
        )) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.UNAUTHORIZED_OWNER_REFERENCE,
                    AllocationProviderFailureScope.RESOURCE,
                    descriptor.providerId(),
                    resource.resourceId().value(),
                    "Resource observation claims an undeclared authoritative subsystem",
                    context.simulationTick()
            ));
        }
    }

    private void validateCapacity(
            ObservedCapacitySnapshot capacity,
            AllocationProviderDescriptor descriptor,
            AllocationObservationContext context,
            Map<ResourceId, ObservedResourceSnapshot> resourceById,
            List<AllocationProviderFailure> failures
    ) {
        if (capacity.observationSimulationTick() != context.simulationTick()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.TICK_MISMATCH,
                    AllocationProviderFailureScope.CAPACITY,
                    descriptor.providerId(),
                    capacity.capacityId().value(),
                    "Capacity observation tick does not match the request",
                    context.simulationTick()
            ));
        }
        if (capacity.schemaVersion() != context.schemaVersion()) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.SCHEMA_MISMATCH,
                    AllocationProviderFailureScope.CAPACITY,
                    descriptor.providerId(),
                    capacity.capacityId().value(),
                    "Capacity observation schema does not match the request",
                    context.simulationTick()
            ));
        }
        if (!resourceById.containsKey(capacity.resourceId())) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                    AllocationProviderFailureScope.CAPACITY,
                    descriptor.providerId(),
                    capacity.capacityId().value(),
                    "Capacity observation references an unknown Resource in its result",
                    context.simulationTick()
            ));
        }
        if (!descriptor.supports(capacity.capacityTypeId())
                || !descriptor.supports(capacity.capacityUnitId())
                || !context.requests(capacity.capacityTypeId())) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.UNDECLARED_CAPABILITY,
                    AllocationProviderFailureScope.CAPACITY,
                    descriptor.providerId(),
                    capacity.capacityId().value(),
                    "Capacity type or unit is not declared or requested",
                    context.simulationTick()
            ));
        }
        if (!descriptor.authorizesOwner(
                capacity.authoritativeExternalReference().authoritativeSubsystemId()
        )) {
            failures.add(providerFailure(
                    AllocationProviderFailureCode.UNAUTHORIZED_OWNER_REFERENCE,
                    AllocationProviderFailureScope.CAPACITY,
                    descriptor.providerId(),
                    capacity.capacityId().value(),
                    "Capacity observation claims an undeclared authoritative subsystem",
                    context.simulationTick()
            ));
        }
    }

    private boolean exceedsTotalBounds(
            AllocationObservationRequest request,
            List<ObservedResourceSnapshot> resources,
            List<ObservedCapacitySnapshot> capacities,
            AllocationObservationResult result
    ) {
        return resources.size() + result.resources().size()
                > request.maximumTotalResources()
                || capacities.size() + result.capacities().size()
                > request.maximumTotalCapacities();
    }

    private List<AllocationProviderFailure> validateAggregation(
            List<AllocationObservationResult> results,
            long simulationTick
    ) {
        List<AllocationProviderFailure> failures = new ArrayList<>();
        Map<ResourceId, ResourceClaim> resourceById = new TreeMap<>();
        for (AllocationObservationResult result : results) {
            if (!result.successful()) {
                continue;
            }
            for (ObservedResourceSnapshot resource : result.resources()) {
                ResourceClaim current = new ResourceClaim(result.providerId(), resource);
                ResourceClaim previous = resourceById.putIfAbsent(
                        resource.resourceId(),
                        current
                );
                if (previous == null) {
                    continue;
                }
                failures.add(AllocationProviderFailure.global(
                        previous.snapshot().equals(resource)
                                ? AllocationProviderFailureCode.DUPLICATE_RESOURCE
                                : AllocationProviderFailureCode.CONFLICTING_OBSERVATION,
                        AllocationProviderFailureScope.BUNDLE,
                        conflictSubject(
                                resource.resourceId().value(),
                                previous.providerId(),
                                current.providerId()
                        ),
                        "Multiple providers claimed the same Resource identity",
                        OptionalLong.of(simulationTick)
                ));
            }
        }

        Map<CapacityId, CapacityClaim> capacityById = new TreeMap<>();
        Map<CapacityKey, CapacityClaim> capacityByKey = new TreeMap<>();
        for (AllocationObservationResult result : results) {
            if (!result.successful()) {
                continue;
            }
            for (ObservedCapacitySnapshot capacity : result.capacities()) {
                CapacityClaim current = new CapacityClaim(result.providerId(), capacity);
                CapacityClaim previousById = capacityById.putIfAbsent(
                        capacity.capacityId(),
                        current
                );
                CapacityClaim previousByKey = capacityByKey.putIfAbsent(
                        capacity.capacityKey(),
                        current
                );
                if (previousById == null && previousByKey == null) {
                    continue;
                }
                CapacityClaim previous =
                        previousById != null ? previousById : previousByKey;
                failures.add(AllocationProviderFailure.global(
                        previous.snapshot().equals(capacity)
                                ? AllocationProviderFailureCode.DUPLICATE_CAPACITY
                                : AllocationProviderFailureCode.CONFLICTING_OBSERVATION,
                        AllocationProviderFailureScope.BUNDLE,
                        conflictSubject(
                                capacity.capacityId().value(),
                                previous.providerId(),
                                current.providerId()
                        ),
                        "Multiple providers claimed the same Capacity identity or key",
                        OptionalLong.of(simulationTick)
                ));
            }
        }
        return failures.stream().sorted().distinct().toList();
    }

    private static AllocationObservationBundleStatus bundleStatus(
            List<AllocationProviderFailure> failures
    ) {
        if (failures.isEmpty()) {
            return AllocationObservationBundleStatus.COMPLETE;
        }
        return failures.stream()
                .anyMatch(failure -> failure.scope() == AllocationProviderFailureScope.BUNDLE)
                ? AllocationObservationBundleStatus.UNUSABLE
                : AllocationObservationBundleStatus.INCOMPLETE;
    }

    private static long operationCount(int... counts) {
        long result = 0L;
        for (int count : counts) {
            result = Math.addExact(result, count);
        }
        return result;
    }

    private static List<AllocationProviderFailure> boundedFailures(
            List<AllocationProviderFailure> failures,
            long simulationTick
    ) {
        List<AllocationProviderFailure> ordered =
                failures.stream().sorted().distinct().toList();
        if (ordered.size() <= AllocationSchema.MAXIMUM_PROVIDER_FAILURES) {
            return ordered;
        }
        List<AllocationProviderFailure> retained = new ArrayList<>(
                ordered.subList(
                        0,
                        AllocationSchema.MAXIMUM_PROVIDER_FAILURES - 1
                )
        );
        retained.add(AllocationProviderFailure.global(
                AllocationProviderFailureCode.RESULT_LIMIT_EXCEEDED,
                AllocationProviderFailureScope.BUNDLE,
                "failures",
                "Observation failure evidence exceeded the retained schema bound",
                OptionalLong.of(simulationTick)
        ));
        return retained.stream().sorted().distinct().toList();
    }

    private static AllocationObservationResult invalidConstructionResult(
            AllocationProviderId providerId,
            AllocationObservationContext context,
            AllocationProviderFailureCode code,
            String message
    ) {
        return AllocationObservationResult.failure(
                providerId,
                context.simulationTick(),
                List.of(providerFailure(
                        code,
                        AllocationProviderFailureScope.PROVIDER,
                        providerId,
                        providerId.value(),
                        message,
                        context.simulationTick()
                ))
        );
    }

    private static AllocationProviderFailureCode providerCode(
            AllocationValidationFailureCode code
    ) {
        return switch (code) {
            case UNSUPPORTED_SCHEMA_CONCEPT ->
                    AllocationProviderFailureCode.UNSUPPORTED_SCHEMA_CONCEPT;
            case INVALID_SCHEMA_VERSION -> AllocationProviderFailureCode.SCHEMA_MISMATCH;
            case INVALID_SIMULATION_TICK -> AllocationProviderFailureCode.TICK_MISMATCH;
            case NEGATIVE_QUANTITY, ZERO_QUANTITY, INCOMPATIBLE_UNIT,
                    QUANTITY_UNDERFLOW, ARITHMETIC_OVERFLOW ->
                    AllocationProviderFailureCode.INVALID_CAPACITY_SNAPSHOT;
            default -> AllocationProviderFailureCode.INVALID_RESOURCE_SNAPSHOT;
        };
    }

    private static AllocationProviderFailure providerFailure(
            AllocationProviderFailureCode code,
            AllocationProviderFailureScope scope,
            AllocationProviderId providerId,
            String subject,
            String message,
            long simulationTick
    ) {
        return AllocationProviderFailure.provider(
                code,
                scope,
                providerId,
                subject,
                message,
                simulationTick
        );
    }

    private static String conflictSubject(
            String identity,
            AllocationProviderId first,
            AllocationProviderId second
    ) {
        return identity + "|" + first.value() + "|" + second.value();
    }

    private record ResourceClaim(
            AllocationProviderId providerId,
            ObservedResourceSnapshot snapshot
    ) {
    }

    private record CapacityClaim(
            AllocationProviderId providerId,
            ObservedCapacitySnapshot snapshot
    ) {
    }
}
