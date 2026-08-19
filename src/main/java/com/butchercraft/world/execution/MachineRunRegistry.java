package com.butchercraft.world.execution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public record MachineRunRegistry(
        int schemaVersion,
        long ownerRevision,
        String worldIdentity,
        String configurationIdentity,
        List<MachineRunGenerationAllocator> generationAllocators,
        List<MachineRunRecord> runs
) {
    public MachineRunRegistry {
        if (schemaVersion != MachineRunSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Machine Run registry schema: " + schemaVersion);
        }
        if (ownerRevision < 0L) throw new IllegalArgumentException("Machine Run owner revision must not be negative");
        worldIdentity = ExecutionValidation.requireId(worldIdentity, "Machine Run registry World Identity");
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine Run registry configuration identity"
        );
        generationAllocators = Objects.requireNonNull(generationAllocators, "generationAllocators")
                .stream().sorted().toList();
        runs = Objects.requireNonNull(runs, "runs").stream().sorted().toList();

        Map<String, MachineRunGenerationAllocator> allocatorsByInstance = new HashMap<>();
        for (MachineRunGenerationAllocator allocator : generationAllocators) {
            if (allocatorsByInstance.put(allocator.workstationInstanceIdentity(), allocator) != null) {
                throw new IllegalArgumentException("Duplicate Machine Run generation allocator");
            }
        }
        Map<MachineRunIdentity, MachineRunRecord> byIdentity = new LinkedHashMap<>();
        Map<String, MachineRunRecord> activeByInstance = new HashMap<>();
        Map<String, MachineRunRecord> startByRequest = new HashMap<>();
        Map<String, MachineRunRecord> stopByRequest = new HashMap<>();
        Map<String, Long> maximumGeneration = new HashMap<>();
        for (MachineRunRecord run : runs) {
            if (!run.worldIdentity().equals(worldIdentity)
                    || !run.configurationIdentity().equals(configurationIdentity)) {
                throw new IllegalArgumentException("Machine Run record has incompatible registry identity");
            }
            if (byIdentity.put(run.runIdentity(), run) != null) {
                throw new IllegalArgumentException("Duplicate Machine Run Identity: " + run.runIdentity().value());
            }
            if (!run.lifecycle().terminal()
                    && activeByInstance.put(run.workstationInstanceIdentity(), run) != null) {
                throw new IllegalArgumentException("More than one active Machine Run exists for a Workstation instance");
            }
            if (startByRequest.put(run.startEvidence().requestKey(), run) != null) {
                throw new IllegalArgumentException("Duplicate Machine START source request identity");
            }
            run.stopEvidence().ifPresent(stop -> {
                if (stopByRequest.put(stop.requestKey(), run) != null) {
                    throw new IllegalArgumentException("Duplicate Machine STOP source request identity");
                }
            });
            maximumGeneration.merge(run.workstationInstanceIdentity(), run.generation(), Math::max);
        }
        for (Map.Entry<String, Long> maximum : maximumGeneration.entrySet()) {
            MachineRunGenerationAllocator allocator = allocatorsByInstance.get(maximum.getKey());
            if (allocator == null || allocator.nextGeneration() <= maximum.getValue()) {
                throw new IllegalArgumentException("Machine Run generation allocator has regressed");
            }
        }
    }

    public static MachineRunRegistry empty(String worldIdentity, MachineRunConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new MachineRunRegistry(
                MachineRunSchema.CURRENT_VERSION,
                0L,
                worldIdentity,
                configuration.configurationIdentity(),
                List.of(),
                List.of()
        );
    }

    public Optional<MachineRunRecord> find(MachineRunIdentity identity) {
        return runs.stream().filter(run -> run.runIdentity().equals(identity)).findFirst();
    }

    public Optional<MachineRunRecord> activeFor(String workstationInstanceIdentity) {
        return runs.stream()
                .filter(run -> run.workstationInstanceIdentity().equals(workstationInstanceIdentity))
                .filter(run -> !run.lifecycle().terminal())
                .max(Comparator.comparingLong(MachineRunRecord::generation));
    }

    public Optional<MachineRunRecord> startForRequest(String sourceOwner, String sourceRequestIdentity) {
        String key = sourceOwner + "\n" + sourceRequestIdentity;
        return runs.stream().filter(run -> run.startEvidence().requestKey().equals(key)).findFirst();
    }

    public Optional<MachineRunRecord> stopForRequest(String sourceOwner, String sourceRequestIdentity) {
        String key = sourceOwner + "\n" + sourceRequestIdentity;
        return runs.stream()
                .filter(run -> run.stopEvidence().map(MachineStopAuthorizationEvidence::requestKey)
                        .filter(key::equals).isPresent())
                .findFirst();
    }

    public MachineRunRegistryMutation acceptStart(
            MachineStartAuthorizationEvidence evidence,
            MachineRunConfiguration configuration,
            long tick
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(configuration, "configuration");
        if (!evidence.worldIdentity().equals(worldIdentity)
                || !evidence.configurationIdentity().equals(configurationIdentity)
                || !configuration.configurationIdentity().equals(configurationIdentity)) {
            return rejected(MachineRunResultCode.CONFIGURATION_MISMATCH, "Machine START configuration mismatch");
        }
        Optional<MachineRunRecord> priorRequest = startForRequest(
                evidence.sourceOwner(),
                evidence.sourceRequestIdentity()
        );
        if (priorRequest.isPresent()) {
            MachineRunRecord existing = priorRequest.orElseThrow();
            if (existing.startEvidence().sameIntent(evidence)) {
                return observed(existing, "Existing Machine Run observed for duplicate START");
            }
            return rejected(MachineRunResultCode.START_IDENTITY_CONFLICT,
                    "Machine START source request identity has conflicting content");
        }
        Optional<MachineRunRecord> active = activeFor(evidence.workstationInstanceIdentity());
        if (active.isPresent()) {
            return new MachineRunRegistryMutation(
                    MachineRunResultCode.ALREADY_RUNNING,
                    this,
                    active,
                    false,
                    "Workstation instance already has an active Machine Run"
            );
        }
        if (runs.size() >= configuration.maximumRetainedRuns()) {
            return rejected(MachineRunResultCode.CAPACITY_EXHAUSTED, "Machine Run retention capacity is exhausted");
        }
        long generation = allocatorFor(evidence.workstationInstanceIdentity())
                .map(MachineRunGenerationAllocator::nextGeneration)
                .orElse(1L);
        MachineRunRecord run = MachineRunRecord.create(generation, evidence, configurationIdentity, tick);
        List<MachineRunRecord> candidateRuns = new ArrayList<>(runs);
        candidateRuns.add(run);
        List<MachineRunGenerationAllocator> allocators = advanceAllocator(
                evidence.workstationInstanceIdentity(),
                Math.addExact(generation, 1L)
        );
        MachineRunRegistry candidate = new MachineRunRegistry(
                schemaVersion,
                Math.addExact(ownerRevision, 1L),
                worldIdentity,
                configurationIdentity,
                allocators,
                candidateRuns
        );
        return accepted(candidate, run, "Machine START accepted");
    }

    public MachineRunRegistryMutation acceptStop(MachineStopAuthorizationEvidence evidence, long tick) {
        Objects.requireNonNull(evidence, "evidence");
        Optional<MachineRunRecord> priorRequest = stopForRequest(
                evidence.sourceOwner(),
                evidence.sourceRequestIdentity()
        );
        if (priorRequest.isPresent()) {
            MachineRunRecord existing = priorRequest.orElseThrow();
            if (existing.stopEvidence().orElseThrow().sameIntent(evidence)) {
                return observed(existing, "Existing Machine STOP observed");
            }
            return rejected(MachineRunResultCode.STOP_IDENTITY_CONFLICT,
                    "Machine STOP source request identity has conflicting content");
        }
        MachineRunRecord target = find(evidence.targetRunIdentity()).orElse(null);
        if (target == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        if (!target.workstationInstanceIdentity().equals(evidence.workstationInstanceIdentity())) {
            return rejected(MachineRunResultCode.INSTANCE_MISMATCH, "Machine STOP instance does not match target Run");
        }
        if (target.lifecycle().terminal()
                || activeFor(target.workstationInstanceIdentity()).filter(target::equals).isEmpty()) {
            return rejected(MachineRunResultCode.STALE_RUN, "Machine STOP targets a historical or inactive Run");
        }
        if (target.revision() != evidence.expectedRunRevision()) {
            return rejected(MachineRunResultCode.STALE_REVISION, "Machine STOP expected Run revision is stale");
        }
        try {
            MachineRunRecord updated = target.requestStop(evidence, tick);
            return replace(updated, MachineRunResultCode.ACCEPTED, "Machine STOP accepted");
        } catch (IllegalStateException exception) {
            return rejected(MachineRunResultCode.INVALID_LIFECYCLE, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation prepareChild(
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            ExecutionAuthorizationEvidence authorization,
            MachineRunConfiguration configuration,
            long tick
    ) {
        MachineRunRecord run = find(runIdentity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        if (!Objects.requireNonNull(configuration, "configuration").configurationIdentity()
                .equals(configurationIdentity)) {
            return rejected(MachineRunResultCode.CONFIGURATION_MISMATCH,
                    "Machine Run child configuration does not match the registry");
        }
        Optional<MachineRunChildRecord> existing = run.childForOperation(ExecutionOperationId.derive(authorization));
        if (existing.isPresent()) {
            if (existing.orElseThrow().authorizationContentDigest()
                    .equals(authorization.authorizationContentDigest())) {
                return observed(run, "Existing Machine Run child observed");
            }
            return rejected(MachineRunResultCode.CHILD_IDENTITY_CONFLICT,
                    "Machine Run child identity has conflicting authorization content");
        }
        if (run.revision() != expectedRunRevision) {
            return rejected(MachineRunResultCode.STALE_REVISION, "Machine Run child expected revision is stale");
        }
        if (!run.lifecycle().authorizesChildren()) {
            return rejected(MachineRunResultCode.INVALID_LIFECYCLE,
                    "Machine Run does not currently authorize child admission");
        }
        if (run.currentChild().isPresent()) {
            return rejected(MachineRunResultCode.CHILD_ALREADY_ACTIVE,
                    "Machine Run already has one nonterminal child");
        }
        if (run.terminalChildren().size() >= configuration.maximumRetainedChildrenPerRun()) {
            return rejected(MachineRunResultCode.CAPACITY_EXHAUSTED,
                    "Machine Run retained child capacity is exhausted");
        }
        try {
            return replace(run.prepareChild(authorization, tick), MachineRunResultCode.ACCEPTED,
                    "Machine Run child prepared");
        } catch (IllegalArgumentException exception) {
            return rejected(MachineRunResultCode.CHILD_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation admitPreparedChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationSnapshot operation,
            long tick
    ) {
        MachineRunRecord run = find(runIdentity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        if (run.currentChild().filter(child -> child.state() == MachineRunChildState.ADMITTED
                && child.operationId().equals(operation.operationId())).isPresent()) {
            return observed(run, "Machine Run child was already admitted");
        }
        try {
            return replace(run.admitPreparedChild(operation, tick), MachineRunResultCode.ACCEPTED,
                    "Machine Run child admitted");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineRunResultCode.CHILD_NOT_PREPARED, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation cancelPreparedChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            MachineRunResultCode failureCode,
            long tick
    ) {
        MachineRunRecord run = find(runIdentity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        Optional<MachineRunChildRecord> prior = run.childForOperation(operationId);
        if (prior.filter(child -> child.state() == MachineRunChildState.CANCELLED).isPresent()) {
            return observed(run, "Prepared Machine Run child already cancelled");
        }
        try {
            return replace(
                    run.cancelPreparedChild(operationId, tick, failureCode),
                    MachineRunResultCode.ACCEPTED,
                    "Prepared Machine Run child cancelled before Execution admission"
            );
        } catch (IllegalStateException exception) {
            return rejected(MachineRunResultCode.CHILD_NOT_PREPARED, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation observeChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationSnapshot operation,
            long tick
    ) {
        MachineRunRecord run = find(runIdentity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        Optional<MachineRunChildRecord> existing = run.childForOperation(operation.operationId());
        if (existing.filter(child -> child.state().terminal()).isPresent()) {
            return observed(run, "Existing terminal Machine Run child result observed");
        }
        try {
            return replace(run.observeChild(operation, tick), MachineRunResultCode.ACCEPTED,
                    "Machine Run child terminal result observed");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineRunResultCode.CHILD_RESULT_CONFLICT, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation suspendForRestart(MachineRunIdentity identity, long tick) {
        MachineRunRecord run = find(identity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        MachineRunRecord suspended = run.suspendForRestart(tick);
        return suspended.equals(run)
                ? observed(run, "Machine Run already has restart-safe lifecycle")
                : replace(suspended, MachineRunResultCode.ACCEPTED, "Machine Run suspended for explicit restart decision");
    }

    public MachineRunRegistryMutation resume(MachineRunIdentity identity, long expectedRevision, long tick) {
        MachineRunRecord run = find(identity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        if (run.revision() != expectedRevision) {
            return rejected(MachineRunResultCode.STALE_REVISION, "Machine Run resume revision is stale");
        }
        try {
            MachineRunRecord resumed = run.resume(tick);
            return resumed.equals(run) ? observed(run, "Machine Run already active")
                    : replace(resumed, MachineRunResultCode.ACCEPTED, "Machine Run resumed");
        } catch (IllegalStateException exception) {
            return rejected(MachineRunResultCode.INVALID_LIFECYCLE, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation completeStop(MachineRunIdentity identity, long tick) {
        MachineRunRecord run = find(identity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        if (run.lifecycle() == MachineRunLifecycle.STOPPED) return observed(run, "Machine Run already stopped");
        try {
            return replace(run.stopped(tick), MachineRunResultCode.ACCEPTED, "Machine Run stopped");
        } catch (IllegalStateException exception) {
            return rejected(MachineRunResultCode.INVALID_LIFECYCLE, exception.getMessage());
        }
    }

    public MachineRunRegistryMutation recoveryRequired(
            MachineRunIdentity identity,
            MachineRunResultCode code,
            String detail,
            long tick
    ) {
        MachineRunRecord run = find(identity).orElse(null);
        if (run == null) return rejected(MachineRunResultCode.UNKNOWN_RUN, "Unknown Machine Run");
        MachineRunRecord updated = run.recoveryRequired(code, detail, tick);
        return updated.equals(run) ? observed(run, detail)
                : replace(updated, MachineRunResultCode.ACCEPTED, detail);
    }

    private Optional<MachineRunGenerationAllocator> allocatorFor(String instanceIdentity) {
        return generationAllocators.stream()
                .filter(allocator -> allocator.workstationInstanceIdentity().equals(instanceIdentity))
                .findFirst();
    }

    private List<MachineRunGenerationAllocator> advanceAllocator(String instanceIdentity, long nextGeneration) {
        List<MachineRunGenerationAllocator> candidates = new ArrayList<>();
        boolean replaced = false;
        for (MachineRunGenerationAllocator allocator : generationAllocators) {
            if (allocator.workstationInstanceIdentity().equals(instanceIdentity)) {
                candidates.add(new MachineRunGenerationAllocator(instanceIdentity, nextGeneration));
                replaced = true;
            } else {
                candidates.add(allocator);
            }
        }
        if (!replaced) candidates.add(new MachineRunGenerationAllocator(instanceIdentity, nextGeneration));
        return candidates;
    }

    private MachineRunRegistryMutation replace(
            MachineRunRecord updated,
            MachineRunResultCode code,
            String detail
    ) {
        List<MachineRunRecord> candidates = new ArrayList<>(runs.size());
        boolean replaced = false;
        for (MachineRunRecord run : runs) {
            if (run.runIdentity().equals(updated.runIdentity())) {
                candidates.add(updated);
                replaced = true;
            } else {
                candidates.add(run);
            }
        }
        if (!replaced) throw new IllegalStateException("Machine Run replacement target is missing");
        MachineRunRegistry candidate = new MachineRunRegistry(
                schemaVersion,
                Math.addExact(ownerRevision, 1L),
                worldIdentity,
                configurationIdentity,
                generationAllocators,
                candidates
        );
        return new MachineRunRegistryMutation(code, candidate, Optional.of(updated), true, detail);
    }

    private MachineRunRegistryMutation accepted(MachineRunRegistry candidate, MachineRunRecord run, String detail) {
        return new MachineRunRegistryMutation(
                MachineRunResultCode.ACCEPTED,
                candidate,
                Optional.of(run),
                true,
                detail
        );
    }

    private MachineRunRegistryMutation observed(MachineRunRecord run, String detail) {
        return new MachineRunRegistryMutation(
                MachineRunResultCode.EXISTING_RUN,
                this,
                Optional.of(run),
                false,
                detail
        );
    }

    private MachineRunRegistryMutation rejected(MachineRunResultCode code, String detail) {
        return new MachineRunRegistryMutation(code, this, Optional.empty(), false, detail);
    }
}
