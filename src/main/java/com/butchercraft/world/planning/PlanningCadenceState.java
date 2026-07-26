package com.butchercraft.world.planning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

final class PlanningCadenceState {
    static final String PERIODIC_REASON = "butchercraft:planning_eligibility/periodic";
    static final String TRIGGER_REASON = "butchercraft:planning_eligibility/trigger";

    private final PlanningCadenceConfiguration configuration;
    private OptionalLong lastCompletedCycleTick;
    private long nextPeriodicEligibilityTick;
    private final Map<String, PlanningTriggerRecord> pendingTriggers = new LinkedHashMap<>();
    private final Map<String, PlanningTriggerRecord> knownTriggers = new LinkedHashMap<>();
    private final Map<PlanningCycleId, PlanningCycleCadenceEvidence> evidenceByCycle = new LinkedHashMap<>();
    private Optional<PlanningCycleId> activeCycleId;
    private long revision;

    private PlanningCadenceState(
            PlanningCadenceConfiguration configuration,
            OptionalLong lastCompletedCycleTick,
            long nextPeriodicEligibilityTick,
            Collection<PlanningTriggerRecord> pendingTriggers,
            Collection<PlanningCycleCadenceEvidence> cycleEvidence,
            Optional<PlanningCycleId> activeCycleId,
            long revision
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.lastCompletedCycleTick = Objects.requireNonNull(lastCompletedCycleTick, "lastCompletedCycleTick");
        this.nextPeriodicEligibilityTick = PlanningValidation.tick(nextPeriodicEligibilityTick);
        this.activeCycleId = Objects.requireNonNull(activeCycleId, "activeCycleId");
        if (revision < 0L) throw new IllegalArgumentException("Planning cadence revision must not be negative");
        this.revision = revision;
        cycleEvidence.stream().sorted(Comparator
                .comparingLong(PlanningCycleCadenceEvidence::simulationTick)
                .thenComparing(PlanningCycleCadenceEvidence::cycleId)).forEach(evidence -> {
            if (evidenceByCycle.putIfAbsent(evidence.cycleId(), evidence) != null) {
                throw new IllegalArgumentException("Duplicate Planning cadence evidence");
            }
            for (PlanningTriggerRecord trigger : evidence.consumedTriggers()) {
                rememberTrigger(trigger);
            }
        });
        pendingTriggers.stream().sorted().forEach(trigger -> {
            rememberTrigger(trigger);
            this.pendingTriggers.put(trigger.triggerIdentity(), trigger);
        });
        validate();
    }

    static PlanningCadenceState empty(PlanningCadenceConfiguration configuration) {
        return new PlanningCadenceState(
                configuration,
                OptionalLong.empty(),
                0L,
                List.of(),
                List.of(),
                Optional.empty(),
                0L
        );
    }

    static PlanningCadenceState legacyFromCycles(
            PlanningCadenceConfiguration configuration,
            Collection<PlanningCycleSnapshot> cycles
    ) {
        OptionalLong latest = cycles.stream().mapToLong(PlanningCycleSnapshot::simulationTick).max();
        long next = latest.isPresent() ? addCapped(latest.orElseThrow(), configuration.periodicIntervalTicks()) : 0L;
        return new PlanningCadenceState(
                configuration,
                latest,
                next,
                List.of(),
                List.of(),
                Optional.empty(),
                0L
        );
    }

    static PlanningCadenceState loaded(PlanningCadenceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new PlanningCadenceState(
                snapshot.configuration(),
                snapshot.lastCompletedCycleTick(),
                snapshot.nextPeriodicEligibilityTick(),
                snapshot.pendingTriggers(),
                snapshot.cycleEvidence(),
                snapshot.activeCycleId(),
                snapshot.revision()
        );
    }

    synchronized PlanningCadenceConfiguration configuration() {
        return configuration;
    }

    synchronized String configurationIdentity() {
        return configuration.configurationIdentity();
    }

    synchronized Optional<PlanningCycleCadenceEvidence> evidenceFor(PlanningCycleId id) {
        return Optional.ofNullable(evidenceByCycle.get(Objects.requireNonNull(id, "id")));
    }

    synchronized List<PlanningCycleCadenceEvidence> cycleEvidence() {
        return List.copyOf(evidenceByCycle.values());
    }

    synchronized List<PlanningTriggerRecord> pendingTriggers() {
        return List.copyOf(pendingTriggers.values().stream().sorted().toList());
    }

