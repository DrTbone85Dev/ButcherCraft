package com.butchercraft.workstation.operation;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.world.execution.MachineRunConfiguration;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachineOperatingRegistryTest {
    private static final String WORLD = "test:world/identity";
    private static final MachineOperatingConfiguration CONFIGURATION = MachineOperatingConfiguration.standard();
    private static final MachineRunConfiguration RUN_CONFIGURATION = MachineRunConfiguration.standard();

    @Test
    void activeStateObservationsRetainRunAndSimulationDuration() {
        Started started = started();
        MachineOperatingMutation empty = started.registry().publishOperationalState(
                started.runIdentity(),
                MachineOperatingState.RUNNING_EMPTY,
                Optional.of("test:eligibility/empty"),
                Optional.empty(),
                20L
        );
        MachineOperatingRecord emptyRecord = empty.record().orElseThrow();
        MachineOperatingMutation blocked = empty.registry().publishOperationalState(
                started.runIdentity(),
                MachineOperatingState.OUTPUT_BLOCKED,
                Optional.of("test:eligibility/output"),
                Optional.of("complete output cannot fit"),
                25L
        );

        assertEquals(started.runIdentity(), emptyRecord.currentRunIdentity().orElseThrow());
        assertEquals(5L, emptyRecord.durationAt(MachineOperatingState.RUNNING_EMPTY, 25L));
        assertEquals(MachineOperatingState.OUTPUT_BLOCKED, blocked.record().orElseThrow().state());
        assertEquals("complete output cannot fit", blocked.record().orElseThrow().blockageReason().orElseThrow());
    }

    @Test
    void unavailableEndpointBlocksChildAndResumeUntilExactInstanceReturns() {
        Started started = started();
        MachineOperatingMutation unavailable = started.registry().availability(
                started.workstation().instanceId().value(),
                MachineEndpointAvailability.UNAVAILABLE,
                3L
        );
        MachineOperatingMutation child = unavailable.registry().bindChild(
                started.runIdentity(),
                new com.butchercraft.world.execution.ExecutionOperationId(
                        "butchercraft:execution_operation/v1/" + "d".repeat(64)
                ),
                4L
        );
        MachineOperatingMutation suspended = unavailable.registry().suspendForRestart(started.runIdentity(), 5L);
        MachineOperatingMutation resumeBlocked = suspended.registry().resume(
                started.runIdentity(),
                suspended.record().orElseThrow().revision(),
                6L
        );

        assertEquals(MachineOperatingResultCode.CHILD_IDENTITY_CONFLICT, child.code());
        assertEquals(MachineOperatingResultCode.INVALID_STATE, resumeBlocked.code());

        MachineOperatingMutation available = suspended.registry().availability(
                started.workstation().instanceId().value(),
                MachineEndpointAvailability.AVAILABLE,
                7L
        );
        MachineOperatingMutation resumed = available.registry().resume(
                started.runIdentity(),
                available.record().orElseThrow().revision(),
                8L
        );
        assertTrue(resumed.accepted());
        assertEquals(MachineOperatingState.RUNNING, resumed.record().orElseThrow().state());
    }

    @Test
    void incompleteStartReturnsOffWithoutFabricatingRun() {
        MachineOperatingRegistry registry = MachineOperatingRegistry.empty(WORLD, CONFIGURATION);
        MachineOperatingMutation prepared = registry.prepareStart(
                workstation(),
                MachineOperatingPolicy.poweredContinuousExplicitStop(),
                0L,
                "test:machine_control",
                "test:start/incomplete",
                RUN_CONFIGURATION.configurationIdentity(),
                1L,
                CONFIGURATION
        );
        MachineOperatingMutation abandoned = prepared.registry().abandonUncommittedStart(
                workstation().instanceId().value(),
                prepared.startEvidence().orElseThrow().authorizationIdentity(),
                2L
        );

        assertEquals(MachineOperatingState.OFF, abandoned.record().orElseThrow().state());
        assertTrue(abandoned.record().orElseThrow().currentRunIdentity().isEmpty());
        assertFalse(abandoned.record().orElseThrow().state().powered());
    }

    @Test
    void policyCannotChangeAcrossAnExistingInstanceRecord() {
        Started started = started();
        MachineOperatingMutation stopAuthorized = started.registry().authorizeStop(
                started.runIdentity(),
                0L,
                started.record().revision(),
                "test:machine_control",
                "test:stop/one",
                RUN_CONFIGURATION.configurationIdentity(),
                2L
        );
        MachineOperatingMutation stopping = started.registry().publishStop(
                stopAuthorized.stopEvidence().orElseThrow(),
                2L
        );
        MachineOperatingMutation off = stopping.registry().completeStop(started.runIdentity(), 3L);

        MachineOperatingMutation changedPolicy = off.registry().prepareStart(
                started.workstation(),
                MachineOperatingPolicy.poweredOneCycle(),
                off.record().orElseThrow().revision(),
                "test:machine_control",
                "test:start/changed_policy",
                RUN_CONFIGURATION.configurationIdentity(),
                4L,
                CONFIGURATION
        );

        assertEquals(MachineOperatingResultCode.START_IDENTITY_CONFLICT, changedPolicy.code());
    }

    @Test
    void operatingObservationsRejectBackwardSimulationTicks() {
        Started started = started();
        MachineOperatingRecord unavailable = started.registry().availability(
                started.workstation().instanceId().value(),
                MachineEndpointAvailability.UNAVAILABLE,
                10L
        ).record().orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> unavailable.availability(
                MachineEndpointAvailability.AVAILABLE,
                9L
        ));
    }

    private static Started started() {
        MachineWorkstationReference workstation = workstation();
        MachineOperatingPolicy policy = MachineOperatingPolicy.poweredContinuousExplicitStop();
        MachineOperatingRegistry operating = MachineOperatingRegistry.empty(WORLD, CONFIGURATION);
        MachineOperatingMutation prepared = operating.prepareStart(
                workstation,
                policy,
                0L,
                "test:machine_control",
                "test:start/one",
                RUN_CONFIGURATION.configurationIdentity(),
                1L,
                CONFIGURATION
        );
        var runs = MachineRunRegistry.empty(WORLD, RUN_CONFIGURATION)
                .acceptStart(prepared.startEvidence().orElseThrow(), RUN_CONFIGURATION, 1L);
        MachineRunIdentity runIdentity = runs.run().orElseThrow().runIdentity();
        MachineOperatingMutation active = prepared.registry().activateStart(
                workstation.instanceId().value(),
                runIdentity,
                prepared.startEvidence().orElseThrow().authorizationIdentity(),
                1L
        );
        return new Started(active.registry(), active.record().orElseThrow(), workstation, runIdentity);
    }

    private static MachineWorkstationReference workstation() {
        return new MachineWorkstationReference(
                new WorkstationInstanceId("butchercraft:workstation_instance/v1/" + "e".repeat(64)),
                new WorkstationEndpointKey("butchercraft:grinder", "minecraft:overworld", 1, 64, 2),
                1L,
                "butchercraft:workstation_instance_allocation/schema_1"
        );
    }

    private record Started(
            MachineOperatingRegistry registry,
            MachineOperatingRecord record,
            MachineWorkstationReference workstation,
            MachineRunIdentity runIdentity
    ) {
    }
}
