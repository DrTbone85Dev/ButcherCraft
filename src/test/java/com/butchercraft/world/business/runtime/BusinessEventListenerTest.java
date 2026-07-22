package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.ScheduledSimulationEvent;
import com.butchercraft.world.simulation.SimulationEventBus;
import com.butchercraft.world.simulation.SimulationEventStatus;
import com.butchercraft.world.simulation.SimulationEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.CONFIGURATION;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.allDays;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.shift;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.state;
import static com.butchercraft.world.business.runtime.BusinessRuntimeTestFixtures.tickAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessEventListenerTest {
    @Test
    void listenerOpensAndClosesBusinessesFromSimulationEvents() {
        BusinessRuntimeState runtime = state("event_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4);
        BusinessRuntimeManager manager = new BusinessRuntimeManager(BusinessRuntimeRegistry.of(List.of(runtime)), CONFIGURATION);
        SimulationEventBus eventBus = new SimulationEventBus();
        eventBus.subscribe(SimulationEventType.DAILY_ROLLOVER, new BusinessEventListener(manager, CONFIGURATION));

        eventBus.publish(event("open_event", tickAt(0, 8, 0), SimulationEventType.DAILY_ROLLOVER));
        assertTrue(manager.registry().find("event_market").orElseThrow().open());

        eventBus.publish(event("close_event", tickAt(0, 17, 0), SimulationEventType.DAILY_ROLLOVER));
        assertFalse(manager.registry().find("event_market").orElseThrow().open());
    }

    @Test
    void listenerHandlesWeeklyValidationWithoutChangingDeterministicOrder() {
        BusinessRuntimeRegistry registry = BusinessRuntimeRegistry.of(List.of(
                state("zeta_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4),
                state("alpha_market", allDays(8, 17), List.of(shift("day", 8, 17, 2)), 4)
        ));
        BusinessRuntimeManager manager = new BusinessRuntimeManager(registry, CONFIGURATION);
        SimulationEventBus eventBus = new SimulationEventBus();
        eventBus.subscribe(SimulationEventType.WEEKLY_ROLLOVER, new BusinessEventListener(manager, CONFIGURATION));

        eventBus.publish(event("weekly_event", tickAt(0, 8, 0), SimulationEventType.WEEKLY_ROLLOVER));

        assertEquals(List.of("alpha_market", "zeta_market"),
                manager.registry().states().stream().map(state -> state.businessId().value()).toList());
        assertTrue(manager.registry().states().stream().allMatch(BusinessRuntimeState::open));
    }

    private static ScheduledSimulationEvent event(String id, long tick, SimulationEventType eventType) {
        return new ScheduledSimulationEvent(
                id,
                tick,
                eventType,
                "business_runtime_test",
                SimulationEventStatus.EXECUTED
        );
    }
}
