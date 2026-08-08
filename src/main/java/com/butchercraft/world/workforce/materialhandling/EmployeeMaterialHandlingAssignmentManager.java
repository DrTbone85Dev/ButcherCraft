package com.butchercraft.world.workforce.materialhandling;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EmployeeMaterialHandlingAssignmentManager {
    private long ownerRevision;
    private final Map<EmployeeMaterialHandlingAssignmentId, EmployeeMaterialHandlingAssignment> assignments =
            new LinkedHashMap<>();

    public EmployeeMaterialHandlingAssignmentManager(EmployeeMaterialHandlingAssignmentDirectory directory) {
        EmployeeMaterialHandlingAssignmentDirectory value = Objects.requireNonNull(directory, "directory");
        ownerRevision = value.ownerRevision();
        value.assignments().forEach(assignment -> assignments.put(assignment.assignmentId(), assignment));
    }

    public static EmployeeMaterialHandlingAssignmentManager empty() {
        return new EmployeeMaterialHandlingAssignmentManager(EmployeeMaterialHandlingAssignmentDirectory.empty());
    }

    public synchronized CreateResult createOrObserve(
            WorldIdentityRootIdentity worldIdentity,
            EmployeeId employeeId,
            MaterialTransferId transferId,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            long createdTick
    ) {
        Optional<EmployeeMaterialHandlingAssignment> active = activeFor(employeeId);
        if (active.isPresent()) {
            EmployeeMaterialHandlingAssignment existing = active.orElseThrow();
            if (existing.transferId().equals(transferId) && existing.binds(source, destination)) {
                return CreateResult.observed(existing);
            }
            return CreateResult.conflict(existing);
        }
        EmployeeMaterialHandlingAssignmentId id = EmployeeMaterialHandlingAssignmentId.create(
                worldIdentity,
                employeeId,
                transferId,
                EmployeeMaterialHandlingAssignmentSchema.CONFIGURATION_IDENTITY
        );
        EmployeeMaterialHandlingAssignment existing = assignments.get(id);
        if (existing != null) {
            return CreateResult.observed(existing);
        }
        long revision = Math.addExact(ownerRevision, 1L);
        EmployeeMaterialHandlingAssignment created = EmployeeMaterialHandlingAssignment.create(
                worldIdentity,
                employeeId,
                transferId,
                source,
                destination,
                revision,
                createdTick
        );
        assignments.put(created.assignmentId(), created);
        ownerRevision = revision;
        return CreateResult.created(created);
    }

    public synchronized EmployeeMaterialHandlingAssignment transition(
            EmployeeMaterialHandlingAssignmentId assignmentId,
            EmployeeMaterialHandlingAssignmentState target,
            Optional<EmployeeMaterialHandlingFailure> failure
    ) {
        EmployeeMaterialHandlingAssignment existing = assignments.get(
                Objects.requireNonNull(assignmentId, "assignmentId")
        );
        if (existing == null) {
            throw new IllegalArgumentException("Unknown employee Material Handling assignment: " + assignmentId.value());
        }
        if (existing.state() == target && existing.failure().equals(failure)) {
            return existing;
        }
        long revision = Math.addExact(ownerRevision, 1L);
        EmployeeMaterialHandlingAssignment updated = existing.transition(target, revision, failure);
        assignments.put(updated.assignmentId(), updated);
        ownerRevision = revision;
        return updated;
    }

    public synchronized Optional<EmployeeMaterialHandlingAssignment> activeFor(EmployeeId employeeId) {
        return assignments.values().stream()
                .filter(EmployeeMaterialHandlingAssignment::active)
                .filter(assignment -> assignment.employeeId().equals(employeeId))
                .findFirst();
    }

    public synchronized Optional<EmployeeMaterialHandlingAssignment> latestFor(EmployeeId employeeId) {
        return assignments.values().stream()
                .filter(assignment -> assignment.employeeId().equals(employeeId))
                .max((left, right) -> Long.compare(left.revision(), right.revision()));
    }

    public synchronized Optional<EmployeeMaterialHandlingAssignment> find(
            EmployeeMaterialHandlingAssignmentId assignmentId
    ) {
        return Optional.ofNullable(assignments.get(Objects.requireNonNull(assignmentId, "assignmentId")));
    }

    public synchronized List<EmployeeMaterialHandlingAssignment> assignments() {
        return assignments.values().stream().sorted().toList();
    }

    public synchronized EmployeeMaterialHandlingAssignmentDirectory directory() {
        return new EmployeeMaterialHandlingAssignmentDirectory(
                EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION,
                ownerRevision,
                new ArrayList<>(assignments.values())
        );
    }

    public record CreateResult(CreateStatus status, EmployeeMaterialHandlingAssignment assignment) {
        public CreateResult {
            status = Objects.requireNonNull(status, "status");
            assignment = Objects.requireNonNull(assignment, "assignment");
        }

        static CreateResult created(EmployeeMaterialHandlingAssignment assignment) {
            return new CreateResult(CreateStatus.CREATED, assignment);
        }

        static CreateResult observed(EmployeeMaterialHandlingAssignment assignment) {
            return new CreateResult(CreateStatus.OBSERVED, assignment);
        }

        static CreateResult conflict(EmployeeMaterialHandlingAssignment assignment) {
            return new CreateResult(CreateStatus.CONFLICT, assignment);
        }
    }

    public enum CreateStatus {
        CREATED,
        OBSERVED,
        CONFLICT
    }
}
