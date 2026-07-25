package com.butchercraft.world.execution;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAuthorizationBindingTest {
    @Test
    void operationIdentityBindsImmutableAuthorizationContent() {
        ExecutionAuthorizationEvidence first = ExecutionTestFixtures.evidence(
                "test:work/identity",
                "test:frozen/identity",
                0
        );
        ExecutionAuthorizationEvidence repeated = ExecutionTestFixtures.evidence(
                "test:work/identity",
                "test:frozen/identity",
                0
        );
        ExecutionAuthorizationEvidence changedInput = ExecutionTestFixtures.evidence(
                "test:work/identity",
                "test:frozen/changed",
                0
        );

        assertEquals(ExecutionOperationId.derive(first), ExecutionOperationId.derive(repeated));
        assertNotEquals(ExecutionOperationId.derive(first), ExecutionOperationId.derive(changedInput));
    }

    @Test
    void duplicateAuthorizationObservesExistingOperationAndConflictIsRejected() {
        ExecutionManager manager = new ExecutionManager(
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
        ExecutionAuthorization authorization = ExecutionTestFixtures.authorization(
                "test:work/duplicate",
                "test:frozen/duplicate",
                0
        );
        ExecutionOperationResult<ExecutionOperationSnapshot> first =
                manager.acceptAuthorization(authorization, 0);
        ExecutionOperationResult<ExecutionOperationSnapshot> duplicate =
                manager.acceptAuthorization(new ExecutionAuthorization(authorization.evidence()), 0);

        assertTrue(first.accepted());
        assertTrue(duplicate.accepted());
        assertEquals(first.value().orElseThrow().operationId(), duplicate.value().orElseThrow().operationId());

        ExecutionAuthorizationEvidence changed = ExecutionTestFixtures.evidence(
                "test:work/duplicate",
                "test:frozen/conflict",
                0
        );
        ExecutionAuthorizationEvidence conflictingSameIdentity = new ExecutionAuthorizationEvidence(
                ExecutionSchema.CURRENT_VERSION,
                authorization.evidence().authorizationIdentity(),
                changed.authorizationSourceOwner(),
                changed.executableWorkReferenceType(),
                changed.executableWorkReferenceId(),
                changed.operationType(),
                changed.handlerId(),
                changed.frozenInputIdentity(),
                changed.sourceFreshnessIdentity(),
                changed.configurationIdentity(),
                changed.worldIdentity(),
                changed.issuedSimulationTick(),
                changed.validUntilSimulationTick(),
                changed.explicitInputIdentities(),
                changed.authorizationContentDigest()
        );

        ExecutionOperationResult<ExecutionOperationSnapshot> conflict =
                manager.acceptAuthorization(new ExecutionAuthorization(conflictingSameIdentity), 0);

        assertFalse(conflict.accepted());
        assertEquals(ExecutionFailureCode.AUTHORIZATION_IDENTITY_CONFLICT,
                conflict.failureCode().orElseThrow());
    }

    @Test
    void immutableEvidenceAloneDoesNotExposeLiveMutationAuthority() throws NoSuchMethodException {
        assertFalse(Modifier.isPublic(ExecutionAuthorization.class
                .getDeclaredConstructor(ExecutionAuthorizationEvidence.class)
                .getModifiers()));
        assertTrue(java.util.Arrays.stream(ExecutionManager.class.getMethods()).noneMatch(method ->
                method.getName().equals("acceptAuthorization")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == ExecutionAuthorizationEvidence.class));
    }
}