    synchronized OptionalLong lastCompletedCycleTick() {
        return lastCompletedCycleTick;
    }

    synchronized long nextPeriodicEligibilityTick() {
        return nextPeriodicEligibilityTick;
    }

    synchronized Optional<PlanningCycleId> activeCycleId() {
        return activeCycleId;
    }

    synchronized long revision() {
        return revision;
    }

    synchronized PlanningTriggerPublicationResult publishTrigger(PlanningTriggerRecord trigger, long currentTick) {
        Objects.requireNonNull(trigger, "trigger");
        PlanningValidation.tick(currentTick);
        if (!allowedSourceOwner(trigger.sourceOwner())) {
            return PlanningTriggerPublicationResult.failure(
                    PlanningFailureCode.PLANNING_TRIGGER_OWNER_INVALID,
                    "Planning trigger source owner is not a recognized Planning input authority"
            );
        }
        PlanningTriggerRecord existing = knownTriggers.get(trigger.triggerIdentity());
        if (existing != null) {
            if (existing.sameContentAs(trigger)) {
                return PlanningTriggerPublicationResult.duplicate(nextEligibilityTick(currentTick));
            }
            return PlanningTriggerPublicationResult.failure(
                    PlanningFailureCode.PLANNING_TRIGGER_IDENTITY_CONFLICT,
                    "Planning trigger identity conflicts with different content"
            );
        }
        if (pendingTriggers.size() >= configuration.pendingTriggerLimit()) {
            return PlanningTriggerPublicationResult.failure(
                    PlanningFailureCode.PLANNING_TRIGGER_CAPACITY_EXHAUSTED,
                    "Planning pending trigger capacity is exhausted"
            );
        }
        rememberTrigger(trigger);
        pendingTriggers.put(trigger.triggerIdentity(), trigger);
        revision = Math.incrementExact(revision);
        return PlanningTriggerPublicationResult.accepted(nextEligibilityTick(currentTick));
    }

    synchronized PlanningCadenceBoundary beginCycle(PlanningCycleId cycleId, long tick) {
        Objects.requireNonNull(cycleId, "cycleId");
        PlanningValidation.tick(tick);
        if (activeCycleId.isPresent()) {
            throw new IllegalStateException("Planning Cycle already active: " + activeCycleId.orElseThrow().value());
        }
        if (!cycleId.equals(PlanningCycleId.forTick(tick))) {
            throw new IllegalArgumentException("Planning Cycle id does not match cadence tick");
        }
        long earliest = earliestBySeparation(tick);
        if (tick < earliest) {
            throw new IllegalStateException("Planning Cycle violates minimum cadence separation");
        }
        List<PlanningTriggerRecord> consumed = pendingTriggers.values().stream()
                .filter(trigger -> trigger.authoritativeSimulationTick() <= tick)
                .sorted()
                .toList();
        boolean periodic = tick >= nextPeriodicEligibilityTick;
        if (!periodic && consumed.isEmpty()) {
            throw new IllegalStateException("Planning Cycle is not cadence-eligible");
        }
        activeCycleId = Optional.of(cycleId);
        revision = Math.incrementExact(revision);
        List<String> reasons = new ArrayList<>();
        if (periodic) reasons.add(PERIODIC_REASON);
        if (!consumed.isEmpty()) reasons.add(TRIGGER_REASON);
        return new PlanningCadenceBoundary(cycleId, tick, configurationIdentity(), reasons, consumed);
    }

    synchronized PlanningCycleCadenceEvidence completeCycle(
            PlanningCadenceBoundary boundary,
            String frozenInputIdentity
    ) {
        Objects.requireNonNull(boundary, "boundary");
        frozenInputIdentity = PlanningValidation.id(frozenInputIdentity, "Planning frozen input identity");
        if (activeCycleId.isEmpty() || !activeCycleId.orElseThrow().equals(boundary.cycleId())) {
            throw new IllegalStateException("Planning active cycle does not match cadence completion");
        }
        lastCompletedCycleTick = OptionalLong.of(boundary.simulationTick());
        boundary.consumedTriggers().forEach(trigger -> pendingTriggers.remove(trigger.triggerIdentity()));
        nextPeriodicEligibilityTick = addCapped(
                boundary.simulationTick(),
                Math.min(configuration.periodicIntervalTicks(), configuration.maximumIntervalTicks())
        );
        activeCycleId = Optional.empty();
        long next = nextEligibilityTick(boundary.simulationTick());
        PlanningCycleCadenceEvidence evidence = new PlanningCycleCadenceEvidence(
                boundary.cycleId(),
                boundary.simulationTick(),
                boundary.cadenceConfigurationIdentity(),
                boundary.eligibilityReasons(),
                boundary.consumedTriggers(),
                frozenInputIdentity,
                boundary.simulationTick(),
                next,
                PlanningValidation.SCHEMA_VERSION
        );
        if (evidenceByCycle.putIfAbsent(boundary.cycleId(), evidence) != null) {
            throw new IllegalArgumentException("Duplicate Planning cadence evidence");
        }
        revision = Math.incrementExact(revision);
        return evidence;
    }

