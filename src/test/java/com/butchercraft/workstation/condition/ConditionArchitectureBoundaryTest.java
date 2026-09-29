package com.butchercraft.workstation.condition;

import com.butchercraft.integration.machine.MachineConditionPolicies;
import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConditionArchitectureBoundaryTest {
    @Test
    void arithmeticCannotAcquireOtherOwnersOrHiddenTime() throws Exception {
        exclude("workstation/condition", List.of("import net.minecraft.", "import net.neoforged.",
                "ExecutionManager", "SimulationSchedulerService", "MachineRunRegistry", "InventoryManager",
                "MaterialHandlingService", "EmployeeRecords", "WorkstationInventory", "System.currentTimeMillis",
                "System.nanoTime", "java.time.", "Random", "getChunk(", "setChunkForced", "Files.move("));
    }

    @Test
    void otherRuntimeOwnersCannotMutateCondition() throws Exception {
        for (String root : List.of("world/execution", "world/simulation", "world/workforce",
                "world/materialhandling", "world/production", "world/inventory", "client")) {
            exclude(root, List.of("WorkstationConditionEngine", "ConditionReceiptStorage",
                    "ConditionProcessingCandidates", "changeConditionPolicy(", "acceptConditionProjection("));
        }
        exclude("world/checkpoint", List.of("WorkstationConditionEngine", "ConditionReceiptStorage",
                "ConditionProcessingCandidates", "acceptConditionProjection("));
    }

    @Test
    void productionPoliciesRemainInertAndCuttingTableIsNotApplicable() {
        var policies = MachineConditionPolicies.standard();
        for (String type : List.of("butchercraft:grinder", "butchercraft:patty_former")) {
            var policy = policies.forMachine(type).orElseThrow();
            assertTrue(policy.inert());
            assertEquals(0, policy.processingLoss());
            assertEquals(0, policy.processingServiceDebt());
            assertTrue(policy.exposures().isEmpty());
            assertTrue(policy.faultLoss().isEmpty());
        }
        assertTrue(policies.forMachine("butchercraft:cutting_table").isEmpty());
    }

    @Test
    void durableResultPrecedesReturnAndConditionClosurePrecedesOperatingSuccessor() throws Exception {
        String block = source("workstation/block/AbstractProcessingWorkstationBlockEntity.java");
        String commit = block.substring(block.indexOf("protected WorkstationExecutionEffectResult completeScheduledExecution("));
        assertTrue(commit.indexOf("endDurableProjectionMutation();") < commit.indexOf("return result;"));
        assertTrue(commit.contains("failJointPublication(before, conditionBefore)"));
        String operating = source("world/MachineOperatingStateService.java");
        int prepared = operating.indexOf("prepareConditionTransition(server,");
        assertTrue(prepared >= 0);
        assertTrue(operating.indexOf("storage().save(", prepared) > prepared);
        String owner = source("workstation/projection/DurableWorkstationProjectionService.java");
        String due = owner.substring(owner.indexOf("public synchronized void settleConditionDue("),
                owner.indexOf("private DurableWorkstationProjection settleCondition("));
        assertFalse(due.contains("instanceRegistrySnapshot("));
        assertFalse(due.contains("enumerate("));
        assertTrue(due.contains("conditionDue.due(tick, 64)"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/" + file));
    }

    private static void exclude(String root, List<String> forbidden) throws Exception {
        try (var files = Files.walk(TestProjectPaths.projectPath("src/main/java/com/butchercraft/" + root))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                for (String token : forbidden) assertFalse(text.contains(token), file + " contains forbidden " + token);
            }
        }
    }
}
