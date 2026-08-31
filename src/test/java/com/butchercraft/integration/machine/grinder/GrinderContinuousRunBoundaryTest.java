package com.butchercraft.integration.machine.grinder;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.operation.MachineOperatingPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderContinuousRunBoundaryTest {
    @Test
    void activatesOnlyRatifiedContinuousExplicitStopPolicy() {
        assertEquals("POWERED_CONTINUOUS_EXPLICIT_STOP",
                MachineOperatingPolicy.poweredContinuousExplicitStop().kind().name());
    }

    @Test
    void grinderIntegrationDelegatesRunAuthorityToGenericCoordinator() throws IOException {
        String facade = source("src/main/java/com/butchercraft/integration/machine/grinder/GrinderContinuousRunService.java");
        String shared = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");

        assertTrue(facade.contains("PoweredProcessingMachineRunService<GrinderBlockEntity>"));
        assertTrue(shared.contains("MachineRunCoordinatorService.INSTANCE"));
        assertTrue(shared.contains("coordinator.start("));
        assertTrue(shared.contains("coordinator.stop("));
        assertTrue(shared.contains("coordinator.resume("));
        assertTrue(shared.contains("coordinator.admitChild("));
    }

    @Test
    void childAuthorizationBindsExactRunInstanceSequenceAndPolicy() throws IOException {
        String source = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");

        assertTrue(source.contains("run.runIdentity().value()"));
        assertTrue(source.contains("run.workstationInstanceIdentity()"));
        assertTrue(source.contains("MachineRunRecord.childSequenceIdentity(run.nextChildSequence())"));
        assertTrue(source.contains("run.operatingPolicyIdentity()"));
    }

    @Test
    void eligibilityCreatesNoInventoryLoopOrSchedulerAuthority() throws IOException {
        String source = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");

        assertFalse(source.contains("while ("));
        assertFalse(source.contains("SimulationSchedulerService"));
        assertFalse(source.contains("setInputInternal"));
        assertFalse(source.contains("setOutputInternal"));
        assertFalse(source.contains("MaterialHandlingService"));
    }

    @Test
    void playerOneCycleBypassIsRemovedButEmployeeBoundedPathRemains() throws IOException {
        String grinder = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java");
        String employee = source("src/main/java/com/butchercraft/integration/employee/EmployeeWorkstationOperationService.java");

        assertFalse(grinder.contains("requestPlayerProcessing"));
        assertTrue(grinder.contains("requestEmployeeProcessing"));
        assertTrue(employee.contains("requestEmployeeProcessing"));
        assertTrue(employee.contains("active player Machine Run"));
    }

    @Test
    void onlyGrinderAndPattyFormerUseSharedContinuousService() throws IOException {
        String shared = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");
        String patty = source("src/main/java/com/butchercraft/integration/machine/pattyformer/PattyFormerContinuousRunService.java");
        String cutting = source("src/main/java/com/butchercraft/machine/cuttingtable/CuttingTableBlockEntity.java");

        assertFalse(shared.contains("GrinderBlockEntity"));
        assertFalse(shared.contains("PattyFormerBlockEntity"));
        assertFalse(shared.contains("CuttingTable"));
        assertTrue(patty.contains("PoweredProcessingMachineRunService<PattyFormerBlockEntity>"));
        assertFalse(cutting.contains("MachineRunCoordinatorService"));
        assertFalse(cutting.contains("PoweredProcessingMachineRunService"));
    }

    @Test
    void menuUsesBuiltInContainerButtonSynchronization() throws IOException {
        String menu = source("src/main/java/com/butchercraft/machine/grinder/GrinderMenu.java");
        String screen = source("src/main/java/com/butchercraft/client/screen/AbstractMachineRunScreen.java");

        assertTrue(menu.contains("public boolean clickMenuButton(Player player, int id)"));
        assertTrue(screen.contains("handleInventoryButtonClick(menu.containerId, id)"));
        assertFalse(menu.contains("CustomPacketPayload"));
    }

    @Test
    void materialPresenceCannotCreateRunWithoutExplicitControl() throws IOException {
        String entity = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java");
        String service = source("src/main/java/com/butchercraft/integration/machine/grinder/GrinderContinuousRunService.java");

        assertTrue(entity.contains("hasActiveRun(serverLevel, blockEntity)"));
        assertTrue(service.contains("public GrinderRunControlResult start("));
        assertFalse(entity.contains("onInventoryChanged()") && entity.contains("startRun()"));
    }

    @Test
    void unchangedEmptyOrBlockedObservationDoesNotRepublishEveryTick() throws IOException {
        String source = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");

        assertTrue(source.contains("record.state() == state"));
        assertTrue(source.contains("record.eligibilityIdentity().equals(eligibility)"));
        assertTrue(source.contains("record.blockageReason().equals(blockage)"));
        assertTrue(source.contains("machine.workstationState() == WorkstationState.BLOCKED"));
        assertTrue(source.indexOf("record.blockageReason().equals(blockage)")
                < source.indexOf("operatingService.publishOperationalState("));
    }

    @Test
    void machineOwnerPublicationPersistsClockBeforeLaterTickEvidence() throws IOException {
        String runs = source("src/main/java/com/butchercraft/world/ExecutionMachineRunService.java");
        String operating = source("src/main/java/com/butchercraft/world/MachineOperatingStateService.java");

        assertClockBeforeChangedOwnerSave(runs);
        assertClockBeforeChangedOwnerSave(operating);
    }

    @Test
    void recoveredMachineEvidenceAheadOfClockFailsVisible() throws IOException {
        String source = source("src/main/java/com/butchercraft/integration/machine/MachineRunCoordinatorService.java");

        assertTrue(source.contains("is newer than the recovered Simulation Clock"));
        assertTrue(source.contains("MachineOperatingResultCode.RECOVERY_REQUIRED"));
        assertTrue(source.contains("MachineRunResultCode.RECOVERY_REQUIRED"));
    }

    @Test
    void authorityBlockedWorldKeepsContinuousMachineLifecycleReadOnly() throws IOException {
        String source = source("src/main/java/com/butchercraft/integration/machine/PoweredProcessingMachineRunService.java");

        assertTrue(source.contains("if (!runtimeMutationPermitted(level)) return;"));
        assertTrue(source.contains("LegacySplitRecoveryParticipants.EXECUTION"));
        assertTrue(source.contains("LegacySplitRecoveryParticipants.WORKSTATION"));
        int endpointLoaded = source.indexOf("public void endpointLoaded");
        int mutationGate = source.indexOf("if (!runtimeMutationPermitted(level)) return;", endpointLoaded);
        int endpointReference = source.indexOf(
                "endpointService.referenceFor(level, machine.getBlockPos())", endpointLoaded);
        assertTrue(endpointLoaded >= 0 && mutationGate > endpointLoaded && endpointReference > mutationGate);
    }

    @Test
    void manifestKeepsFutureMachineScopeGated() throws IOException {
        String manifest = source("src/main/java/com/butchercraft/architecture/ButcherCraftArchitectureManifest.java");

        assertTrue(manifest.contains("grinder_continuous_run_activation"));
        assertTrue(manifest.contains("patty_former_continuous_run_activation"));
        assertTrue(manifest.contains("powered_processing_shared_run_coordination"));
        assertTrue(manifest.contains("machine_run_remaining_activation_gates"));
        assertTrue(manifest.contains("machine wear remain unactivated"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }

    private static void assertClockBeforeChangedOwnerSave(String source) {
        int changedMutation = source.lastIndexOf("if (mutation.changed())");
        int clockSave = source.indexOf("clockPersistence.accept(server)", changedMutation);
        int ownerSave = source.indexOf("active.storage().save(", changedMutation);
        assertTrue(changedMutation >= 0 && clockSave > changedMutation && ownerSave > clockSave);
    }
}
