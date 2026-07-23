package com.butchercraft.world.simulation.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class SimulationSchedulerRegistry {
    private final Map<SimulationWorkId, ScheduledSimulationWork> byId;
    private final List<ScheduledSimulationWork> definitions;
    private final Map<SimulationWorkTypeId, List<ScheduledSimulationWork>> byType;
    private final Map<SimulationStageId, List<ScheduledSimulationWork>> byStage;
    private final Map<String, List<ScheduledSimulationWork>> byOrigin;
    private final Map<String, List<ScheduledSimulationWork>> byCorrelation;
    private final Map<WorkReference, List<ScheduledSimulationWork>> byReference;
    private SimulationSchedulerRegistry(Collection<ScheduledSimulationWork> source) {
        Map<SimulationWorkId, ScheduledSimulationWork> ids = new LinkedHashMap<>();
        Set<Long> sequences = new HashSet<>();
        for (ScheduledSimulationWork work : Objects.requireNonNull(source, "definitions")) {
            ScheduledSimulationWork value = Objects.requireNonNull(work, "work");
            if (ids.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate simulation work id: " + value.id().value());
            }
            if (!sequences.add(value.authoritativeSubmissionSequence())) {
                throw new IllegalArgumentException("Duplicate submission sequence: "
                        + value.authoritativeSubmissionSequence());
            }
        }
        byId = Collections.unmodifiableMap(ids);
        definitions = List.copyOf(ids.values());
        byType = groups(definitions, ScheduledSimulationWork::typeId);
        byStage = groups(definitions, ScheduledSimulationWork::stageId);
        byOrigin = groups(definitions, work -> work.origin().sourceSubsystemId());
        byCorrelation = optionalGroups(definitions, work -> work.origin().correlationId());
        byReference = referenceGroups(definitions);
    }
    public static SimulationSchedulerRegistry empty() { return of(List.of()); }
    public static SimulationSchedulerRegistry of(Collection<ScheduledSimulationWork> source) {
        return new SimulationSchedulerRegistry(source);
    }
    public static SimulationSchedulerRegistryBuilder builder() { return new SimulationSchedulerRegistryBuilder(); }
    public SimulationSchedulerRegistry withWork(ScheduledSimulationWork work) {
        List<ScheduledSimulationWork> updated = new ArrayList<>(definitions);
        updated.add(Objects.requireNonNull(work, "work"));
        return of(updated);
    }
    public boolean contains(SimulationWorkId id) { return byId.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<ScheduledSimulationWork> find(SimulationWorkId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }
    public List<ScheduledSimulationWork> definitions() { return definitions; }
    public int size() { return definitions.size(); }
    public List<ScheduledSimulationWork> findByType(SimulationWorkTypeId id) { return get(byType, id); }
    public List<ScheduledSimulationWork> findByStage(SimulationStageId id) { return get(byStage, id); }
    public List<ScheduledSimulationWork> findByOriginSubsystem(String id) { return get(byOrigin, id); }
    public List<ScheduledSimulationWork> findByCorrelationId(String id) { return get(byCorrelation, id); }
    public List<ScheduledSimulationWork> findByReference(WorkReference reference) { return get(byReference, reference); }
    public List<ScheduledSimulationWork> findScheduledBetween(long first, long last) {
        SchedulerValidation.requireTick(first, "Scheduled query first tick");
        SchedulerValidation.requireTick(last, "Scheduled query last tick");
        if (last < first) throw new IllegalArgumentException("Scheduled query range is reversed");
        return definitions.stream().filter(work -> work.scheduledTick() >= first && work.scheduledTick() <= last).toList();
    }
    private static <K> Map<K, List<ScheduledSimulationWork>> groups(
            List<ScheduledSimulationWork> source, Function<ScheduledSimulationWork, K> key
    ) {
        Map<K, List<ScheduledSimulationWork>> mutable = new LinkedHashMap<>();
        for (ScheduledSimulationWork work : source) {
            mutable.computeIfAbsent(key.apply(work), ignored -> new ArrayList<>()).add(work);
        }
        return immutableGroups(mutable);
    }
    private static <K> Map<K, List<ScheduledSimulationWork>> optionalGroups(
            List<ScheduledSimulationWork> source, Function<ScheduledSimulationWork, Optional<K>> key
    ) {
        Map<K, List<ScheduledSimulationWork>> mutable = new LinkedHashMap<>();
        for (ScheduledSimulationWork work : source) {
            key.apply(work).ifPresent(value -> mutable.computeIfAbsent(value, ignored -> new ArrayList<>()).add(work));
        }
        return immutableGroups(mutable);
    }
    private static Map<WorkReference, List<ScheduledSimulationWork>> referenceGroups(
            List<ScheduledSimulationWork> source
    ) {
        Map<WorkReference, List<ScheduledSimulationWork>> mutable = new LinkedHashMap<>();
        for (ScheduledSimulationWork work : source) {
            for (WorkReference reference : work.references()) {
                mutable.computeIfAbsent(reference, ignored -> new ArrayList<>()).add(work);
            }
        }
        return immutableGroups(mutable);
    }
    private static <K> Map<K, List<ScheduledSimulationWork>> immutableGroups(
            Map<K, List<ScheduledSimulationWork>> source
    ) {
        Map<K, List<ScheduledSimulationWork>> immutable = new LinkedHashMap<>();
        source.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }
    private static <K> List<ScheduledSimulationWork> get(Map<K, List<ScheduledSimulationWork>> source, K key) {
        return source.getOrDefault(Objects.requireNonNull(key, "key"), List.of());
    }
}
