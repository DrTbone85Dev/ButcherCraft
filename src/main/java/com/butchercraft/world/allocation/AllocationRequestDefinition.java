package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record AllocationRequestDefinition(
        AllocationRequestId id,
        AllocationSetId allocationSetId,
        ExternalReference executionWorkReference,
        List<RequirementId> requirementIds,
        AllocationOrderingContext orderingContext,
        long creationSimulationTick,
        AllocationMetadata metadata,
        int schemaVersion
) {
    public AllocationRequestDefinition {
        List<AllocationValidationFailure> failures = AllocationValidation.failures();
        AllocationValidation.required(id, "id", failures);
        AllocationValidation.required(allocationSetId, "allocationSetId", failures);
        AllocationValidation.required(executionWorkReference, "executionWorkReference", failures);
        AllocationValidation.required(requirementIds, "requirementIds", failures);
        AllocationValidation.required(orderingContext, "orderingContext", failures);
        AllocationValidation.required(metadata, "metadata", failures);
        if (requirementIds != null) {
            List<RequirementId> copy = new ArrayList<>(requirementIds.size());
            for (RequirementId requirementId : requirementIds) {
                if (requirementId == null) {
                    AllocationValidation.add(
                            failures,
                            AllocationValidationFailureCode.NULL_VALUE,
                            "requirementIds",
                            "Allocation request requirement id is required"
                    );
                } else {
                    copy.add(requirementId);
                }
            }
            copy.sort(Comparator.naturalOrder());
            if (copy.isEmpty()) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.EMPTY_REQUIREMENTS,
                        "requirementIds",
                        "Allocation request requires at least one Requirement"
                );
            }
            if (copy.size() > AllocationSchema.MAXIMUM_REQUIREMENTS_PER_SET) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                        "requirementIds",
                        "Allocation request exceeds the schema-1 Requirement limit"
                );
            }
            Set<RequirementId> unique = new HashSet<>(copy);
            if (unique.size() != copy.size()) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.DUPLICATE_REQUIREMENT,
                        "requirementIds",
                        "Allocation request contains duplicate Requirement ids"
                );
            }
            requirementIds = List.copyOf(copy);
        }
        if (creationSimulationTick < 0L) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    "creationSimulationTick",
                    "Allocation request creation tick must not be negative"
            );
        }
        if (orderingContext != null
                && creationSimulationTick != orderingContext.requestCreationSimulationTick()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_ORDERING_CONTEXT,
                    "creationSimulationTick",
                    "Allocation request creation tick must match its ordering context"
            );
        }
        if (schemaVersion != AllocationSchema.CURRENT_VERSION) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SCHEMA_VERSION,
                    "schemaVersion",
                    "Unsupported Allocation schema version: " + schemaVersion
            );
        }
        if (failures.isEmpty()) {
            AllocationRequestId expected = AllocationIds.requestId(
                    executionWorkReference,
                    orderingContext,
                    schemaVersion
            );
            if (!expected.equals(id)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.NONCANONICAL_INPUT,
                        "id",
                        "Allocation request id does not match its canonical structural identity"
                );
            }
            AllocationSetId expectedSetId = AllocationIds.setId(
                    orderingContext.sourceApprovedPlanReference(),
                    executionWorkReference,
                    id,
                    schemaVersion
            );
            if (!expectedSetId.equals(allocationSetId)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                        "allocationSetId",
                        "Allocation request set id does not match its canonical association"
                );
            }
        }
        AllocationValidation.throwIfAny(failures);
    }

    public static AllocationRequestDefinition create(
            AllocationSetId allocationSetId,
            ExternalReference executionWorkReference,
            Collection<RequirementDefinition> requirements,
            AllocationOrderingContext orderingContext,
            AllocationMetadata metadata
    ) {
        Collection<RequirementDefinition> source = AllocationValidation.required(
                requirements,
                "requirements"
        );
        List<AllocationValidationFailure> failures = AllocationValidation.failures();
        List<RequirementId> ids = new ArrayList<>(source.size());
        for (RequirementDefinition requirement : source) {
            if (requirement == null) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.NULL_VALUE,
                        "requirements",
                        "Allocation request Requirement is required"
                );
                continue;
            }
            ids.add(requirement.id());
            if (!requirement.allocationSetId().equals(allocationSetId)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                        "requirements",
                        "Requirement " + requirement.id().value() + " belongs to another AllocationSet"
                );
            }
            if (!requirement.executionWorkReference().equals(executionWorkReference)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_EXECUTION_REFERENCE,
                        "requirements",
                        "Requirement " + requirement.id().value() + " refers to different execution work"
                );
            }
        }
        AllocationValidation.throwIfAny(failures);
        AllocationRequestId id = AllocationIds.requestId(
                executionWorkReference,
                orderingContext,
                AllocationSchema.CURRENT_VERSION
        );
        return new AllocationRequestDefinition(
                id,
                allocationSetId,
                executionWorkReference,
                ids,
                orderingContext,
                orderingContext.requestCreationSimulationTick(),
                metadata,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public static Comparator<AllocationRequestDefinition> canonicalComparator(long currentSimulationTick) {
        long current = AllocationValidation.tick(currentSimulationTick, "currentSimulationTick");
        return Comparator
                .comparingInt((AllocationRequestDefinition request) ->
                        request.orderingContext().horizonPrecedence())
                .thenComparing(Comparator.comparingInt(
                        (AllocationRequestDefinition request) ->
                                request.orderingContext().priority()).reversed())
                .thenComparingLong(request ->
                        request.orderingContext().requiredBySimulationTick().orElse(Long.MAX_VALUE))
                .thenComparing(Comparator.comparingLong(
                        (AllocationRequestDefinition request) ->
                                request.orderingContext().starvationAge(current)).reversed())
                .thenComparingLong(request ->
                        request.orderingContext().needCreationSimulationTick())
                .thenComparingLong(request ->
                        request.orderingContext().stableRequestSequence())
                .thenComparing(AllocationRequestDefinition::id);
    }
}
