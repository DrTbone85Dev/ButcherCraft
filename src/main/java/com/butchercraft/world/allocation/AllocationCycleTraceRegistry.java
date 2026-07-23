package com.butchercraft.world.allocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class AllocationCycleTraceRegistry {
    private static final AllocationCycleTraceRegistry EMPTY =
            new AllocationCycleTraceRegistry(List.of());

    private final List<AllocationCycleTrace> traces;
    private final Map<AllocationCycleId, AllocationCycleTrace> byCycle;

    private AllocationCycleTraceRegistry(Collection<AllocationCycleTrace> source) {
        TreeMap<AllocationCycleId, AllocationCycleTrace> canonical = new TreeMap<>();
        for (AllocationCycleTrace trace : AllocationValidation.required(
                source,
                "traces"
        )) {
            AllocationCycleTrace value = AllocationValidation.required(trace, "trace");
            if (canonical.putIfAbsent(value.cycleId(), value) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        value.cycleId().value(),
                        "Duplicate Allocation Cycle trace"
                );
            }
        }
        traces = List.copyOf(canonical.values());
        byCycle = Collections.unmodifiableMap(new LinkedHashMap<>(canonical));
    }

    public static AllocationCycleTraceRegistry empty() {
        return EMPTY;
    }

    public static AllocationCycleTraceRegistry of(
            Collection<AllocationCycleTrace> traces
    ) {
        Collection<AllocationCycleTrace> source = AllocationValidation.required(
                traces,
                "traces"
        );
        return source.isEmpty() ? EMPTY : new AllocationCycleTraceRegistry(source);
    }

    public int size() {
        return traces.size();
    }

    public List<AllocationCycleTrace> traces() {
        return traces;
    }

    public Optional<AllocationCycleTrace> find(AllocationCycleId cycleId) {
        return Optional.ofNullable(byCycle.get(
                AllocationValidation.required(cycleId, "cycleId")
        ));
    }

    public Stream<AllocationCycleTrace> stream() {
        return traces.stream();
    }
}
