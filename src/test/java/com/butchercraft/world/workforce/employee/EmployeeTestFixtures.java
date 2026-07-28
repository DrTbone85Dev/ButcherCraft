package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;

import java.util.Comparator;
import java.util.Optional;

final class EmployeeTestFixtures {
    static final WorldTimeConfiguration WORLD_TIME_CONFIGURATION = WorldTimeConfiguration.enabled(60);
    static final BusinessRuntimeCalendarConfiguration BUSINESS_RUNTIME =
            BusinessRuntimeCalendarConfiguration.defaults(WORLD_TIME_CONFIGURATION.identity());
    static final WorldIdentity WORLD_IDENTITY = new WorldIdentityGenerator().generate(12345L);
    static final Business BUSINESS = WORLD_IDENTITY.businesses().stream()
            .sorted(Comparator.comparing(business -> business.id().value()))
            .findFirst()
            .orElseThrow();

    private EmployeeTestFixtures() {
    }

    static EmployeeManager manager() {
        return new EmployeeManager(EmployeeDirectory.empty(), 8);
    }

    static EmployeeRecord employee(EmployeeManager manager) {
        return manager.createEmployee(
                WorldIdentityRootIdentities.from(WORLD_IDENTITY),
                BUSINESS,
                Optional.of("Ada Cutter"),
                Optional.of(dayShift()),
                Optional.empty(),
                calendar(0L, 7, 0),
                "butchercraft:employee_creation/test",
                BUSINESS_RUNTIME.identity().value()
        ).orThrow();
    }

    static EmployeeShiftAssignment dayShift() {
        BusinessShiftDefinition shift = BUSINESS_RUNTIME.shiftSet().shifts().stream()
                .filter(candidate -> candidate.id().equals("day_shift"))
                .findFirst()
                .orElseThrow();
        return EmployeeShiftAssignment.from(shift, BUSINESS_RUNTIME.shiftSet(), BUSINESS_RUNTIME.identity());
    }

    static EmployeeShiftAssignment eveningShift() {
        BusinessShiftDefinition shift = BUSINESS_RUNTIME.shiftSet().shifts().stream()
                .filter(candidate -> candidate.id().equals("evening_shift"))
                .findFirst()
                .orElseThrow();
        return EmployeeShiftAssignment.from(shift, BUSINESS_RUNTIME.shiftSet(), BUSINESS_RUNTIME.identity());
    }

    static BusinessRuntimeObservationSnapshot observe(long businessDay, int hour, int minute) {
        return BusinessRuntimeObservationSnapshot.observe(
                calendar(businessDay, hour, minute),
                BUSINESS_RUNTIME,
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT
        );
    }

    static BusinessCalendarSnapshot calendar(long businessDay, int hour, int minute) {
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                businessDay,
                BusinessDayOfWeek.fromDayIndex(businessDay),
                new BusinessTimeOfDay(hour, minute),
                0L,
                0L,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/test/" + businessDay,
                WORLD_TIME_CONFIGURATION.identity(),
                "minecraft:overworld",
                0L,
                0L
        );
    }
}
