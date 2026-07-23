package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class AllocationReportRegistry {
    private static final AllocationReportRegistry EMPTY =
            new AllocationReportRegistry(List.of());

    private final List<AllocationReport> reports;
    private final Map<AllocationCycleId, AllocationReport> byCycle;
    private final Map<Long, AllocationReport> byTick;

    private AllocationReportRegistry(Collection<AllocationReport> source) {
        List<AllocationReport> canonical = new ArrayList<>(
                AllocationValidation.required(source, "reports")
        );
        canonical.forEach(report -> AllocationValidation.required(report, "report"));
        canonical.sort(Comparator.naturalOrder());
        Map<AllocationCycleId, AllocationReport> cycles = new LinkedHashMap<>();
        Map<Long, AllocationReport> ticks = new TreeMap<>();
        for (AllocationReport report : canonical) {
            if (cycles.putIfAbsent(report.allocationCycleId(), report) != null
                    || ticks.putIfAbsent(report.simulationTick(), report) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        report.allocationCycleId().value(),
                        "Duplicate Allocation report Cycle or tick"
                );
            }
        }
        reports = List.copyOf(canonical);
        byCycle = Collections.unmodifiableMap(cycles);
        byTick = Collections.unmodifiableMap(ticks);
    }

    public static AllocationReportRegistry empty() {
        return EMPTY;
    }

    public static AllocationReportRegistry of(Collection<AllocationReport> reports) {
        Collection<AllocationReport> source = AllocationValidation.required(
                reports,
                "reports"
        );
        return source.isEmpty() ? EMPTY : new AllocationReportRegistry(source);
    }

    public boolean contains(AllocationCycleId id) {
        return byCycle.containsKey(AllocationValidation.required(id, "id"));
    }

    public Optional<AllocationReport> find(AllocationCycleId id) {
        return Optional.ofNullable(byCycle.get(AllocationValidation.required(id, "id")));
    }

    public Optional<AllocationReport> findByTick(long tick) {
        return Optional.ofNullable(byTick.get(
                AllocationValidation.tick(tick, "tick")
        ));
    }

    public int size() {
        return reports.size();
    }

    public List<AllocationReport> reports() {
        return reports;
    }

    public Stream<AllocationReport> stream() {
        return reports.stream();
    }

    public List<AllocationReport> findBetween(long firstInclusive, long lastInclusive) {
        AllocationValidation.tick(firstInclusive, "firstInclusive");
        AllocationValidation.tick(lastInclusive, "lastInclusive");
        if (lastInclusive < firstInclusive) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_SIMULATION_TICK,
                    "reports",
                    "Allocation report tick range is reversed"
            );
        }
        return reports.stream()
                .filter(report -> report.simulationTick() >= firstInclusive
                        && report.simulationTick() <= lastInclusive)
                .toList();
    }
}
