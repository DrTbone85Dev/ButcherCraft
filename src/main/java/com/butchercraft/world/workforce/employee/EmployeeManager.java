package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.business.runtime.BusinessScheduleBoundary;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.PositionId;
import com.butchercraft.world.workforce.WorkforceDefinition;
import com.butchercraft.world.workforce.WorkforceRegistry;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class EmployeeManager {
    private EmployeeRegistry registry;
    private long nextSequence;
    private final int capacity;

    public EmployeeManager(EmployeeDirectory directory, int capacity) {
        this(
                Objects.requireNonNull(directory, "directory").registry(),
                directory.nextSequence(),
                capacity
        );
    }

    public EmployeeManager(EmployeeRegistry registry, long nextSequence, int capacity) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (nextSequence < 0L) {
            throw new IllegalArgumentException("Employee next sequence must not be negative: " + nextSequence);
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Employee capacity must be positive: " + capacity);
        }
        this.nextSequence = nextSequence;
        this.capacity = capacity;
        if (registry.size() > capacity) {
            throw new IllegalArgumentException("Employee registry exceeds capacity: " + registry.size());
        }
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> createEmployee(
            WorldIdentityRootIdentity worldIdentity,
            Business business,
            Optional<String> requestedDisplayName,
            Optional<EmployeeShiftAssignment> initialShift,
            Optional<PositionId> initialPosition,
            BusinessCalendarSnapshot hireSnapshot,
            String creationSourceIdentity,
            String creationConfigurationIdentity
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(business, "business");
        requestedDisplayName = Objects.requireNonNull(requestedDisplayName, "requestedDisplayName")
                .map(name -> EmployeeValidation.requireText(name, "displayName"));
        initialShift = Objects.requireNonNull(initialShift, "initialShift");
        initialPosition = Objects.requireNonNull(initialPosition, "initialPosition");
        if (registry.size() >= capacity) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.CAPACITY_EXCEEDED,
                    "Employee capacity has been reached"
            );
        }
        long sequence = nextSequence;
        EmployeeId employeeId = EmployeeId.from(worldIdentity, business.id(), sequence, creationSourceIdentity);
        if (registry.contains(employeeId)) {
            return EmployeeOperationResult.failed(
                    EmployeeFailureCode.DUPLICATE_EMPLOYEE_ID,
                    "Generated employee identity already exists: " + employeeId.value()
            );
        }
        String displayName = requestedDisplayName.orElseGet(() ->
                EmployeeNameGenerator.generatedDisplayName(worldIdentity, business.id(), sequence));
        EmployeeRecord record = EmployeeRecord.hired(
                employeeId,
                business.id(),
                sequence,
                worldIdentity.identity(),
                worldIdentity.rootDigest(),
                displayName,
                initialShift,
                initialPosition,
                hireSnapshot,
                creationSourceIdentity,
                creationConfigurationIdentity
        );
        registry = registry.with(record);
        nextSequence = Math.addExact(nextSequence, 1L);
        return EmployeeOperationResult.succeeded(record);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> transitionStatus(
            EmployeeId employeeId,
            EmployeeStatus nextStatus
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (record.status() == EmployeeStatus.TERMINATED && nextStatus != EmployeeStatus.TERMINATED) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.TERMINATED_EMPLOYEE,
                    "Terminated employee cannot be reactivated: " + employeeId.value());
        }
        if (record.status() == nextStatus) {
            return EmployeeOperationResult.succeeded(record);
        }
        EmployeeRecord updated = record.withStatus(nextStatus);
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> assignShift(
            EmployeeId employeeId,
            Optional<EmployeeShiftAssignment> shift
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (record.status() == EmployeeStatus.TERMINATED) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.TERMINATED_EMPLOYEE,
                    "Cannot assign shift to terminated employee: " + employeeId.value());
        }
        EmployeeRecord updated = record.withAssignedShift(Objects.requireNonNull(shift, "shift"));
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> assignPosition(
            EmployeeId employeeId,
            Optional<PositionId> positionId,
            WorkforceRegistry workforceRegistry
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (positionId.isPresent() && !positionExists(record.businessId(), positionId.orElseThrow(), workforceRegistry)) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.INVALID_POSITION,
                    "Position is not defined for employee business: " + positionId.orElseThrow().value());
        }
        EmployeeRecord updated = record.withAssignedPosition(positionId);
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> setPresence(
            EmployeeId employeeId,
            EmployeePresenceState presenceState
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (!Objects.requireNonNull(presenceState, "presenceState").isExplicitCommandState()) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.INVALID_PRESENCE_STATE,
                    "Scheduled presence is derived from Business Runtime and cannot be written directly");
        }
        if (!record.status().permitsPresence() && presenceState != EmployeePresenceState.UNAVAILABLE) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.INVALID_PRESENCE_STATE,
                    "Inactive employee presence must be unavailable: " + employeeId.value());
        }
        EmployeeRecord updated = record.withPresenceState(presenceState);
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> bindEntity(
            EmployeeId employeeId,
            EmployeeEntityLink link,
            EmployeeAnchor anchor
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (record.status() == EmployeeStatus.TERMINATED) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.TERMINATED_EMPLOYEE,
                    "Cannot bind entity to terminated employee: " + employeeId.value());
        }
        Optional<EmployeeRecord> other = registry.records().stream()
                .filter(candidate -> !candidate.employeeId().equals(employeeId))
                .filter(candidate -> candidate.entityLink()
                        .map(existing -> existing.entityUuid().equals(link.entityUuid()))
                        .orElse(false))
                .findFirst();
        if (other.isPresent()) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.ENTITY_LINK_CONFLICT,
                    "Entity is already linked to employee: " + other.orElseThrow().employeeId().value());
        }
        if (record.entityLink().isPresent()
                && !record.entityLink().orElseThrow().entityUuid().equals(link.entityUuid())) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.ENTITY_ALREADY_BOUND,
                    "Employee is already bound to another entity: " + employeeId.value());
        }
        EmployeeRecord updated = record.withEntityLink(link, anchor);
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized EmployeeOperationResult<EmployeeRecord> unbindEntity(EmployeeId employeeId, UUID entityUuid) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        if (record.entityLink().isEmpty()) {
            return EmployeeOperationResult.succeeded(record);
        }
        if (!record.entityLink().orElseThrow().entityUuid().equals(Objects.requireNonNull(entityUuid, "entityUuid"))) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.ENTITY_LINK_CONFLICT,
                    "Employee is bound to a different entity: " + employeeId.value());
        }
        EmployeeRecord updated = record.withoutEntityLink();
        registry = registry.with(updated);
        return EmployeeOperationResult.succeeded(updated);
    }

    public synchronized Optional<EmployeeRecord> find(EmployeeId employeeId) {
        return registry.find(employeeId);
    }

    public synchronized EmployeeRegistry registry() {
        return registry;
    }

    public synchronized EmployeeDirectory directory() {
        return new EmployeeDirectory(nextSequence, registry);
    }

    public synchronized EmployeeOperationResult<EmployeePresenceObservation> observe(
            EmployeeId employeeId,
            BusinessRuntimeObservationSnapshot snapshot,
            BusinessRuntimeCalendarConfiguration configuration
    ) {
        EmployeeRecord record = registry.find(employeeId).orElse(null);
        if (record == null) {
            return EmployeeOperationResult.failed(EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                    "Unknown employee: " + employeeId.value());
        }
        return EmployeeOperationResult.succeeded(observation(record, snapshot, configuration));
    }

    public synchronized void validateBusinessReferences(Collection<Business> businesses) {
        Set<BusinessId> known = Objects.requireNonNull(businesses, "businesses").stream()
                .map(business -> Objects.requireNonNull(business, "business").id())
                .collect(Collectors.toUnmodifiableSet());
        for (EmployeeRecord record : registry.records()) {
            if (!known.contains(record.businessId())) {
                throw new IllegalArgumentException("Employee references unknown business: "
                        + record.employeeId().value() + "/" + record.businessId().value());
            }
        }
    }

    private static EmployeePresenceObservation observation(
            EmployeeRecord record,
            BusinessRuntimeObservationSnapshot snapshot,
            BusinessRuntimeCalendarConfiguration configuration
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(configuration, "configuration");
        Optional<String> activeShiftIdentity = snapshot.activeShift()
                .flatMap(BusinessScheduleBoundary::identity);
        EmployeePresenceState observed;
        String reason;
        if (!record.status().permitsPresence()) {
            observed = EmployeePresenceState.UNAVAILABLE;
            reason = "employment status is " + record.status().serializedName();
        } else if (record.presenceState() == EmployeePresenceState.UNAVAILABLE) {
            observed = EmployeePresenceState.UNAVAILABLE;
            reason = "employee is explicitly unavailable";
        } else if (record.assignedShift().isEmpty()) {
            observed = EmployeePresenceState.UNAVAILABLE;
            reason = "employee has no assigned shift";
        } else if (!assignmentIsCurrent(record.assignedShift().orElseThrow(), configuration)) {
            observed = EmployeePresenceState.UNAVAILABLE;
            reason = "assigned shift is not present in current Business Runtime configuration";
        } else if (activeShiftIdentity
                .filter(identity -> identity.equals(record.assignedShift().orElseThrow().shiftIdentity()))
                .isEmpty()) {
            observed = EmployeePresenceState.OFF_SHIFT;
            reason = "assigned shift is not active";
        } else if (record.presenceState() == EmployeePresenceState.PRESENT) {
            observed = EmployeePresenceState.PRESENT;
            reason = "employee was explicitly marked present for the active shift";
        } else if (record.presenceState() == EmployeePresenceState.ABSENT) {
            observed = EmployeePresenceState.ABSENT;
            reason = "employee was explicitly marked absent for the active shift";
        } else {
            observed = EmployeePresenceState.SCHEDULED;
            reason = "employee is scheduled for the active shift";
        }
        return new EmployeePresenceObservation(
                record.employeeId(),
                record.businessId(),
                record.displayName(),
                record.status(),
                observed,
                record.assignedShift(),
                activeShiftIdentity,
                snapshot.plantOpen(),
                reason,
                record.recordRevision()
        );
    }

    private static boolean assignmentIsCurrent(
            EmployeeShiftAssignment assignment,
            BusinessRuntimeCalendarConfiguration configuration
    ) {
        return configuration.shiftSet().shifts().stream()
                .filter(shift -> shift.id().equals(assignment.shiftId()))
                .anyMatch(assignment::matches);
    }

    private static boolean positionExists(
            BusinessId businessId,
            PositionId positionId,
            WorkforceRegistry workforceRegistry
    ) {
        Objects.requireNonNull(workforceRegistry, "workforceRegistry");
        return workforceRegistry.findByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(definition -> definition.workforceDefinitionId().value()))
                .flatMap((WorkforceDefinition definition) -> definition.positions().stream())
                .anyMatch(position -> position.positionId().equals(positionId));
    }
}
