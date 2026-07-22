package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.SimulationConfiguration;

import java.util.List;

final class BusinessRuntimeTestFixtures {
    static final SimulationConfiguration CONFIGURATION = SimulationConfiguration.standard();

    private BusinessRuntimeTestFixtures() {
    }

    static BusinessRuntimeState state(String businessId, BusinessHours hours, List<BusinessShift> shifts, int capacity) {
        return BusinessRuntimeState.closed(new BusinessId(businessId), hours, shifts, capacity, 0L);
    }

    static BusinessRuntimeState suspended(String businessId, BusinessHours hours) {
        return BusinessRuntimeState.suspended(new BusinessId(businessId), hours, List.of(), 0, 0L);
    }

    static BusinessHours allDays(int openingHour, int closingHour) {
        return BusinessHours.allDays(openingHour, 0, closingHour, 0, CONFIGURATION);
    }

    static BusinessHours weekdays(int openingHour, int closingHour) {
        return BusinessHours.weekdays(openingHour, 0, closingHour, 0, CONFIGURATION);
    }

    static BusinessShift shift(String id, int startHour, int endHour, int expectedWorkforce) {
        return new BusinessShift(id, startHour, 0, endHour, 0, expectedWorkforce, true);
    }

    static long tickAt(int dayOffset, int hour, int minute) {
        long elapsedMinutes = Math.addExact(
                Math.multiplyExact((long) dayOffset, CONFIGURATION.hoursPerDay() * CONFIGURATION.minutesPerHour()),
                Math.addExact(Math.multiplyExact(hour, CONFIGURATION.minutesPerHour()), minute)
        );
        return Math.multiplyExact(elapsedMinutes, CONFIGURATION.ticksPerSimulationMinute());
    }
}
