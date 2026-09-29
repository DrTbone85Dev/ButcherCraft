package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Rebuildable scheduling hint. Every returned entry must be revalidated against its owner projection. */
public final class ConditionDueIndex {
    public record Entry(WorkstationInstanceId instanceId, long conditionRevision, String conditionDigest, long dueTick) { }
    private final Map<WorkstationInstanceId, Entry> byInstance = new HashMap<>();
    private final TreeSet<Entry> due = new TreeSet<>(Comparator.comparingLong(Entry::dueTick)
            .thenComparing(entry -> entry.instanceId().value()));

    public void observe(MachineConditionState state) {
        remove(state.instanceId());
        state.activeExposure().ifPresent(active -> {
            long cadence = state.policy().exposure(active.type()).orElseThrow().settlementTicks();
            Entry entry = new Entry(state.instanceId(), state.revision(), state.digest(),
                    Math.addExact(active.accountedThroughTick(), Math.min(cadence,
                            WorkstationConditionEngine.ticksUntilFault(state))));
            byInstance.put(state.instanceId(), entry);
            due.add(entry);
        });
    }

    public List<Entry> due(long tick, int maximum) {
        if (tick < 0 || maximum <= 0) throw new IllegalArgumentException("Invalid condition due-work budget");
        return due.stream().takeWhile(entry -> entry.dueTick() <= tick).limit(maximum).toList();
    }

    public void remove(WorkstationInstanceId instance) {
        Entry previous = byInstance.remove(instance);
        if (previous != null) due.remove(previous);
    }

    public void clear() { byInstance.clear(); due.clear(); }
    public int size() { return due.size(); }
}
