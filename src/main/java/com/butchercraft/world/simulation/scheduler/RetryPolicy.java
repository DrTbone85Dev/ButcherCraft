package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.OptionalLong;

public record RetryPolicy(
        RetryPolicyType type,
        OptionalLong intervalSimulationTicks,
        OptionalLong maximumRetryTick
) {
    public RetryPolicy {
        type = Objects.requireNonNull(type, "type");
        intervalSimulationTicks = Objects.requireNonNull(intervalSimulationTicks, "intervalSimulationTicks");
        maximumRetryTick = Objects.requireNonNull(maximumRetryTick, "maximumRetryTick");
        boolean needsInterval = type == RetryPolicyType.FIXED_INTERVAL
                || type == RetryPolicyType.EXPONENTIAL_SIMULATION_INTERVAL;
        if (needsInterval != intervalSimulationTicks.isPresent()) {
            throw new IllegalArgumentException("Retry interval presence does not match policy type");
        }
        intervalSimulationTicks.ifPresent(value -> {
            if (value <= 0L) throw new IllegalArgumentException("Retry interval must be positive");
        });
        maximumRetryTick.ifPresent(value -> SchedulerValidation.requireTick(value, "Maximum retry tick"));
    }

    public static RetryPolicy never() {
        return new RetryPolicy(RetryPolicyType.NEVER, OptionalLong.empty(), OptionalLong.empty());
    }
    public static RetryPolicy nextTick() {
        return new RetryPolicy(RetryPolicyType.NEXT_TICK, OptionalLong.empty(), OptionalLong.empty());
    }
    public static RetryPolicy fixedInterval(long ticks) {
        return new RetryPolicy(RetryPolicyType.FIXED_INTERVAL, OptionalLong.of(ticks), OptionalLong.empty());
    }
    public static RetryPolicy exponential(long baseTicks) {
        return new RetryPolicy(
                RetryPolicyType.EXPONENTIAL_SIMULATION_INTERVAL, OptionalLong.of(baseTicks), OptionalLong.empty()
        );
    }

    public long nextEligibleTick(long currentTick, int completedAttemptCount, OptionalLong handlerRequestedTick) {
        SchedulerValidation.requireTick(currentTick, "Retry current tick");
        if (completedAttemptCount <= 0) throw new IllegalArgumentException("Retry requires a completed attempt");
        long next = switch (type) {
            case NEVER -> throw new IllegalStateException("Retry policy does not permit retry");
            case NEXT_TICK -> Math.addExact(currentTick, 1L);
            case FIXED_INTERVAL -> Math.addExact(currentTick, intervalSimulationTicks.orElseThrow());
            case EXPONENTIAL_SIMULATION_INTERVAL -> {
                long multiplier = completedAttemptCount >= 63 ? Long.MAX_VALUE : 1L << (completedAttemptCount - 1);
                yield Math.addExact(currentTick, Math.multiplyExact(intervalSimulationTicks.orElseThrow(), multiplier));
            }
            case HANDLER_REQUESTED -> {
                long requested = handlerRequestedTick.orElseThrow(() ->
                        new IllegalArgumentException("Handler-requested retry requires a next eligible tick"));
                if (requested <= currentTick) throw new IllegalArgumentException("Retry tick must follow current tick");
                yield requested;
            }
        };
        if (maximumRetryTick.isPresent() && next > maximumRetryTick.orElseThrow()) {
            throw new IllegalStateException("Retry tick exceeds policy maximum");
        }
        return next;
    }
}
