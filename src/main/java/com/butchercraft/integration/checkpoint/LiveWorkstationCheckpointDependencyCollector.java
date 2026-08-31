package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependency;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependencyCategory;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationEndpointRuntimeService;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Collects immutable owner references used by Checkpoint Recovery's Workstation dependency closure. */
public final class LiveWorkstationCheckpointDependencyCollector {
    private static final String WORKSTATION_PREFIX = "butchercraft:workstation_instance/v1/";

    private LiveWorkstationCheckpointDependencyCollector() {
    }

    public static List<WorkstationCheckpointDependency> collect(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<WorkstationCheckpointDependency> dependencies = new ArrayList<>();
        execution(server, dependencies);
        machineRuns(server, dependencies);
        materialHandling(server, dependencies);
        endpointJournal(server, dependencies);
        return dependencies.stream().sorted().distinct().toList();
    }

    private static void execution(
            MinecraftServer server,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        ExecutionService.INSTANCE.managerFor(server).operations().stream()
                .filter(operation -> !operation.status().terminal()
                        || operation.status() == ExecutionStatus.UNKNOWN_OUTCOME)
                .forEach(operation -> operation.authorizationEvidence().explicitInputIdentities().stream()
                        .filter(identity -> identity.startsWith(WORKSTATION_PREFIX))
                        .forEach(identity -> dependencies.add(new WorkstationCheckpointDependency(
                                new WorkstationInstanceId(identity),
                                WorkstationCheckpointDependencyCategory.EXECUTION_OPERATION,
                                operation.operationId().value()))));
    }

    private static void machineRuns(
            MinecraftServer server,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        ExecutionMachineRunService.INSTANCE.snapshot(server).runs().stream()
                .filter(run -> !run.lifecycle().terminal())
                .forEach(run -> dependencies.add(new WorkstationCheckpointDependency(
                        new WorkstationInstanceId(run.workstationInstanceIdentity()),
                        WorkstationCheckpointDependencyCategory.MACHINE_RUN,
                        run.runIdentity().value())));
    }

    private static void materialHandling(
            MinecraftServer server,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        var schema2 = MaterialHandlingService.INSTANCE.currentRuntimeV2(server);
        if (schema2.isPresent()) {
            schema2.orElseThrow().transfers().stream()
                    .filter(transfer -> recoveryRelevant(transfer.lifecycle()))
                    .forEach(transfer -> {
                        dependencies.add(new WorkstationCheckpointDependency(
                                transfer.source().instanceId(),
                                WorkstationCheckpointDependencyCategory.MATERIAL_HANDLING_SOURCE,
                                transfer.transferId().value()));
                        dependencies.add(new WorkstationCheckpointDependency(
                                transfer.destination().instanceId(),
                                WorkstationCheckpointDependencyCategory.MATERIAL_HANDLING_DESTINATION,
                                transfer.transferId().value()));
                    });
            return;
        }
        MaterialHandlingService.INSTANCE.currentRuntime().ifPresent(runtime -> runtime.transfers().stream()
                .filter(transfer -> recoveryRelevant(transfer.lifecycle()))
                .forEach(transfer -> {
                    dependencies.add(new WorkstationCheckpointDependency(
                            transfer.source().instanceId(),
                            WorkstationCheckpointDependencyCategory.MATERIAL_HANDLING_SOURCE,
                            transfer.transferId().value()));
                    dependencies.add(new WorkstationCheckpointDependency(
                            transfer.destination().instanceId(),
                            WorkstationCheckpointDependencyCategory.MATERIAL_HANDLING_DESTINATION,
                            transfer.transferId().value()));
                }));
    }

    private static boolean recoveryRelevant(MaterialTransferLifecycle lifecycle) {
        return !lifecycle.terminal()
                || lifecycle == MaterialTransferLifecycle.UNKNOWN_OUTCOME
                || lifecycle == MaterialTransferLifecycle.RECOVERY_REQUIRED;
    }

    private static void endpointJournal(
            MinecraftServer server,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        var schema2 = StackAwareWorkstationEndpointRuntimeService.INSTANCE.currentJournal(server);
        if (schema2.isPresent()) {
            schema2.orElseThrow().records().stream()
                    .filter(record -> unresolved(record.state()))
                    .forEach(record -> {
                        WorkstationInstanceId instanceId = record.preparation().observation().instanceId();
                        dependencies.add(new WorkstationCheckpointDependency(
                                instanceId,
                                WorkstationCheckpointDependencyCategory.ENDPOINT_EFFECT,
                                record.effectId().value()));
                        record.ownerResult().ifPresent(result -> dependencies.add(
                                new WorkstationCheckpointDependency(
                                        instanceId,
                                        WorkstationCheckpointDependencyCategory.ENDPOINT_OWNER_RESULT,
                                        result.evidenceIdentity())));
                    });
            return;
        }
        WorkstationEndpointService.INSTANCE.legacyJournalSnapshot(server).records().stream()
                .filter(record -> unresolved(record.state()))
                .forEach(record -> {
                    dependencies.add(new WorkstationCheckpointDependency(
                            record.instanceId(),
                            WorkstationCheckpointDependencyCategory.ENDPOINT_EFFECT,
                            record.effectId().value()));
                    record.ownerResult().ifPresent(result -> dependencies.add(
                            new WorkstationCheckpointDependency(
                                    record.instanceId(),
                                    WorkstationCheckpointDependencyCategory.ENDPOINT_OWNER_RESULT,
                                    result.evidenceIdentity())));
                });
    }

    private static boolean unresolved(WorkstationEndpointJournalState state) {
        return state != WorkstationEndpointJournalState.RECONCILED
                && state != WorkstationEndpointJournalState.REJECTED
                && state != WorkstationEndpointJournalState.FAILED;
    }
}
