package com.butchercraft.world.workforce;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.BusinessType;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.business.runtime.BusinessShift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BuiltInWorkforceDefinitions {
    private BuiltInWorkforceDefinitions() {
    }

    public static List<WorkforceDefinition> fromBusinesses(
            List<Business> businesses,
            List<BusinessRuntimeState> runtimeStates
    ) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(runtimeStates, "runtimeStates");
        Map<BusinessId, BusinessRuntimeState> runtimeByBusinessId = runtimeStates.stream()
                .collect(Collectors.toUnmodifiableMap(BusinessRuntimeState::businessId, Function.identity()));
        return businesses.stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .map(business -> defaultDefinition(business, runtimeByBusinessId.get(business.id())))
                .toList();
    }

    public static WorkforceDefinition defaultDefinition(Business business, BusinessRuntimeState runtimeState) {
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(runtimeState, "runtimeState");
        if (!business.id().equals(runtimeState.businessId())) {
            throw new IllegalArgumentException("Business and runtime state ids must match for workforce defaults");
        }
        List<WorkforcePosition> positions = new ArrayList<>();
        List<WorkforceShiftAssignment> assignments = new ArrayList<>();
        for (BusinessShift shift : runtimeState.shifts().stream()
                .sorted(Comparator.comparing(BusinessShift::id))
                .toList()) {
            addShiftPositions(business, shift, positions, assignments);
        }
        List<PositionId> requiredPositions = positions.stream()
                .filter(WorkforcePosition::required)
                .map(WorkforcePosition::positionId)
                .toList();
        List<PositionId> optionalPositions = positions.stream()
                .filter(position -> !position.required())
                .map(WorkforcePosition::positionId)
                .toList();
        int minimumStaffing = assignments.stream().mapToInt(WorkforceShiftAssignment::minimumWorkers).sum();
        int maximumStaffing = assignments.stream().mapToInt(WorkforceShiftAssignment::maximumWorkers).sum();
        return new WorkforceDefinition(
                business.id(),
                new WorkforceDefinitionId(business.id().value() + "_workforce"),
                positions,
                assignments,
                new WorkforceStaffingRule(requiredPositions, optionalPositions, minimumStaffing, maximumStaffing),
                WorkforceSchema.CURRENT_VERSION
        );
    }

    private static void addShiftPositions(
            Business business,
            BusinessShift shift,
            List<WorkforcePosition> positions,
            List<WorkforceShiftAssignment> assignments
    ) {
        if (!shift.active() || shift.expectedWorkforce() <= 0) {
            return;
        }
        WorkforcePositionType leaderType = leaderType(business.businessType());
        WorkforcePositionType primaryType = primaryOperatorType(business.businessType());
        int expected = shift.expectedWorkforce();

        PositionId leaderId = new PositionId(shift.id() + "_" + leaderType.serializedName());
        positions.add(position(
                leaderId,
                leaderType,
                displayName(leaderType, shift),
                WorkforceSkillLevel.EXPERIENCED,
                certificationsFor(leaderType),
                shift.id(),
                true,
                1
        ));
        assignments.add(new WorkforceShiftAssignment(shift.id(), leaderId, 1, 1));

        if (expected > 1) {
            int operatorCount = expected - 1;
            PositionId operatorId = new PositionId(shift.id() + "_" + primaryType.serializedName());
            positions.add(position(
                    operatorId,
                    primaryType,
                    displayName(primaryType, shift),
                    skillFor(primaryType),
                    certificationsFor(primaryType),
                    shift.id(),
                    true,
                    operatorCount
            ));
            assignments.add(new WorkforceShiftAssignment(shift.id(), operatorId, operatorCount, operatorCount));
        }
    }

    private static WorkforcePosition position(
            PositionId positionId,
            WorkforcePositionType positionType,
            String displayName,
            WorkforceSkillLevel skillLevel,
            List<CertificationType> certifications,
            String shiftId,
            boolean required,
            int maximumWorkers
    ) {
        return new WorkforcePosition(
                positionId,
                positionType,
                displayName,
                skillLevel,
                certifications,
                shiftId,
                required,
                maximumWorkers
        );
    }

    private static WorkforcePositionType leaderType(BusinessType businessType) {
        return switch (businessType) {
            case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET, WHOLESALE_SUPPLIER -> WorkforcePositionType.MANAGER;
            case CUSTOM_PROCESSOR, REGIONAL_PROCESSING_COMPANY, LOCKER_PLANT, COLD_STORAGE_COMPANY,
                 FOOD_DISTRIBUTION_COMPANY -> WorkforcePositionType.SUPERVISOR;
        };
    }

    private static WorkforcePositionType primaryOperatorType(BusinessType businessType) {
        return switch (businessType) {
            case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET, CUSTOM_PROCESSOR, LOCKER_PLANT -> WorkforcePositionType.MEAT_CUTTER;
            case REGIONAL_PROCESSING_COMPANY -> WorkforcePositionType.BANDSAW_OPERATOR;
            case COLD_STORAGE_COMPANY, FOOD_DISTRIBUTION_COMPANY, WHOLESALE_SUPPLIER -> WorkforcePositionType.SHIPPING_RECEIVING;
        };
    }

    private static WorkforceSkillLevel skillFor(WorkforcePositionType positionType) {
        return switch (positionType) {
            case OWNER, MANAGER, SUPERVISOR, QUALITY_ASSURANCE, OFFICE_ADMINISTRATION -> WorkforceSkillLevel.EXPERIENCED;
            case MEAT_CUTTER, SLAUGHTER_TECHNICIAN, PACKAGING_OPERATOR, GRINDER_OPERATOR, BANDSAW_OPERATOR,
                 SANITATION_TECHNICIAN, SHIPPING_RECEIVING, MAINTENANCE -> WorkforceSkillLevel.QUALIFIED;
            case APPRENTICE_CUTTER, RETAIL_CLERK, CUSTOMER_SERVICE -> WorkforceSkillLevel.TRAINEE;
        };
    }

    private static List<CertificationType> certificationsFor(WorkforcePositionType positionType) {
        return switch (positionType) {
            case MANAGER, SUPERVISOR, MEAT_CUTTER, PACKAGING_OPERATOR, GRINDER_OPERATOR, BANDSAW_OPERATOR,
                 QUALITY_ASSURANCE -> List.of(CertificationType.FOOD_SAFETY);
            case SLAUGHTER_TECHNICIAN -> List.of(CertificationType.FOOD_SAFETY, CertificationType.EQUIPMENT_OPERATION);
            case SANITATION_TECHNICIAN -> List.of(CertificationType.SANITATION);
            case SHIPPING_RECEIVING -> List.of(CertificationType.FORKLIFT);
            case OWNER, APPRENTICE_CUTTER, RETAIL_CLERK, CUSTOMER_SERVICE, MAINTENANCE, OFFICE_ADMINISTRATION ->
                    List.of(CertificationType.NONE);
        };
    }

    private static String displayName(WorkforcePositionType positionType, BusinessShift shift) {
        return readable(shift.id()) + " " + readable(positionType.serializedName());
    }

    private static String readable(String value) {
        String[] parts = value.split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                words.add(part.substring(0, 1).toUpperCase() + part.substring(1));
            }
        }
        return String.join(" ", words);
    }
}
