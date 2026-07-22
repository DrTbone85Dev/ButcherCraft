package com.butchercraft.world.workforce;

import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.workforce.WorkforceTestFixtures.CONFIGURATION;
import static com.butchercraft.world.workforce.WorkforceTestFixtures.definition;
import static com.butchercraft.world.workforce.WorkforceTestFixtures.runtimeState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkforceDefinitionTest {
    @Test
    void defaultWorkforceDefinitionUsesBusinessRuntimeShiftStructure() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(7_777L);
        BusinessRuntimeRegistry runtimeRegistry = BusinessRuntimeRegistry.fromBusinesses(identity.businesses(), CONFIGURATION);

        WorkforceRegistry workforceRegistry = WorkforceRegistry.empty()
                .withMissingDefaults(identity.businesses(), runtimeRegistry);

        assertEquals(identity.businesses().size(), workforceRegistry.size());
        WorkforceDefinition first = workforceRegistry.definitions().stream()
                .filter(definition -> !definition.positions().isEmpty())
                .findFirst()
                .orElseThrow();
        BusinessRuntimeState runtimeState = runtimeRegistry.find(first.businessId()).orElseThrow();

        first.validateAgainst(runtimeState);
        assertEquals(first.businessId().value() + "_workforce", first.workforceDefinitionId().value());
        assertTrue(first.positions().stream().allMatch(position -> position.assignedShiftId().equals("day")));
        assertFalse(first.requiredPositionsForShift("day").isEmpty());
    }

    @Test
    void workforceManagerReturnsRequiredPositionsForCurrentShift() {
        BusinessRuntimeState runtimeState = runtimeState("alpha_market").operatingAt("day", 3, 100L);
        WorkforceManager manager = new WorkforceManager(WorkforceRegistry.of(List.of(definition("alpha_market", "primary"))));

        List<WorkforcePosition> positions = manager.requiredPositionsForCurrentShift(runtimeState);

        assertEquals(2, positions.size());
        assertEquals(List.of("day_manager_primary", "day_meat_cutter_primary"),
                positions.stream().map(position -> position.positionId().value()).toList());
    }

    @Test
    void workforceManagerReturnsNoRequiredPositionsWhenNoCurrentShiftExists() {
        BusinessRuntimeState runtimeState = runtimeState("alpha_market");
        WorkforceManager manager = new WorkforceManager(WorkforceRegistry.of(List.of(definition("alpha_market", "primary"))));

        assertTrue(manager.requiredPositionsForCurrentShift(runtimeState).isEmpty());
    }

    @Test
    void definitionsAreImmutable() {
        WorkforceDefinition definition = definition("alpha_market", "primary");

        assertThrows(UnsupportedOperationException.class, () -> definition.positions().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.shiftAssignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.staffingRule().requiredPositions().clear());
    }
}
