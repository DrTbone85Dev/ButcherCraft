package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.operation.MachineOperatingConfiguration;
import com.butchercraft.workstation.operation.MachineOperatingMutation;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.butchercraft.workstation.operation.MachineWorkstationReference;
import com.butchercraft.world.execution.MachineRunConfiguration;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationProjectionPolicyBRepairTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world/root/policy_b_test", 1, "sha256:" + "a".repeat(64));
    private static final String INSTANCE_CONFIGURATION =
            "butchercraft:workstation_instance_configuration/v1/policy_b_test";

    @Test
    void exactPolicyBSuccessorIsProvenButUnboundOrBroaderDivergenceIsNot() {
        WorkstationInstanceRecord pending = WorkstationInstanceRecord.pending(
                WORLD,
                new WorkstationEndpointKey("butchercraft:patty_former", "minecraft:overworld", 1, 64, 2),
                1L,
                INSTANCE_CONFIGURATION,
                1L);
        WorkstationInstanceRecord instance = pending.transition(
                WorkstationInstanceLifecycle.ACTIVE, 2L, Optional.empty(), List.of());
        MachineWorkstationReference workstation = new MachineWorkstationReference(
                instance.instanceId(), instance.endpointKey(), instance.generation(), INSTANCE_CONFIGURATION);
        MachineOperatingConfiguration operatingConfiguration = MachineOperatingConfiguration.standard();
        MachineRunConfiguration runConfiguration = MachineRunConfiguration.standard();
        MachineOperatingRegistry registry = MachineOperatingRegistry.empty(
                WORLD.identity(), operatingConfiguration);
        MachineOperatingMutation prepared = registry.prepareStart(
                workstation,
                MachineOperatingPolicy.poweredContinuousExplicitStop(),
                0L,
                "test:machine_control",
                "test:start/policy_b",
                runConfiguration.configurationIdentity(),
                10L,
                operatingConfiguration);
        var run = MachineRunRegistry.empty(WORLD.identity(), runConfiguration)
                .acceptStart(prepared.startEvidence().orElseThrow(), runConfiguration, 10L)
                .run().orElseThrow();
        MachineOperatingRecord running = prepared.registry().activateStart(
                instance.instanceId().value(),
                run.runIdentity(),
                prepared.startEvidence().orElseThrow().authorizationIdentity(),
                10L).record().orElseThrow();
        MachineOperatingRecord restartRequired = prepared.registry().activateStart(
                instance.instanceId().value(),
                run.runIdentity(),
                prepared.startEvidence().orElseThrow().authorizationIdentity(),
                10L).registry().suspendForRestart(run.runIdentity(), 20L).record().orElseThrow();

        DurableWorkstationProjection durable = projection(instance, 0L, reference(running));
        DurableWorkstationProjection policyB = projection(instance, 0L, reference(restartRequired));
        DurableWorkstationProjection broaderDivergence = projection(instance, 1L, reference(restartRequired));

        assertEquals("RUNNING", durable.operatingStateReference().orElseThrow().state());
        assertEquals("RESTART_REQUIRED", policyB.operatingStateReference().orElseThrow().state());
        assertTrue(DurableWorkstationProjectionService.provenPolicyBOperatingSuccessor(
                durable, policyB, restartRequired, List.of(run.runIdentity().value())));
        assertFalse(DurableWorkstationProjectionService.provenPolicyBOperatingSuccessor(
                durable, policyB, restartRequired, List.of()));
        assertFalse(DurableWorkstationProjectionService.provenPolicyBOperatingSuccessor(
                durable, broaderDivergence, restartRequired, List.of(run.runIdentity().value())));
    }

    @Test
    void monotonicRegistryRevisionForTheSameActiveInstanceIsProvenButBroaderOrUnboundChangesAreNot() {
        WorkstationInstanceRecord pending = WorkstationInstanceRecord.pending(
                WORLD,
                new WorkstationEndpointKey("butchercraft:grinder", "minecraft:overworld", 2, 64, 2),
                1L,
                INSTANCE_CONFIGURATION,
                1L);
        WorkstationInstanceRecord instance = pending.transition(
                WorkstationInstanceLifecycle.ACTIVE, 2L, Optional.empty(), List.of());
        WorkstationInstanceRegistry advanced = new WorkstationInstanceRegistry(
                1,
                3L,
                WORLD,
                2L,
                INSTANCE_CONFIGURATION,
                List.of(instance));
        WorkstationInstanceRegistry stale = new WorkstationInstanceRegistry(
                1,
                2L,
                WORLD,
                2L,
                INSTANCE_CONFIGURATION,
                List.of(instance));
        WorkstationInstanceRecord retired = instance.transition(
                WorkstationInstanceLifecycle.RETIRED, 3L, Optional.of("test retirement"), List.of());
        WorkstationInstanceRegistry retiredRegistry = new WorkstationInstanceRegistry(
                1,
                3L,
                WORLD,
                2L,
                INSTANCE_CONFIGURATION,
                List.of(retired));

        DurableWorkstationProjection durable = projection(instance, 2L, 0L, Optional.empty());
        DurableWorkstationProjection registrySuccessor = projection(instance, 3L, 0L, Optional.empty());
        DurableWorkstationProjection broaderDivergence = projection(instance, 3L, 1L, Optional.empty());

        assertTrue(DurableWorkstationProjectionService.provenMonotonicInstanceRegistryRevision(
                durable, registrySuccessor, advanced));
        assertFalse(DurableWorkstationProjectionService.provenMonotonicInstanceRegistryRevision(
                durable, registrySuccessor, stale));
        assertFalse(DurableWorkstationProjectionService.provenMonotonicInstanceRegistryRevision(
                durable, registrySuccessor, retiredRegistry));
        assertFalse(DurableWorkstationProjectionService.provenMonotonicInstanceRegistryRevision(
                durable, broaderDivergence, advanced));
    }

    private static WorkstationOperatingStateReference reference(MachineOperatingRecord record) {
        return new WorkstationOperatingStateReference(
                record.workstation().instanceId().value(),
                record.revision(),
                record.state().name(),
                record.contentDigest());
    }

    private static DurableWorkstationProjection projection(
            WorkstationInstanceRecord instance,
            long inventoryRevision,
            WorkstationOperatingStateReference operating
    ) {
        return projection(instance, 2L, inventoryRevision, Optional.of(operating));
    }

    private static DurableWorkstationProjection projection(
            WorkstationInstanceRecord instance,
            long instanceRegistryRevision,
            long inventoryRevision,
            Optional<WorkstationOperatingStateReference> operating
    ) {
        return DurableWorkstationProjection.active(
                WORLD,
                instance.instanceId(),
                instance.endpointKey(),
                instance.generation(),
                INSTANCE_CONFIGURATION,
                "butchercraft:patty_former",
                instanceRegistryRevision,
                1L,
                inventoryRevision,
                0L,
                0L,
                "butchercraft:workstation_slot_capacity/v1/policy_b_test",
                List.of(),
                WorkstationProjectionNbtCodec.encode(new CompoundTag()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                operating);
    }
}
