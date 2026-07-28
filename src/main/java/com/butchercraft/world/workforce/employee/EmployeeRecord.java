package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.workforce.PositionId;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.Objects;
import java.util.Optional;

public record EmployeeRecord(
        int schemaVersion,
        EmployeeId employeeId,
        BusinessId businessId,
        long sequence,
        String worldIdentityRoot,
        String worldIdentityRootDigest,
        String displayName,
        Optional<String> preferredName,
        EmployeeStatus status,
        EmployeePresenceState presenceState,
        Optional<EmployeeShiftAssignment> assignedShift,
        Optional<PositionId> assignedPositionId,
        long hireBusinessDay,
        BusinessTimeOfDay hireBusinessTime,
        String hireWorldDayIdentity,
        Optional<EmployeeEntityLink> entityLink,
        Optional<EmployeeAnchor> anchor,
        long recordRevision,
        String creationSourceIdentity,
        String creationConfigurationIdentity
) {
    public EmployeeRecord {
        if (schemaVersion != EmployeeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported employee record schema version: " + schemaVersion);
        }
        employeeId = Objects.requireNonNull(employeeId, "employeeId");
        businessId = Objects.requireNonNull(businessId, "businessId");
        if (sequence < 0L) {
            throw new IllegalArgumentException("Employee sequence must not be negative: " + sequence);
        }
        worldIdentityRoot = EmployeeValidation.requireIdentity(worldIdentityRoot, "worldIdentityRoot");
        worldIdentityRootDigest = EmployeeValidation.requireDigest(worldIdentityRootDigest, "worldIdentityRootDigest");
        displayName = EmployeeValidation.requireText(displayName, "displayName");
        preferredName = Objects.requireNonNull(preferredName, "preferredName")
                .map(name -> EmployeeValidation.requireText(name, "preferredName"));
        status = Objects.requireNonNull(status, "status");
        presenceState = Objects.requireNonNull(presenceState, "presenceState");
        assignedShift = Objects.requireNonNull(assignedShift, "assignedShift");
        assignedPositionId = Objects.requireNonNull(assignedPositionId, "assignedPositionId");
        if (hireBusinessDay < 0L) {
            throw new IllegalArgumentException("Employee hire business day must not be negative: " + hireBusinessDay);
        }
        hireBusinessTime = Objects.requireNonNull(hireBusinessTime, "hireBusinessTime");
        hireWorldDayIdentity = EmployeeValidation.requireIdentity(hireWorldDayIdentity, "hireWorldDayIdentity");
        entityLink = Objects.requireNonNull(entityLink, "entityLink");
        anchor = Objects.requireNonNull(anchor, "anchor");
        if (entityLink.isPresent() != anchor.isPresent()) {
            throw new IllegalArgumentException("Employee entity link and anchor must be present together");
        }
        if (recordRevision < 0L) {
            throw new IllegalArgumentException("Employee record revision must not be negative: " + recordRevision);
        }
        creationSourceIdentity = EmployeeValidation.requireIdentity(creationSourceIdentity, "creationSourceIdentity");
        creationConfigurationIdentity = EmployeeValidation.requireIdentity(
                creationConfigurationIdentity,
                "creationConfigurationIdentity"
        );
        if (!status.permitsPresence() && presenceState != EmployeePresenceState.UNAVAILABLE) {
            throw new IllegalArgumentException("Inactive employee records must be unavailable: " + employeeId.value());
        }
    }

    public static EmployeeRecord hired(
            EmployeeId employeeId,
            BusinessId businessId,
            long sequence,
            String worldIdentityRoot,
            String worldIdentityRootDigest,
            String displayName,
            Optional<EmployeeShiftAssignment> shift,
            Optional<PositionId> positionId,
            BusinessCalendarSnapshot hireSnapshot,
            String creationSourceIdentity,
            String creationConfigurationIdentity
    ) {
        Objects.requireNonNull(hireSnapshot, "hireSnapshot");
        return new EmployeeRecord(
                EmployeeSchema.CURRENT_VERSION,
                employeeId,
                businessId,
                sequence,
                worldIdentityRoot,
                worldIdentityRootDigest,
                displayName,
                Optional.empty(),
                EmployeeStatus.ACTIVE,
                EmployeePresenceState.OFF_SHIFT,
                shift,
                positionId,
                hireSnapshot.businessDayIndex(),
                hireSnapshot.timeOfDay(),
                hireSnapshot.worldDayIdentity(),
                Optional.empty(),
                Optional.empty(),
                1L,
                creationSourceIdentity,
                creationConfigurationIdentity
        );
    }

    public EmployeeRecord withStatus(EmployeeStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        EmployeePresenceState nextPresence = nextStatus.permitsPresence()
                ? EmployeePresenceState.OFF_SHIFT
                : EmployeePresenceState.UNAVAILABLE;
        return copy(nextStatus, nextPresence, assignedShift, assignedPositionId, entityLink, anchor);
    }

    public EmployeeRecord withAssignedShift(Optional<EmployeeShiftAssignment> nextShift) {
        return copy(status, presenceState, nextShift, assignedPositionId, entityLink, anchor);
    }

    public EmployeeRecord withAssignedPosition(Optional<PositionId> nextPositionId) {
        return copy(status, presenceState, assignedShift, nextPositionId, entityLink, anchor);
    }

    public EmployeeRecord withPresenceState(EmployeePresenceState nextPresenceState) {
        return copy(status, nextPresenceState, assignedShift, assignedPositionId, entityLink, anchor);
    }

    public EmployeeRecord withEntityLink(EmployeeEntityLink nextLink, EmployeeAnchor nextAnchor) {
        return copy(status, presenceState, assignedShift, assignedPositionId,
                Optional.of(nextLink), Optional.of(nextAnchor));
    }

    public EmployeeRecord withoutEntityLink() {
        return copy(status, presenceState, assignedShift, assignedPositionId, Optional.empty(), Optional.empty());
    }

    private EmployeeRecord copy(
            EmployeeStatus nextStatus,
            EmployeePresenceState nextPresence,
            Optional<EmployeeShiftAssignment> nextShift,
            Optional<PositionId> nextPosition,
            Optional<EmployeeEntityLink> nextEntityLink,
            Optional<EmployeeAnchor> nextAnchor
    ) {
        return new EmployeeRecord(
                schemaVersion,
                employeeId,
                businessId,
                sequence,
                worldIdentityRoot,
                worldIdentityRootDigest,
                displayName,
                preferredName,
                nextStatus,
                nextPresence,
                Objects.requireNonNull(nextShift, "nextShift"),
                Objects.requireNonNull(nextPosition, "nextPosition"),
                hireBusinessDay,
                hireBusinessTime,
                hireWorldDayIdentity,
                Objects.requireNonNull(nextEntityLink, "nextEntityLink"),
                Objects.requireNonNull(nextAnchor, "nextAnchor"),
                recordRevision + 1L,
                creationSourceIdentity,
                creationConfigurationIdentity
        );
    }
}
