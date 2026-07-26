package com.butchercraft.machine.grinder.execution;

import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.world.execution.ExecutionAuthorization;
import com.butchercraft.world.execution.ExecutionAuthorizationEvidence;
import com.butchercraft.world.execution.ExecutionFailureCode;
import com.butchercraft.world.execution.ExecutionHandlerRegistry;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionRuntimeConfiguration;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderExecutionAuthorizationTest {
    private final GrinderExecutionOperationHandler handler =
            new GrinderExecutionOperationHandler(() -> {
                throw new AssertionError("Handler execution is not part of authorization tests");
            });

    @Test
    void handlerContractIsIdempotentAndOwnerResultRequired() {
        assertEquals(HandlerEffectType.IDEMPOTENT, handler.contract().schedulerEffectType());
        assertTrue(handler.contract().ownerResultRequired());
        assertEquals(GrinderExecutionConstants.OPERATION_TYPE, handler.contract().operationType());
        assertEquals(GrinderExecutionConstants.HANDLER_ID, handler.contract().handlerId());
    }

    @Test
    void promotedGrinderOperationsAreExactlyTheSixRecipeCatalogOperations() {
        assertEquals(Set.of(
                BuiltInDefinitionIds.GRIND_BEEF,
                BuiltInDefinitionIds.GRIND_PORK,
                BuiltInDefinitionIds.GRIND_CHICKEN,
                BuiltInDefinitionIds.GRIND_BISON,
                BuiltInDefinitionIds.GRIND_LAMB,
                BuiltInDefinitionIds.GRIND_VENISON
        ), GrinderExecutionConstants.PROMOTED_GRINDER_OPERATIONS);
    }

    @Test
    void grinderProcessIdentityDiffersBetweenAllPromotedProcesses() {
        WorkstationOperationResolver resolver = new WorkstationOperationResolver();
        List<String> identities = List.of(
                ModItems.BEEF_TRIM,
                ModItems.PORK_TRIM,
                ModItems.CHICKEN_TRIM,
                ModItems.BUFFALO_TRIM,
                ModItems.LAMB_TRIM,
                ModItems.VENISON_TRIM
        ).stream()
                .map(item -> resolver.resolve(
                        BuiltInProcessingDefinitions.builtInView(),
                        GrinderWorkstation.capability(),
                        item.get().getDefaultInstance()
                ).operation().orElseThrow())
                .map(GrinderExecutionIdentities::operationIdentity)
                .toList();

        assertEquals(6, Set.copyOf(identities).size());
    }

    @Test
    void validAuthorizationBindsWorkstationAndFrozenInput() {
        ExecutionAuthorizationEvidence evidence = evidence("butchercraft:workstation_input/v1/aaa", 12);

        assertTrue(handler.validateAuthorization(evidence).accepted());
    }

    @Test
    void invalidWorkstationReferenceIsRejected() {
        ExecutionAuthorizationEvidence evidence = evidence(
                "butchercraft:not_a_grinder",
                "butchercraft:workstation_input/v1/aaa",
                "butchercraft:workstation_output/v1/bbb",
                12
        );

        var rejected = handler.validateAuthorization(evidence);

        assertFalse(rejected.accepted());
        assertEquals(ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION, rejected.failureCode().orElseThrow());
    }

    @Test
    void repeatedAuthorizationContentObservesExistingOperation() {
        ExecutionManager manager = new ExecutionManager(
                new ExecutionHandlerRegistry(List.of(handler)),
                ExecutionRuntimeConfiguration.standard()
        );
        ExecutionAuthorizationEvidence evidence = evidence("butchercraft:workstation_input/v1/aaa", 12);

        ExecutionOperationSnapshot first = manager.acceptAuthorization(ExecutionAuthorization.issue(evidence), 12)
                .value().orElseThrow();
        ExecutionOperationSnapshot duplicate = manager.acceptAuthorization(ExecutionAuthorization.issue(evidence), 12)
                .value().orElseThrow();

        assertEquals(first.operationId(), duplicate.operationId());
        assertEquals(ExecutionStatus.AUTHORIZED, duplicate.status());
    }

    @Test
    void changedFrozenInputProducesDifferentOperationIdentity() {
        ExecutionManager manager = new ExecutionManager(
                new ExecutionHandlerRegistry(List.of(handler)),
                ExecutionRuntimeConfiguration.standard()
        );

        ExecutionOperationSnapshot first = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence("butchercraft:workstation_input/v1/aaa", 12)),
                12
        ).value().orElseThrow();
        ExecutionOperationSnapshot changed = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence("butchercraft:workstation_input/v1/ccc", 12)),
                12
        ).value().orElseThrow();

        assertNotEquals(first.operationId(), changed.operationId());
    }

    @Test
    void changedExpectedOutputProducesDifferentOperationIdentity() {
        ExecutionManager manager = new ExecutionManager(
                new ExecutionHandlerRegistry(List.of(handler)),
                ExecutionRuntimeConfiguration.standard()
        );

        ExecutionOperationSnapshot first = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence(
                        workstationIdentity(),
                        "butchercraft:workstation_input/v1/aaa",
                        "butchercraft:workstation_output/v1/beef",
                        12
                )),
                12
        ).value().orElseThrow();
        ExecutionOperationSnapshot changed = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence(
                        workstationIdentity(),
                        "butchercraft:workstation_input/v1/aaa",
                        "butchercraft:workstation_output/v1/pork",
                        12
                )),
                12
        ).value().orElseThrow();

        assertNotEquals(first.operationId(), changed.operationId());
    }

    @Test
    void changedSelectedProcessFreshnessProducesDifferentOperationIdentity() {
        ExecutionManager manager = new ExecutionManager(
                new ExecutionHandlerRegistry(List.of(handler)),
                ExecutionRuntimeConfiguration.standard()
        );

        ExecutionOperationSnapshot first = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence(
                        workstationIdentity(),
                        "butchercraft:workstation_input/v1/aaa",
                        "butchercraft:workstation_output/v1/bbb",
                        "butchercraft:workstation_freshness/v1/grind_beef",
                        12
                )),
                12
        ).value().orElseThrow();
        ExecutionOperationSnapshot changed = manager.acceptAuthorization(
                ExecutionAuthorization.issue(evidence(
                        workstationIdentity(),
                        "butchercraft:workstation_input/v1/aaa",
                        "butchercraft:workstation_output/v1/bbb",
                        "butchercraft:workstation_freshness/v1/grind_pork",
                        12
                )),
                12
        ).value().orElseThrow();

        assertNotEquals(first.operationId(), changed.operationId());
    }

    @Test
    void workstationIdentityRoundTripsDimensionAndPosition() {
        GrinderWorkstationReference reference = new GrinderWorkstationReference(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(-3, 64, 17)
        );

        GrinderWorkstationReference parsed = GrinderWorkstationReference.parse(reference.identity()).orElseThrow();

        assertEquals(reference, parsed);
    }

    private static ExecutionAuthorizationEvidence evidence(String frozenInputIdentity, long tick) {
        return evidence(
                workstationIdentity(),
                frozenInputIdentity,
                "butchercraft:workstation_output/v1/bbb",
                tick
        );
    }

    private static ExecutionAuthorizationEvidence evidence(
            String workstationIdentity,
            String frozenInputIdentity,
            String expectedOutputIdentity,
            long tick
    ) {
        return evidence(
                workstationIdentity,
                frozenInputIdentity,
                expectedOutputIdentity,
                "butchercraft:workstation_freshness/v1/fresh",
                tick
        );
    }

    private static ExecutionAuthorizationEvidence evidence(
            String workstationIdentity,
            String frozenInputIdentity,
            String expectedOutputIdentity,
            String sourceFreshnessIdentity,
            long tick
    ) {
        return ExecutionAuthorizationEvidence.issued(
                GrinderExecutionConstants.OWNER_SUBSYSTEM_ID,
                GrinderExecutionConstants.EXECUTABLE_REFERENCE_TYPE,
                workstationIdentity,
                GrinderExecutionConstants.OPERATION_TYPE,
                GrinderExecutionConstants.HANDLER_ID,
                frozenInputIdentity,
                sourceFreshnessIdentity,
                GrinderExecutionConstants.CONFIGURATION_IDENTITY,
                "butchercraft:world/world_test",
                tick,
                OptionalLong.of(tick + 120),
                List.of(workstationIdentity, frozenInputIdentity, expectedOutputIdentity)
        );
    }

    private static String workstationIdentity() {
        return new GrinderWorkstationReference(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO
        ).identity();
    }
}
