package com.butchercraft.world.workforce;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessHours;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.business.runtime.BusinessShift;
import com.butchercraft.world.simulation.SimulationConfiguration;

import java.util.List;

final class WorkforceTestFixtures {
    static final SimulationConfiguration CONFIGURATION = SimulationConfiguration.standard();

    private WorkforceTestFixtures() {
    }

    static BusinessRuntimeState runtimeState(String businessId) {
        return BusinessRuntimeState.closed(
                new BusinessId(businessId),
                BusinessHours.allDays(8, 0, 17, 0, CONFIGURATION),
                List.of(new BusinessShift("day", 8, 0, 17, 0, 3, true)),
                4,
                0L
        );
    }

    static WorkforceDefinition definition(String businessId, String definitionSuffix) {
        PositionId managerId = new PositionId("day_manager_" + definitionSuffix);
        PositionId cutterId = new PositionId("day_meat_cutter_" + definitionSuffix);
        WorkforcePosition manager = new WorkforcePosition(
                managerId,
                WorkforcePositionType.MANAGER,
                "Day Manager",
                WorkforceSkillLevel.EXPERIENCED,
                List.of(CertificationType.FOOD_SAFETY),
                "day",
                true,
                1
        );
        WorkforcePosition cutter = new WorkforcePosition(
                cutterId,
                WorkforcePositionType.MEAT_CUTTER,
                "Day Meat Cutter",
                WorkforceSkillLevel.QUALIFIED,
                List.of(CertificationType.FOOD_SAFETY),
                "day",
                true,
                2
        );
        return new WorkforceDefinition(
                new BusinessId(businessId),
                new WorkforceDefinitionId(businessId + "_workforce_" + definitionSuffix),
                List.of(manager, cutter),
                List.of(
                        new WorkforceShiftAssignment("day", managerId, 1, 1),
                        new WorkforceShiftAssignment("day", cutterId, 2, 2)
                ),
                new WorkforceStaffingRule(List.of(managerId, cutterId), List.of(), 3, 3),
                WorkforceSchema.CURRENT_VERSION
        );
    }
}