    synchronized void abortActiveCycle(PlanningCycleId cycleId) {
        if (activeCycleId.filter(cycleId::equals).isPresent()) {
            activeCycleId = Optional.empty();
            revision = Math.incrementExact(revision);
        }
    }

    synchronized void recordLegacyCompletedCycle(PlanningCycleSnapshot cycle) {
        Objects.requireNonNull(cycle, "cycle");
        if (cycle.cadenceEvidence().isPresent()) return;
        if (lastCompletedCycleTick.isEmpty() || cycle.simulationTick() > lastCompletedCycleTick.orElseThrow()) {
            lastCompletedCycleTick = OptionalLong.of(cycle.simulationTick());
            nextPeriodicEligibilityTick = addCapped(cycle.simulationTick(), configuration.periodicIntervalTicks());
            revision = Math.incrementExact(revision);
        }
    }

    synchronized boolean eligibleAt(long tick) {
        PlanningValidation.tick(tick);
        if (tick < earliestBySeparation(tick)) return false;
        if (tick >= nextPeriodicEligibilityTick) return true;
        return pendingTriggers.values().stream()
                .anyMatch(trigger -> trigger.authoritativeSimulationTick() <= tick);
    }

    synchronized long nextEligibilityTick(long currentTick) {
        PlanningValidation.tick(currentTick);
        long earliest = earliestBySeparation(currentTick);
        long periodic = Math.max(earliest, nextPeriodicEligibilityTick);
        long trigger = pendingTriggers.values().stream()
                .mapToLong(PlanningTriggerRecord::authoritativeSimulationTick)
                .filter(tick -> tick >= 0L)
                .min()
                .stream()
                .map(value -> Math.max(earliest, value))
                .findFirst()
                .orElse(Long.MAX_VALUE);
        long result = Math.min(periodic, trigger);
        if (result == Long.MAX_VALUE) {
            throw new IllegalStateException("Planning next eligibility cannot be determined");
        }
        return Math.max(result, Math.addExact(currentTick, result <= currentTick ? 1L : 0L));
    }

    synchronized PlanningCadenceSnapshot snapshot() {
        validate();
        return new PlanningCadenceSnapshot(
                PlanningValidation.SCHEMA_VERSION,
                configuration,
                configurationIdentity(),
                lastCompletedCycleTick,
                nextPeriodicEligibilityTick,
                pendingTriggers(),
                cycleEvidence(),
                activeCycleId,
                revision
        );
    }

    synchronized void validate() {
        if (!configurationIdentity().equals(configuration.configurationIdentity())) {
            throw new IllegalArgumentException("Planning cadence configuration identity mismatch");
        }
        lastCompletedCycleTick.ifPresent(tick -> {
            PlanningValidation.tick(tick);
            if (nextPeriodicEligibilityTick < tick) {
                throw new IllegalArgumentException("Planning cadence next periodic tick moves backward");
            }
        });
        if (pendingTriggers.size() > configuration.pendingTriggerLimit()) {
            throw new IllegalArgumentException("Planning cadence pending trigger capacity exceeded");
        }
        if (activeCycleId.isPresent()) {
            throw new IllegalArgumentException("Planning cadence cannot persist or load an active cycle");
        }
    }

    private long earliestBySeparation(long currentTick) {
        if (lastCompletedCycleTick.isEmpty()) return 0L;
        long candidate = addCapped(lastCompletedCycleTick.orElseThrow(), configuration.minimumSeparationTicks());
        return Math.max(candidate, 0L);
    }

    private void rememberTrigger(PlanningTriggerRecord trigger) {
        PlanningTriggerRecord existing = knownTriggers.putIfAbsent(trigger.triggerIdentity(), trigger);
        if (existing != null && !existing.sameContentAs(trigger)) {
            throw new IllegalArgumentException("Planning trigger identity conflict");
        }
    }

