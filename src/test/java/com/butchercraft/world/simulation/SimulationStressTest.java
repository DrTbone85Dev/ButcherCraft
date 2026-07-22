package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationStressTest {
    @Test
    void oneMillionSimulationTicksHaveNoDriftOrDuplicateExecution() {
        SimulationConfiguration configuration = SimulationConfiguration.standard();
        List<ScheduledSimulationEvent> firstRun = new ArrayList<>();
        SimulationEventBus firstBus = new SimulationEventBus();
        firstBus.subscribe(firstRun::add);
        SimulationClock first = new SimulationClock(configuration, SimulationState.initial(configuration), firstBus);

        List<ScheduledSimulationEvent> secondRun = new ArrayList<>();
        SimulationEventBus secondBus = new SimulationEventBus();
        secondBus.subscribe(secondRun::add);
        SimulationClock second = new SimulationClock(configuration, SimulationState.initial(configuration), secondBus);

        first.advance(1_000_000L);
        second.advance(1_000_000L);

        assertEquals(1_000_000L, first.simulationTick());
        assertEquals(SimulationTime.fromTick(1_000_000L, configuration), first.time());
        assertEquals(SimulationCalendar.fromTick(1_000_000L, configuration), first.calendar());
        assertEquals(first.state(), second.state());
        assertEquals(firstRun, secondRun);

        Set<String> executedIds = new HashSet<>();
        for (ScheduledSimulationEvent event : firstRun) {
            executedIds.add(event.id());
        }
        assertEquals(firstRun.size(), executedIds.size());
        assertEquals(firstRun.stream()
                        .sorted(Comparator.comparingLong(ScheduledSimulationEvent::scheduledSimulationTick)
                                .thenComparingInt(event -> event.eventType().executionPriority())
                                .thenComparing(ScheduledSimulationEvent::id))
                        .toList(),
                firstRun);
    }
}
