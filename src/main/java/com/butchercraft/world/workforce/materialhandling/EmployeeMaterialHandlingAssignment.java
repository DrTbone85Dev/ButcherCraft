package com.butchercraft.world.workforce.materialhandling;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.Objects;
import java.util.Optional;

public record EmployeeMaterialHandlingAssignment(
        int schemaVersion,
        EmployeeMaterialHandlingAssignmentId assignmentId,
        WorldIdentityRootIdentity worldIdentity,
        EmployeeId employeeId,
        MaterialTransferId transferId,
        WorkstationEndpointReference source,
        WorkstationEndpointReference destination,
        EmployeeMaterialHandlingAssignmentState state,
        long revision,
        long createdTick,
        Optional<EmployeeMaterialHandlingFailure> failure,
        String configurationIdentity,
        String contentDigest
) implements Comparable<EmployeeMaterialHandlingAssignment> {
    public EmployeeMaterialHandlingAssignment {
        if (schemaVersion != EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported employee Material Handling assignment schema: " + schemaVersion);
        }
        assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        employeeId = Objects.requireNonNull(employeeId, "employeeId");
        transferId = Objects.requireNonNull(transferId, "transferId");
        source = Objects.requireNonNull(source, "source");
        destination = Objects.requireNonNull(destination, "destination");
        state = Objects.requireNonNull(state, "state");
        if (revision <= 0L || createdTick < 0L) {
            throw new IllegalArgumentException("Assignment revision must be positive and created tick non-negative");
        }
        failure = Objects.requireNonNull(failure, "failure");
        configurationIdentity = EmployeeMaterialHandlingAssignmentId.requireIdentity(
                configurationIdentity,
                "configurationIdentity"
        );
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        if (!assignmentId.equals(EmployeeMaterialHandlingAssignmentId.create(
                worldIdentity,
                employeeId,
                transferId,
                configurationIdentity
        ))) {
            throw new IllegalArgumentException("Employee Material Handling assignment identity is not canonical");
        }
        if (!source.endpointKey().dimensionIdentity().equals(destination.endpointKey().dimensionIdentity())) {
            throw new IllegalArgumentException("Employee Material Handling endpoints must share one dimension");
        }
        String expectedDigest = calculateDigest(
                assignmentId,
                employeeId,
                transferId,
                source,
                destination,
                state,
                revision,
                createdTick,
                failure,
                configurationIdentity
        );
        if (!expectedDigest.equals(contentDigest)) {
            throw new IllegalArgumentException("Employee Material Handling assignment digest is not canonical");
        }
        if ((state == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED
                || state == EmployeeMaterialHandlingAssignmentState.FAILED) && failure.isEmpty()) {
            throw new IllegalArgumentException("Failure and recovery assignment states require a typed failure");
        }
    }

    public static EmployeeMaterialHandlingAssignment create(
            WorldIdentityRootIdentity worldIdentity,
            EmployeeId employeeId,
            MaterialTransferId transferId,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            long revision,
            long createdTick
    ) {
        String configuration = EmployeeMaterialHandlingAssignmentSchema.CONFIGURATION_IDENTITY;
        EmployeeMaterialHandlingAssignmentId id = EmployeeMaterialHandlingAssignmentId.create(
                worldIdentity,
                employeeId,
                transferId,
                configuration
        );
        EmployeeMaterialHandlingAssignmentState state = EmployeeMaterialHandlingAssignmentState.IDLE;
        Optional<EmployeeMaterialHandlingFailure> failure = Optional.empty();
        return new EmployeeMaterialHandlingAssignment(
                EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION,
                id,
                worldIdentity,
                employeeId,
                transferId,
                source,
                destination,
                state,
                revision,
                createdTick,
                failure,
                configuration,
                calculateDigest(id, employeeId, transferId, source, destination, state, revision, createdTick,
                        failure, configuration)
        );
    }

    public EmployeeMaterialHandlingAssignment transition(
            EmployeeMaterialHandlingAssignmentState target,
            long nextRevision,
            Optional<EmployeeMaterialHandlingFailure> nextFailure
    ) {
        Objects.requireNonNull(target, "target");
        if (!state.canTransitionTo(target)) {
            throw new IllegalArgumentException("Illegal employee Material Handling transition: " + state + " -> " + target);
        }
        if (nextRevision <= revision) {
            throw new IllegalArgumentException("Employee Material Handling assignment revision must advance");
        }
        Optional<EmployeeMaterialHandlingFailure> normalizedFailure = Objects.requireNonNull(nextFailure, "nextFailure");
        return new EmployeeMaterialHandlingAssignment(
                schemaVersion,
                assignmentId,
                worldIdentity,
                employeeId,
                transferId,
                source,
                destination,
                target,
                nextRevision,
                createdTick,
                normalizedFailure,
                configurationIdentity,
                calculateDigest(assignmentId, employeeId, transferId, source, destination, target, nextRevision,
                        createdTick, normalizedFailure, configurationIdentity)
        );
    }

    public boolean active() {
        return !state.terminal();
    }

    public boolean binds(WorkstationEndpointReference expectedSource, WorkstationEndpointReference expectedDestination) {
        return source.equals(expectedSource) && destination.equals(expectedDestination);
    }

    private static String calculateDigest(
            EmployeeMaterialHandlingAssignmentId id,
            EmployeeId employeeId,
            MaterialTransferId transferId,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            EmployeeMaterialHandlingAssignmentState state,
            long revision,
            long createdTick,
            Optional<EmployeeMaterialHandlingFailure> failure,
            String configurationIdentity
    ) {
        return EmployeeMaterialHandlingDigest.sha256(String.join("\n",
                id.value(),
                employeeId.value(),
                transferId.value(),
                source.instanceId().value(),
                Long.toString(source.generation()),
                destination.instanceId().value(),
                Long.toString(destination.generation()),
                state.name(),
                Long.toString(revision),
                Long.toString(createdTick),
                failure.map(value -> value.code().serializedName()).orElse("none"),
                failure.map(EmployeeMaterialHandlingFailure::detail).orElse("none"),
                configurationIdentity
        ));
    }

    @Override
    public int compareTo(EmployeeMaterialHandlingAssignment other) {
        return assignmentId.compareTo(other.assignmentId);
    }
}