    private static boolean allowedSourceOwner(String owner) {
        return switch (owner) {
            case "butchercraft:orders",
                 "butchercraft:contracts",
                 "butchercraft:production",
                 "butchercraft:inventory",
                 "butchercraft:business_runtime",
                 "butchercraft:workforce",
                 "butchercraft:planning" -> true;
            default -> false;
        };
    }

    private static long addCapped(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Planning next eligibility tick overflow", exception);
        }
    }
}

record PlanningCadenceBoundary(
        PlanningCycleId cycleId,
        long simulationTick,
        String cadenceConfigurationIdentity,
        List<String> eligibilityReasons,
        List<PlanningTriggerRecord> consumedTriggers
) {
    PlanningCadenceBoundary {
        Objects.requireNonNull(cycleId, "cycleId");
        simulationTick = PlanningValidation.tick(simulationTick);
        cadenceConfigurationIdentity = PlanningValidation.id(
                cadenceConfigurationIdentity,
                "Planning cadence configuration identity"
        );
        eligibilityReasons = List.copyOf(eligibilityReasons);
        consumedTriggers = consumedTriggers.stream().sorted().toList();
    }
}

record PlanningCadenceExecutionResult(
        boolean cycleExecuted,
        Optional<PlanningCycleSnapshot> cycle,
        long nextEligibleTick,
        List<String> messages,
        int workUnits
) {
    PlanningCadenceExecutionResult {
        cycle = Objects.requireNonNull(cycle, "cycle");
        nextEligibleTick = PlanningValidation.tick(nextEligibleTick);
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (workUnits <= 0) throw new IllegalArgumentException("Planning cadence work units must be positive");
        if (cycleExecuted != cycle.isPresent()) {
            throw new IllegalArgumentException("Planning cadence result cycle presence is inconsistent");
        }
    }

    static PlanningCadenceExecutionResult idle(long nextEligibleTick) {
        return new PlanningCadenceExecutionResult(
                false,
                Optional.empty(),
                nextEligibleTick,
                List.of("Economic Planning Cycle not cadence-eligible"),
                1
        );
    }

    static PlanningCadenceExecutionResult executed(
            PlanningCycleSnapshot cycle,
            long nextEligibleTick,
            long artifactCount,
            long remainingWorkUnits
    ) {
        int units = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                Math.min(remainingWorkUnits, Math.max(1L, artifactCount))));
        return new PlanningCadenceExecutionResult(
                true,
                Optional.of(cycle),
                nextEligibleTick,
                List.of("Economic Planning Cycle " + cycle.status().name().toLowerCase(java.util.Locale.ROOT)),
                units
        );
    }
}

record PlanningCadenceSnapshot(
        int schemaVersion,
        PlanningCadenceConfiguration configuration,
        String configurationIdentity,
        OptionalLong lastCompletedCycleTick,
        long nextPeriodicEligibilityTick,
        List<PlanningTriggerRecord> pendingTriggers,
        List<PlanningCycleCadenceEvidence> cycleEvidence,
        Optional<PlanningCycleId> activeCycleId,
        long revision
) {
    PlanningCadenceSnapshot {
        schemaVersion = PlanningValidation.schema(schemaVersion);
        configuration = Objects.requireNonNull(configuration, "configuration");
        configurationIdentity = PlanningValidation.id(configurationIdentity, "Planning cadence configuration identity");
        if (!configurationIdentity.equals(configuration.configurationIdentity())) {
            throw new IllegalArgumentException("Planning cadence configuration identity mismatch");
        }
        lastCompletedCycleTick = Objects.requireNonNull(lastCompletedCycleTick, "lastCompletedCycleTick");
        nextPeriodicEligibilityTick = PlanningValidation.tick(nextPeriodicEligibilityTick);
        pendingTriggers = Objects.requireNonNull(pendingTriggers, "pendingTriggers").stream().sorted().toList();
        cycleEvidence = Objects.requireNonNull(cycleEvidence, "cycleEvidence").stream()
                .sorted(Comparator.comparingLong(PlanningCycleCadenceEvidence::simulationTick)
                        .thenComparing(PlanningCycleCadenceEvidence::cycleId))
                .toList();
        activeCycleId = Objects.requireNonNull(activeCycleId, "activeCycleId");
        if (revision < 0L) throw new IllegalArgumentException("Planning cadence revision must not be negative");
    }
}
