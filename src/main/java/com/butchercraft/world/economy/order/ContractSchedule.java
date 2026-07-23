package com.butchercraft.world.economy.order;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record ContractSchedule(
        ContractScheduleType type,
        OptionalLong intervalSimulationTicks,
        Optional<String> periodKey
) {
    public ContractSchedule {
        type = Objects.requireNonNull(type, "type");
        intervalSimulationTicks = Objects.requireNonNull(intervalSimulationTicks, "intervalSimulationTicks");
        periodKey = DomainValidation.optionalText(periodKey, "Contract schedule period key", 128);
        if (type == ContractScheduleType.INTERVAL) {
            if (intervalSimulationTicks.isEmpty() || intervalSimulationTicks.orElseThrow() <= 0L) {
                throw new IllegalArgumentException("Interval contract schedule requires positive simulation ticks");
            }
        } else if (intervalSimulationTicks.isPresent()) {
            throw new IllegalArgumentException("Only interval contract schedules may define interval ticks");
        }
        if (type == ContractScheduleType.SEASONAL && periodKey.isEmpty()) {
            throw new IllegalArgumentException("Seasonal contract schedule requires a period key");
        }
    }

    public static ContractSchedule oneTime() {
        return new ContractSchedule(ContractScheduleType.ONE_TIME, OptionalLong.empty(), Optional.empty());
    }

    public static ContractSchedule interval(long simulationTicks) {
        return new ContractSchedule(
                ContractScheduleType.INTERVAL,
                OptionalLong.of(simulationTicks),
                Optional.empty()
        );
    }
}
