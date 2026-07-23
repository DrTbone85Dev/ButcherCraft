package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class AllocationCycleDigestSupport {
    private AllocationCycleDigestSupport() {
    }

    static String input(AllocationCycleInput input) {
        AllocationCycleInput source = AllocationValidation.required(input, "input");
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_input_v1")
                        .add(source.context().cycleId().value())
                        .add(source.context().simulationTick())
                        .add(source.context().policyId().value())
                        .add(source.context().schemaVersion());
        metadata(digest, source.context().metadata());
        source.resources().forEach(resource -> {
            digest.add("resource")
                    .add(resource.resourceId().value())
                    .add(resource.resourceCategory().value())
                    .add(resource.authoritativeProviderId().value())
                    .add(resource.authoritativeExternalReference().canonicalKey())
                    .add(resource.availability().serializedName())
                    .add(resource.exclusivityMode().serializedName())
                    .add(resource.observationSimulationTick())
                    .add(resource.schemaVersion());
            metadata(digest, resource.metadata());
        });
        source.capacities().forEach(capacity -> {
            digest.add("capacity")
                    .add(capacity.capacityId().value())
                    .add(capacity.resourceId().value())
                    .add(capacity.capacityTypeId().value())
                    .add(capacity.observedAmount().canonicalAmount())
                    .add(capacity.capacityUnitId().value())
                    .add(capacity.observationSimulationTick())
                    .add(capacity.authoritativeExternalReference().canonicalKey())
                    .add(capacity.schemaVersion());
            metadata(digest, capacity.metadata());
        });
        source.definitions().requirements().forEach(requirement -> {
            digest.add("requirement")
                    .add(requirement.id().value())
                    .add(requirement.allocationSetId().value())
                    .add(requirement.executionWorkReference().canonicalKey())
                    .add(requirement.resourceCategory().value())
                    .add(requirement.capacityTypeId().value());
            optionalId(
                    digest,
                    requirement.exactResourceId().map(ResourceId::value)
            );
            digest.add(requirement.requiredQuantity().canonicalAmount())
                    .add(requirement.capacityUnitId().value())
                    .add(requirement.creationSimulationTick())
                    .add(requirement.schemaVersion());
            metadata(digest, requirement.metadata());
        });
        source.definitions().requests().forEach(request -> {
            digest.add("request")
                    .add(request.id().value())
                    .add(request.allocationSetId().value())
                    .add(request.executionWorkReference().canonicalKey());
            request.requirementIds().forEach(id -> digest.add(id.value()));
            orderingContext(digest, request.orderingContext());
            digest.add(request.creationSimulationTick())
                    .add(request.schemaVersion());
            metadata(digest, request.metadata());
        });
        source.definitions().sets().forEach(set -> {
            digest.add("set")
                    .add(set.id().value())
                    .add(set.executionWorkReference().canonicalKey())
                    .add(set.sourceRequestId().value());
            set.requirementIds().forEach(id -> digest.add(id.value()));
            digest.add(set.planningCycleReference().canonicalKey())
                    .add(set.creationSimulationTick());
            optionalLong(digest, set.expirationSimulationTick());
            digest.add(set.schemaVersion());
            metadata(digest, set.metadata());
        });
        source.definitions().commitments().forEach(commitment ->
                commitment(digest, commitment));
        source.runtimes().views().forEach(runtime -> runtime(digest, runtime));
        source.candidateSetIds().forEach(id ->
                digest.add("candidate").add(id.value()));
        return digest.finish();
    }

    static String ordering(
            List<AllocationSetDefinition> sets,
            AllocationRegistry definitions
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_ordering_v1");
        for (AllocationSetDefinition set : sets) {
            AllocationRequestDefinition request = definitions.findRequest(
                    set.sourceRequestId()
            ).orElseThrow();
            digest.add(set.id().value()).add(request.id().value());
            orderingContext(digest, request.orderingContext());
        }
        return digest.finish();
    }

    static String commitments(
            List<AllocationCommitmentDefinition> commitments
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_commitments_v1");
        commitments.stream().sorted().forEach(value -> commitment(digest, value));
        return digest.finish();
    }

    static String report(AllocationReport report) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_report_v1")
                        .add(report.allocationCycleId().value())
                        .add(report.simulationTick())
                        .add(report.policyId().value())
                        .add(report.schemaVersion());
        ids(digest, "successful", report.successfulSetIds());
        ids(digest, "waiting", report.waitingSetIds());
        ids(digest, "rejected", report.rejectedSetIds());
        ids(digest, "failed", report.failedSetIds());
        ids(digest, "released", report.releasedSetIds());
        ids(digest, "expired", report.expiredSetIds());
        report.commitmentIds().forEach(id ->
                digest.add("commitment").add(id.value()));
        report.conflicts().forEach(conflict -> {
            digest.add("conflict")
                    .add(conflict.type().serializedName())
                    .add(conflict.capacityKey().resourceId().value())
                    .add(conflict.capacityKey().capacityTypeId().value())
                    .add(conflict.capacityKey().capacityUnitId().value())
                    .add(conflict.exactShortfall().canonicalAmount());
            ids(digest, "winner", conflict.winnerSetIds());
            ids(digest, "loser", conflict.loserSetIds());
            metadata(digest, conflict.metadata());
        });
        report.capacities().forEach(capacity -> digest
                .add("capacity")
                .add(capacity.capacityKey().resourceId().value())
                .add(capacity.capacityKey().capacityTypeId().value())
                .add(capacity.capacityKey().capacityUnitId().value())
                .add(capacity.observedQuantity().canonicalAmount())
                .add(capacity.committedQuantity().canonicalAmount())
                .add(capacity.remainingQuantity().canonicalAmount()));
        report.orderingContexts().forEach(ordering -> {
            digest.add("ordering").add(ordering.requestId().value());
            orderingContext(digest, ordering.orderingContext());
        });
        report.workSummary().stageCounts().forEach((stage, count) ->
                digest.add(stage).add(count));
        digest.add(report.workSummary().consumedWorkUnits())
                .add(report.workSummary().maximumWorkUnits())
                .add(report.workSummary().truncated());
        report.failures().forEach(failure -> digest
                .add("failure")
                .add(failure.code().name())
                .add(failure.subject())
                .add(failure.message()));
        return digest.finish();
    }

    static String publication(
            AllocationRegistry definitions,
            AllocationRuntimeRegistry runtimes,
            AllocationReportRegistry reports,
            AllocationHistory history,
            AllocationCycleTraceRegistry traces
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_publication_v1");
        definitions.commitments().forEach(commitment ->
                commitment(digest, commitment));
        runtimes.views().forEach(runtime -> runtime(digest, runtime));
        reports.reports().forEach(report ->
                digest.add(report(report)));
        history.records().forEach(record -> {
            digest.add(record.allocationSetId().value());
            optionalEnum(digest, record.previousStatus());
            digest.add(record.status().name())
                    .add(record.transitionSimulationTick())
                    .add(record.revision());
            optionalEnum(digest, record.failureCode());
            optionalId(digest, record.failureMessage());
            digest.add(record.schemaVersion());
        });
        traces.traces().forEach(trace ->
                digest.add(trace.cycleId().value()).add(trace.traceDigest()));
        return digest.finish();
    }

    static String result(
            AllocationCycleId cycleId,
            long tick,
            List<AllocationSetId> ordered,
            List<AllocationSetId> successful,
            List<AllocationSetId> waiting,
            List<AllocationSetId> failed,
            List<AllocationCycleFailure> failures,
            AllocationCycleSummary summary,
            AllocationCycleTrace trace,
            String inputDigest,
            String orderingDigest,
            String initialLedgerDigest,
            String finalLedgerDigest,
            String commitmentDigest,
            String reportDigest,
            String publicationDigest
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_result_v1")
                        .add(cycleId.value())
                        .add(tick);
        ids(digest, "ordered", ordered);
        ids(digest, "successful", successful);
        ids(digest, "waiting", waiting);
        ids(digest, "failed", failed);
        failures.stream().sorted().forEach(failure -> digest
                .add(failure.scope().name())
                .add(failure.code().name())
                .add(failure.subject())
                .add(failure.message()));
        digest.add(summary.observedResourceCount())
                .add(summary.observedCapacityCount())
                .add(summary.activeCommitmentCount())
                .add(summary.evaluatedSetCount())
                .add(summary.successfulSetCount())
                .add(summary.waitingSetCount())
                .add(summary.failedSetCount())
                .add(summary.createdCommitmentCount())
                .add(summary.conflictCount())
                .add(summary.deterministicOperationCount())
                .add(trace.traceDigest())
                .add(inputDigest)
                .add(orderingDigest)
                .add(initialLedgerDigest)
                .add(finalLedgerDigest)
                .add(commitmentDigest)
                .add(reportDigest)
                .add(publicationDigest);
        return digest.finish();
    }

    private static void commitment(
            AllocationCanonicalDigest digest,
            AllocationCommitmentDefinition commitment
    ) {
        digest.add("commitment")
                .add(commitment.id().value())
                .add(commitment.allocationCycleId().value())
                .add(commitment.allocationSetId().value())
                .add(commitment.requirementId().value())
                .add(commitment.resourceId().value())
                .add(commitment.capacityId().value())
                .add(commitment.committedQuantity().canonicalAmount())
                .add(commitment.capacityUnitId().value())
                .add(commitment.createdSimulationTick());
        optionalLong(digest, commitment.expirationSimulationTick());
        commitment.sourceObservationReferences().forEach(reference ->
                digest.add(reference.canonicalKey()));
        metadata(digest, commitment.metadata());
        digest.add(commitment.schemaVersion());
    }

    private static void runtime(
            AllocationCanonicalDigest digest,
            AllocationRuntimeView runtime
    ) {
        digest.add("runtime")
                .add(runtime.allocationSetId().value())
                .add(runtime.status().name())
                .add(runtime.createdSimulationTick());
        optionalLong(digest, runtime.waitingSimulationTick());
        optionalLong(digest, runtime.allocatedSimulationTick());
        optionalLong(digest, runtime.activatedSimulationTick());
        optionalLong(digest, runtime.releasedSimulationTick());
        optionalLong(digest, runtime.expirationSimulationTick());
        digest.add(runtime.lastUpdatedSimulationTick());
        runtime.commitmentIds().forEach(id -> digest.add(id.value()));
        optionalEnum(digest, runtime.failureCode());
        optionalId(digest, runtime.failureMessage());
        metadata(digest, runtime.metadata());
        digest.add(runtime.revision()).add(runtime.schemaVersion());
    }

    private static void orderingContext(
            AllocationCanonicalDigest digest,
            AllocationOrderingContext context
    ) {
        digest.add(context.horizonPrecedence())
                .add(context.priority());
        optionalLong(digest, context.requiredBySimulationTick());
        digest.add(context.needCreationSimulationTick())
                .add(context.planningCycleReference().canonicalKey())
                .add(context.sourceApprovedPlanReference().canonicalKey())
                .add(context.requestCreationSimulationTick())
                .add(context.stableRequestSequence());
    }

    private static void metadata(
            AllocationCanonicalDigest digest,
            AllocationMetadata metadata
    ) {
        digest.add(metadata.values().size());
        metadata.values().forEach((key, value) -> digest
                .add(key)
                .add(value.type().serializedName())
                .add(value.canonicalValue()));
    }

    private static void optionalLong(
            AllocationCanonicalDigest digest,
            OptionalLong value
    ) {
        digest.add(value.isPresent());
        if (value.isPresent()) {
            digest.add(value.getAsLong());
        }
    }

    private static void optionalId(
            AllocationCanonicalDigest digest,
            Optional<String> value
    ) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }

    private static <E extends Enum<E>> void optionalEnum(
            AllocationCanonicalDigest digest,
            Optional<E> value
    ) {
        digest.add(value.isPresent());
        value.ifPresent(entry -> digest.add(entry.name()));
    }

    private static void ids(
            AllocationCanonicalDigest digest,
            String category,
            List<AllocationSetId> ids
    ) {
        ids.forEach(id -> digest.add(category).add(id.value()));
    }
}
