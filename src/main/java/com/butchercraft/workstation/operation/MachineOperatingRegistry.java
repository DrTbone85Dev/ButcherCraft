package com.butchercraft.workstation.operation;

import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineStartAuthorizationEvidence;
import com.butchercraft.world.execution.MachineStopAuthorizationEvidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MachineOperatingRegistry(
        int schemaVersion,
        long ownerRevision,
        String worldIdentity,
        String configurationIdentity,
        List<MachineOperatingRecord> records
) {
    public MachineOperatingRegistry {
        if (schemaVersion != MachineOperatingSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported machine operating registry schema: " + schemaVersion);
        }
        if (ownerRevision < 0L) throw new IllegalArgumentException("Machine operating owner revision must not be negative");
        worldIdentity = MachineOperatingValidation.id(worldIdentity, "Machine operating World Identity");
        configurationIdentity = MachineOperatingValidation.id(
                configurationIdentity,
                "Machine operating registry configuration identity"
        );
        records = Objects.requireNonNull(records, "records").stream().sorted().toList();
        Map<String, MachineOperatingRecord> byInstance = new HashMap<>();
        Map<MachineRunIdentity, MachineOperatingRecord> byRun = new HashMap<>();
        for (MachineOperatingRecord record : records) {
            if (byInstance.put(record.workstation().instanceId().value(), record) != null) {
                throw new IllegalArgumentException("Duplicate machine operating record for Workstation instance");
            }
            record.currentRunIdentity().ifPresent(run -> {
                if (byRun.put(run, record) != null) {
                    throw new IllegalArgumentException("Machine Run referenced by more than one operating record");
                }
            });
        }
    }

    public static MachineOperatingRegistry empty(
            String worldIdentity,
            MachineOperatingConfiguration configuration
    ) {
        Objects.requireNonNull(configuration, "configuration");
        return new MachineOperatingRegistry(
                MachineOperatingSchema.CURRENT_VERSION,
                0L,
                worldIdentity,
                configuration.configurationIdentity(),
                List.of()
        );
    }

    public Optional<MachineOperatingRecord> find(String workstationInstanceIdentity) {
        return records.stream()
                .filter(record -> record.workstation().instanceId().value().equals(workstationInstanceIdentity))
                .findFirst();
    }

    public Optional<MachineOperatingRecord> forRun(MachineRunIdentity runIdentity) {
        return records.stream().filter(record -> record.currentRunIdentity().filter(runIdentity::equals).isPresent())
                .findFirst();
    }

    public MachineOperatingMutation prepareStart(
            MachineWorkstationReference workstation,
            MachineOperatingPolicy policy,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String machineRunConfigurationIdentity,
            long tick,
            MachineOperatingConfiguration configuration
    ) {
        Objects.requireNonNull(workstation, "workstation");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(configuration, "configuration");
        if (!configuration.configurationIdentity().equals(configurationIdentity)) {
            return rejected(MachineOperatingResultCode.START_IDENTITY_CONFLICT,
                    "Machine START configuration does not match the operating-state registry");
        }
        if (!policy.kind().supportsPersistentRun()) {
            return rejected(MachineOperatingResultCode.UNSUPPORTED_POLICY,
                    "Manual-discrete Workstation policy does not support Machine Runs");
        }
        MachineOperatingRecord current = find(workstation.instanceId().value()).orElse(null);
        long currentRevision = current == null ? 0L : current.revision();
        if (currentRevision != expectedOperatingRevision) {
            return rejected(MachineOperatingResultCode.STALE_REVISION,
                    "Machine START expected operating revision is stale");
        }
        if (current != null && !current.workstation().equals(workstation)) {
            return rejected(MachineOperatingResultCode.REPLACEMENT_INSTANCE,
                    "Machine START Workstation reference conflicts with persisted instance evidence");
        }
        if (current != null && !current.policy().equals(policy)) {
            return rejected(MachineOperatingResultCode.START_IDENTITY_CONFLICT,
                    "Machine START policy conflicts with persisted Workstation policy");
        }
        if (current != null && current.endpointAvailability() != MachineEndpointAvailability.AVAILABLE) {
            return rejected(MachineOperatingResultCode.ENDPOINT_UNAVAILABLE,
                    "Machine START requires an available exact Workstation instance");
        }
        MachineStartAuthorizationEvidence evidence = MachineStartAuthorizationEvidence.issued(
                worldIdentity,
                workstation.instanceId().value(),
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                policy.policyIdentity(),
                machineRunConfigurationIdentity,
                tick
        );
        if (current != null && current.startAuthorizationIdentity()
                .filter(evidence.authorizationIdentity()::equals).isPresent()) {
            return observed(current, Optional.of(evidence), Optional.empty(), "Existing Machine START observed");
        }
        if (current != null && current.state() != MachineOperatingState.OFF) {
            return rejected(MachineOperatingResultCode.INVALID_STATE,
                    "Machine START requires OFF operating state");
        }
        if (current == null && records.size() >= configuration.maximumRecords()) {
            return rejected(MachineOperatingResultCode.RECOVERY_REQUIRED,
                    "Machine operating record capacity is exhausted");
        }
        MachineOperatingRecord candidateRecord = current == null
                ? MachineOperatingRecord.starting(workstation, policy, evidence, tick)
                : current.prepareNextStart(evidence, tick);
        return replace(candidateRecord, Optional.of(evidence), Optional.empty(), "Machine START prepared");
    }

    public MachineOperatingMutation activateStart(
            String workstationInstanceIdentity,
            MachineRunIdentity runIdentity,
            String startAuthorizationIdentity,
            long tick
    ) {
        MachineOperatingRecord current = find(workstationInstanceIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.UNKNOWN_INSTANCE, "Unknown machine instance");
        try {
            MachineOperatingRecord candidate = current.activate(runIdentity, startAuthorizationIdentity, tick);
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.empty(), "Machine START already active")
                    : replace(candidate, Optional.empty(), Optional.empty(), "Machine START activated");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineOperatingMutation abandonUncommittedStart(
            String workstationInstanceIdentity,
            String startAuthorizationIdentity,
            long tick
    ) {
        MachineOperatingRecord current = find(workstationInstanceIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.UNKNOWN_INSTANCE, "Unknown machine instance");
        try {
            MachineOperatingRecord candidate = current.abandonUncommittedStart(startAuthorizationIdentity, tick);
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.empty(), "Uncommitted Machine START already OFF")
                    : replace(candidate, Optional.empty(), Optional.empty(), "Uncommitted Machine START returned OFF");
        } catch (IllegalStateException exception) {
            return rejected(MachineOperatingResultCode.START_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineOperatingMutation publishOperationalState(
            MachineRunIdentity runIdentity,
            MachineOperatingState next,
            Optional<String> eligibilityIdentity,
            Optional<String> blockageReason,
            long tick
    ) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine state observation targets an unknown Run");
        try {
            MachineOperatingRecord candidate = current.publishOperationalState(
                    next,
                    runIdentity,
                    eligibilityIdentity,
                    blockageReason,
                    tick
            );
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.empty(), "Machine operating observation unchanged")
                    : replace(candidate, Optional.empty(), Optional.empty(), "Machine operating observation published");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineOperatingResultCode.INVALID_STATE, exception.getMessage());
        }
    }

    public MachineOperatingMutation authorizeStop(
            MachineRunIdentity runIdentity,
            long expectedRunRevision,
            long expectedOperatingRevision,
            String sourceOwner,
            String sourceRequestIdentity,
            String machineRunConfigurationIdentity,
            long tick
    ) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine STOP targets a Run not owned by current operating state");
        if (current.revision() != expectedOperatingRevision) {
            return rejected(MachineOperatingResultCode.STALE_REVISION,
                    "Machine STOP expected operating revision is stale");
        }
        MachineStopAuthorizationEvidence evidence = MachineStopAuthorizationEvidence.issued(
                worldIdentity,
                current.workstation().instanceId().value(),
                runIdentity,
                expectedRunRevision,
                expectedOperatingRevision,
                sourceOwner,
                sourceRequestIdentity,
                machineRunConfigurationIdentity,
                tick
        );
        if (current.stopAuthorizationIdentity().filter(evidence.authorizationIdentity()::equals).isPresent()) {
            return observed(current, Optional.empty(), Optional.of(evidence), "Existing Machine STOP observed");
        }
        return new MachineOperatingMutation(
                MachineOperatingResultCode.ACCEPTED,
                this,
                Optional.of(current),
                Optional.empty(),
                Optional.of(evidence),
                false,
                "Machine STOP authorized for Execution acceptance"
        );
    }

    public MachineOperatingMutation publishStop(MachineStopAuthorizationEvidence evidence, long tick) {
        MachineOperatingRecord current = forRun(evidence.targetRunIdentity()).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine STOP targets an inactive operating record");
        try {
            MachineOperatingRecord candidate = current.requestStop(evidence, tick);
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.of(evidence), "Machine STOP already published")
                    : replace(candidate, Optional.empty(), Optional.of(evidence), "Machine STOP published");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineOperatingResultCode.STOP_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineOperatingMutation completeStop(MachineRunIdentity runIdentity, long tick) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine STOP completion targets an inactive Run");
        try {
            return replace(current.completeStop(runIdentity, tick), Optional.empty(), Optional.empty(),
                    "Machine operating state is OFF");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineOperatingResultCode.INVALID_STATE, exception.getMessage());
        }
    }

    public MachineOperatingMutation suspendForRestart(MachineRunIdentity runIdentity, long tick) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Restart suspension targets an unknown Machine Run");
        MachineOperatingRecord candidate = current.suspendForRestart(tick);
        return candidate.equals(current)
                ? observed(current, Optional.empty(), Optional.empty(), "Machine already restart-safe")
                : replace(candidate, Optional.empty(), Optional.empty(), "Machine requires explicit restart decision");
    }

    public MachineOperatingMutation resume(MachineRunIdentity runIdentity, long expectedRevision, long tick) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine resume targets an unknown Run");
        if (current.revision() != expectedRevision) {
            return rejected(MachineOperatingResultCode.STALE_REVISION, "Machine resume revision is stale");
        }
        try {
            return replace(current.resume(runIdentity, tick), Optional.empty(), Optional.empty(), "Machine resumed");
        } catch (IllegalStateException exception) {
            return rejected(MachineOperatingResultCode.INVALID_STATE, exception.getMessage());
        }
    }

    public MachineOperatingMutation bindChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine child targets an unknown Run");
        try {
            MachineOperatingRecord candidate = current.bindChild(runIdentity, operationId, tick);
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.empty(), "Machine child already bound")
                    : replace(candidate, Optional.empty(), Optional.empty(), "Machine child bound");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return rejected(MachineOperatingResultCode.CHILD_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineOperatingMutation clearChild(
            MachineRunIdentity runIdentity,
            ExecutionOperationId operationId,
            long tick
    ) {
        MachineOperatingRecord current = forRun(runIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.RUN_IDENTITY_CONFLICT,
                "Machine child result targets an unknown Run");
        try {
            MachineOperatingRecord candidate = current.clearChild(operationId, tick);
            return candidate.equals(current)
                    ? observed(current, Optional.empty(), Optional.empty(), "Machine child already clear")
                    : replace(candidate, Optional.empty(), Optional.empty(), "Machine child cleared");
        } catch (IllegalStateException exception) {
            return rejected(MachineOperatingResultCode.CHILD_IDENTITY_CONFLICT, exception.getMessage());
        }
    }

    public MachineOperatingMutation availability(
            String workstationInstanceIdentity,
            MachineEndpointAvailability availability,
            long tick
    ) {
        MachineOperatingRecord current = find(workstationInstanceIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.UNKNOWN_INSTANCE, "Unknown machine instance");
        MachineOperatingRecord candidate = current.availability(availability, tick);
        return candidate.equals(current)
                ? observed(current, Optional.empty(), Optional.empty(), "Machine availability unchanged")
                : replace(candidate, Optional.empty(), Optional.empty(), "Machine availability updated");
    }

    public MachineOperatingMutation recoveryRequired(
            String workstationInstanceIdentity,
            MachineOperatingResultCode code,
            String detail,
            MachineEndpointAvailability availability,
            long tick
    ) {
        MachineOperatingRecord current = find(workstationInstanceIdentity).orElse(null);
        if (current == null) return rejected(MachineOperatingResultCode.UNKNOWN_INSTANCE, "Unknown machine instance");
        MachineOperatingRecord candidate = current.recoveryRequired(code, detail, availability, tick);
        return candidate.equals(current)
                ? observed(current, Optional.empty(), Optional.empty(), detail)
                : replace(candidate, Optional.empty(), Optional.empty(), detail);
    }

    private MachineOperatingMutation replace(
            MachineOperatingRecord candidateRecord,
            Optional<MachineStartAuthorizationEvidence> startEvidence,
            Optional<MachineStopAuthorizationEvidence> stopEvidence,
            String detail
    ) {
        List<MachineOperatingRecord> candidates = new ArrayList<>();
        boolean replaced = false;
        for (MachineOperatingRecord record : records) {
            if (record.workstation().instanceId().equals(candidateRecord.workstation().instanceId())) {
                candidates.add(candidateRecord);
                replaced = true;
            } else {
                candidates.add(record);
            }
        }
        if (!replaced) candidates.add(candidateRecord);
        MachineOperatingRegistry candidate = new MachineOperatingRegistry(
                schemaVersion,
                Math.addExact(ownerRevision, 1L),
                worldIdentity,
                configurationIdentity,
                candidates
        );
        return new MachineOperatingMutation(
                MachineOperatingResultCode.ACCEPTED,
                candidate,
                Optional.of(candidateRecord),
                startEvidence,
                stopEvidence,
                true,
                detail
        );
    }

    private MachineOperatingMutation observed(
            MachineOperatingRecord record,
            Optional<MachineStartAuthorizationEvidence> startEvidence,
            Optional<MachineStopAuthorizationEvidence> stopEvidence,
            String detail
    ) {
        return new MachineOperatingMutation(
                MachineOperatingResultCode.EXISTING_STATE,
                this,
                Optional.of(record),
                startEvidence,
                stopEvidence,
                false,
                detail
        );
    }

    private MachineOperatingMutation rejected(MachineOperatingResultCode code, String detail) {
        return new MachineOperatingMutation(
                code,
                this,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                detail
        );
    }
}
