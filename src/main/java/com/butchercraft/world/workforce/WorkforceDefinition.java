package com.butchercraft.world.workforce;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.business.runtime.BusinessShift;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record WorkforceDefinition(
        BusinessId businessId,
        WorkforceDefinitionId workforceDefinitionId,
        List<WorkforcePosition> positions,
        List<WorkforceShiftAssignment> shiftAssignments,
        WorkforceStaffingRule staffingRule,
        int schemaVersion
) {
    public WorkforceDefinition {
        businessId = Objects.requireNonNull(businessId, "businessId");
        workforceDefinitionId = Objects.requireNonNull(workforceDefinitionId, "workforceDefinitionId");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        shiftAssignments = List.copyOf(Objects.requireNonNull(shiftAssignments, "shiftAssignments"));
        staffingRule = Objects.requireNonNull(staffingRule, "staffingRule");
        if (schemaVersion != WorkforceSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported workforce definition schema version: " + schemaVersion);
        }
        validateDefinitionStructure(positions, shiftAssignments, staffingRule);
    }

    public List<WorkforcePosition> requiredPositionsForShift(String shiftId) {
        String normalizedShiftId = WorkforcePosition.requireShiftId(shiftId);
        Set<PositionId> staffedRequiredPositions = shiftAssignments.stream()
                .filter(assignment -> assignment.shiftId().equals(normalizedShiftId))
                .filter(assignment -> assignment.minimumWorkers() > 0)
                .map(WorkforceShiftAssignment::positionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return positions.stream()
                .filter(position -> position.required() && staffedRequiredPositions.contains(position.positionId()))
                .toList();
    }

    public List<WorkforceShiftAssignment> assignmentsForShift(String shiftId) {
        String normalizedShiftId = WorkforcePosition.requireShiftId(shiftId);
        return shiftAssignments.stream()
                .filter(assignment -> assignment.shiftId().equals(normalizedShiftId))
                .toList();
    }

    public void validateAgainst(BusinessRuntimeState runtimeState) {
        Objects.requireNonNull(runtimeState, "runtimeState");
        if (!runtimeState.businessId().equals(businessId)) {
            throw new IllegalArgumentException("Workforce definition business does not match runtime state: "
                    + workforceDefinitionId.value());
        }
        Set<String> knownShiftIds = runtimeState.shifts().stream()
                .map(BusinessShift::id)
                .collect(Collectors.toUnmodifiableSet());
        for (WorkforcePosition position : positions) {
            if (!knownShiftIds.contains(position.assignedShiftId())) {
                throw new IllegalArgumentException("Workforce position references unknown business shift: "
                        + workforceDefinitionId.value() + "/" + position.positionId().value() + "/" + position.assignedShiftId());
            }
        }
        for (WorkforceShiftAssignment assignment : shiftAssignments) {
            if (!knownShiftIds.contains(assignment.shiftId())) {
                throw new IllegalArgumentException("Workforce assignment references unknown business shift: "
                        + workforceDefinitionId.value() + "/" + assignment.shiftId());
            }
        }
    }

    private static void validateDefinitionStructure(
            List<WorkforcePosition> positions,
            List<WorkforceShiftAssignment> shiftAssignments,
            WorkforceStaffingRule staffingRule
    ) {
        rejectDuplicatePositionIds(positions);
        Map<PositionId, WorkforcePosition> positionsById = positions.stream()
                .collect(Collectors.toUnmodifiableMap(WorkforcePosition::positionId, Function.identity()));
        for (PositionId positionId : staffingRule.requiredPositions()) {
            WorkforcePosition position = requireKnownPosition(positionId, positionsById);
            if (!position.required()) {
                throw new IllegalArgumentException("Workforce required staffing rule references optional position: "
                        + positionId.value());
            }
        }
        for (PositionId positionId : staffingRule.optionalPositions()) {
            WorkforcePosition position = requireKnownPosition(positionId, positionsById);
            if (position.required()) {
                throw new IllegalArgumentException("Workforce optional staffing rule references required position: "
                        + positionId.value());
            }
        }

        Set<PositionId> assignedPositionIds = new HashSet<>();
        int minimumStaffing = 0;
        int maximumStaffing = 0;
        for (WorkforceShiftAssignment assignment : shiftAssignments) {
            WorkforcePosition position = requireKnownPosition(assignment.positionId(), positionsById);
            if (!position.assignedShiftId().equals(assignment.shiftId())) {
                throw new IllegalArgumentException("Workforce assignment shift does not match position shift: "
                        + assignment.positionId().value());
            }
            if (assignment.maximumWorkers() > position.maximumWorkers()) {
                throw new IllegalArgumentException("Workforce assignment exceeds position maximum workers: "
                        + assignment.positionId().value());
            }
            if (position.required() && assignment.minimumWorkers() == 0) {
                throw new IllegalArgumentException("Required workforce positions must have nonzero minimum staffing: "
                        + assignment.positionId().value());
            }
            if (!assignedPositionIds.add(assignment.positionId())) {
                throw new IllegalArgumentException("Workforce position must not have duplicate shift assignments: "
                        + assignment.positionId().value());
            }
            minimumStaffing = Math.addExact(minimumStaffing, assignment.minimumWorkers());
            maximumStaffing = Math.addExact(maximumStaffing, assignment.maximumWorkers());
        }

        for (WorkforcePosition position : positions) {
            if (!assignedPositionIds.contains(position.positionId())) {
                throw new IllegalArgumentException("Workforce position must have a shift assignment: "
                        + position.positionId().value());
            }
            if (position.required() && !staffingRule.requiredPositions().contains(position.positionId())) {
                throw new IllegalArgumentException("Required workforce position missing from staffing rule: "
                        + position.positionId().value());
            }
            if (!position.required() && !staffingRule.optionalPositions().contains(position.positionId())) {
                throw new IllegalArgumentException("Optional workforce position missing from staffing rule: "
                        + position.positionId().value());
            }
        }

        if (minimumStaffing < staffingRule.minimumStaffing()) {
            throw new IllegalArgumentException("Workforce assignments do not satisfy minimum staffing");
        }
        if (maximumStaffing > staffingRule.maximumStaffing()) {
            throw new IllegalArgumentException("Workforce assignments exceed maximum staffing");
        }
        if (positions.isEmpty() && (staffingRule.minimumStaffing() != 0 || staffingRule.maximumStaffing() != 0)) {
            throw new IllegalArgumentException("Empty workforce definitions must have zero staffing");
        }
    }

    private static WorkforcePosition requireKnownPosition(
            PositionId positionId,
            Map<PositionId, WorkforcePosition> positionsById
    ) {
        WorkforcePosition position = positionsById.get(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Workforce references unknown position: " + positionId.value());
        }
        return position;
    }

    private static void rejectDuplicatePositionIds(List<WorkforcePosition> positions) {
        Set<PositionId> seen = new HashSet<>();
        Set<PositionId> duplicates = new HashSet<>();
        for (WorkforcePosition position : positions) {
            Objects.requireNonNull(position, "position");
            if (!seen.add(position.positionId())) {
                duplicates.add(position.positionId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate workforce position ids: " + duplicates);
        }
    }
}
