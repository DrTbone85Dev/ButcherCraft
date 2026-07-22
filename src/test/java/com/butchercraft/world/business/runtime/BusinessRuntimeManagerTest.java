package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.BusinessId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.CONFIGURATION;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.allDays;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.shift;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.state;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.suspended;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.tickAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRuntimeManagerTest {
    @Test
    void managerOpensTransitionsShiftsAndClosesOnSchedule() {
        BusinessRuntimeState runtime = state("market_one", allDays(8, 17), List.of(
                shift("morning", 8, 12, 2),
                shift("afternoon", 12, 17, 3)
        ), 4);
        BusinessRuntimeManager manager = new BusinessRuntimeManager(BusinessRuntimeRegistry.of(List.of(runtime)), CONFIGURATION);

        assertTrue(manager.evaluateAt(tickAt(0, 7, 59)).isEmpty());

        manager.evaluateAt(tickAt(0, 8, 0));
        BusinessRuntimeState opened = manager.registry().find("market_one").orElseThrow();
        assertTrue(opened.open());
        assertEquals(BusinessOperationalStatus.OPERATING, opened.operationalStatus());
        assertEquals("morning", opened.activeShiftId().orElseThrow());
        assertEquals(2, opened.activeWorkforce());

        manager.evaluateAt(tickAt(0, 12, 0));
        BusinessRuntimeState shifted = manager.registry().find("market_one").orElseThrow();
        assertEquals("afternoon", shifted.activeShiftId().orElseThrow());
        assertEquals(3, shifted.activeWorkforce());

        manager.evaluateAt(tickAt(0, 17, 0));
        BusinessRuntimeState closed = manager.registry().find("market_one").orElseThrow();
        assertFalse(closed.open());
        assertEquals(BusinessOperationalStatus.CLOSED, closed.operationalStatus());
        assertTrue(closed.activeShiftId().isEmpty());
        assertEquals(0, closed.activeWorkforce());
    }

    @Test
    void closedOrSuspendedBusinessRemainsClosedDuringOpenHours() {
        BusinessRuntimeState runtime = suspended("archived_shop", allDays(8, 17));
        BusinessRuntimeManager manager = new BusinessRuntimeManager(BusinessRuntimeRegistry.of(List.of(runtime)), CONFIGURATION);

        manager.evaluateAt(tickAt(0, 9, 0));

        BusinessRuntimeState state = manager.registry().find("archived_shop").orElseThrow();
        assertFalse(state.open());
        assertEquals(BusinessOperationalStatus.SUSPENDED, state.operationalStatus());
    }

    @Test
    void maintenancePreventsScheduledOpeningUntilEnded() {
        BusinessRuntimeState runtime = state("maintenance_shop", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4);
        BusinessRuntimeManager manager = new BusinessRuntimeManager(BusinessRuntimeRegistry.of(List.of(runtime)), CONFIGURATION);
        BusinessId businessId = new BusinessId("maintenance_shop");

        manager.beginMaintenance(businessId, tickAt(0, 6, 0));
        manager.evaluateAt(tickAt(0, 9, 0));
        assertEquals(BusinessOperationalStatus.MAINTENANCE, manager.registry().find(businessId).orElseThrow().operationalStatus());

        manager.endMaintenance(businessId, tickAt(0, 10, 0));
        manager.evaluateAt(tickAt(0, 10, 1));
        assertEquals(BusinessOperationalStatus.OPERATING, manager.registry().find(businessId).orElseThrow().operationalStatus());
    }

    @Test
    void shiftGapUsesExplicitShiftChangeState() {
        BusinessRuntimeState runtime = state("gap_shop", allDays(8, 17), List.of(
                shift("morning", 8, 10, 2),
                shift("afternoon", 12, 17, 2)
        ), 4);
        BusinessRuntimeManager manager = new BusinessRuntimeManager(BusinessRuntimeRegistry.of(List.of(runtime)), CONFIGURATION);

        manager.evaluateAt(tickAt(0, 11, 0));

        BusinessRuntimeState state = manager.registry().find("gap_shop").orElseThrow();
        assertTrue(state.open());
        assertEquals(BusinessOperationalStatus.SHIFT_CHANGE, state.operationalStatus());
        assertTrue(state.activeShiftId().isEmpty());
        assertEquals(0, state.activeWorkforce());
    }
}
