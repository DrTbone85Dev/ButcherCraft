package com.butchercraft.world.execution;

import com.butchercraft.world.execution.persistence.ExecutionStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRegistryCompatibilityTest {
    private static final ExecutionRuntimeConfiguration CONFIGURATION = ExecutionRuntimeConfiguration.standard();
    private static final String OLD_REGISTRY_IDENTITY =
            ExecutionLegacyRegistryProfiles.PRE_CUTTING_TABLE_SCHEMA_1_REGISTRY_IDENTITY;
    private static final String CURRENT_REGISTRY_IDENTITY =
            "butchercraft:execution_handler_registry/v1/"
                    + "d0337e2d8560f661a17f3e7520c65a24bd039578e4ae337209e99e81edcc92ee";

    private static final ExecutionHandlerContract GRINDER = ExecutionHandlerContract.idempotent(
            "butchercraft:execution_handler/grinder_player_operation",
            "butchercraft:workstation/grinder_operation",
            50,
            "butchercraft:execution_configuration/grinder_player_operation_v1"
    );
    private static final ExecutionHandlerContract PATTY_FORMER = ExecutionHandlerContract.idempotent(
            "butchercraft:execution_handler/patty_former_player_operation",
            "butchercraft:workstation/patty_former_operation",
            50,
            "butchercraft:execution_configuration/patty_former_player_operation_v1"
    );
    private static final ExecutionHandlerContract CUTTING_TABLE = ExecutionHandlerContract.idempotent(
            "butchercraft:execution_handler/cutting_table_player_operation",
            "butchercraft:workstation/cutting_table_operation",
            50,
            "butchercraft:execution_configuration/cutting_table_player_operation_v1"
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactRegistryIdentityRemainsIdentical() {
        ExecutionHandlerRegistry current = currentRegistry();

        ExecutionRegistryCompatibilityObservation observation = classify(
                current.registryIdentity(),
                current,
                List.of()
        );

        assertEquals(ExecutionRegistryCompatibilityClassification.IDENTICAL, observation.classification());
        assertEquals(ExecutionRetainedOperationValidation.VALID, observation.retainedOperationValidation());
        assertTrue(observation.permitsExecutionAuthority());
        assertFalse(observation.operatorRecoveryRequired());
    }

    @Test
    void exactReleasedProfileWithCuttingTableAdditionIsAdditiveCompatible() {
        ExecutionRegistryCompatibilityObservation observation = classify(
                OLD_REGISTRY_IDENTITY,
                currentRegistry(),
                List.of()
        );

        assertEquals(
                ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE,
                observation.classification()
        );
        assertEquals(List.of(CUTTING_TABLE.handlerId()), observation.addedHandlerIds());
        assertEquals(2, observation.historicalHandlerCount());
        assertEquals(3, observation.currentHandlerCount());
        assertTrue(observation.historicalProfileIdentity().isPresent());
    }

    @Test
    void emptySchemaOneDocumentUnderReleasedProfileLoads() {
        ExecutionStorage currentStorage = currentStorage("empty.json", currentRegistry());

        ExecutionManager loaded = currentStorage.deserialize(legacyJson(List.of()));

        assertTrue(loaded.operations().isEmpty());
        assertEquals(
                ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE,
                currentStorage.compatibilityObservation().orElseThrow().classification()
        );
    }

    @Test
    void unchangedGrinderAndPattyFormerOperationsLoadUnderAdditiveRegistry() {
        List<ExecutionOperationSnapshot> operations = List.of(
                operation(GRINDER, "grinder"),
                operation(PATTY_FORMER, "patty_former")
        );
        ExecutionStorage currentStorage = currentStorage("operations.json", currentRegistry());

        ExecutionManager loaded = currentStorage.deserialize(legacyJson(operations));

        assertEquals(2, loaded.operations().size());
        ExecutionRegistryCompatibilityObservation observation =
                currentStorage.compatibilityObservation().orElseThrow();
        assertEquals(ExecutionRetainedOperationValidation.VALID, observation.retainedOperationValidation());
        assertEquals(2, observation.retainedOperationCount());
    }

    @Test
    void unrelatedAdditionalHandlerDoesNotInvalidateHistoricalOperation() {
        ExecutionHandlerContract unrelated = ExecutionHandlerContract.idempotent(
                "test:execution_handler/unrelated",
                "test:operation/unrelated",
                5,
                "test:execution_configuration/unrelated_v1"
        );
        ExecutionHandlerRegistry expanded = registry(GRINDER, PATTY_FORMER, CUTTING_TABLE, unrelated);

        ExecutionRegistryCompatibilityObservation observation = classify(
                OLD_REGISTRY_IDENTITY,
                expanded,
                List.of(operation(GRINDER, "unrelated_addition"))
        );

        assertEquals(
                ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE,
                observation.classification()
        );
        assertEquals(List.of(CUTTING_TABLE.handlerId(), unrelated.handlerId()), observation.addedHandlerIds());
    }

    @Test
    void historicalHandlerRemovalIsIncompatibleEvenWithoutOperations() {
        ExecutionHandlerRegistry missingPattyFormer = registry(GRINDER, CUTTING_TABLE);

        ExecutionRegistryCompatibilityObservation observation = classify(
                OLD_REGISTRY_IDENTITY,
                missingPattyFormer,
                List.of()
        );

        assertEquals(ExecutionRegistryCompatibilityClassification.INCOMPATIBLE, observation.classification());
        assertEquals(List.of(PATTY_FORMER.handlerId()), observation.missingHandlerIds());
        assertTrue(observation.operatorRecoveryRequired());
    }

    @Test
    void historicalHandlerContractChangeIsIncompatible() {
        ExecutionHandlerContract changedGrinder = ExecutionHandlerContract.idempotent(
                GRINDER.handlerId(),
                GRINDER.operationType(),
                51,
                GRINDER.configurationIdentity()
        );
        ExecutionHandlerRegistry changed = registry(changedGrinder, PATTY_FORMER, CUTTING_TABLE);

        ExecutionRegistryCompatibilityObservation observation = classify(
                OLD_REGISTRY_IDENTITY,
                changed,
                List.of(operation(GRINDER, "changed_contract"))
        );

        assertEquals(ExecutionRegistryCompatibilityClassification.INCOMPATIBLE, observation.classification());
        assertEquals(List.of(GRINDER.handlerId()), observation.incompatibleContractHandlerIds());
        assertTrue(observation.failures().stream().anyMatch(failure ->
                failure.code() == ExecutionRegistryCompatibilityFailureCode.HANDLER_CONTRACT_MISMATCH));
    }

    @Test
    void unknownRegistryIdentityRequiresRecoveryAndCannotLoad() {
        String unknownIdentity = "butchercraft:execution_handler_registry/v1/" + "0".repeat(64);
        String unknownJson = legacyJson(List.of()).replace(OLD_REGISTRY_IDENTITY, unknownIdentity);
        ExecutionStorage storage = currentStorage("unknown.json", currentRegistry());

        ExecutionRegistryCompatibilityException exception = assertThrows(
                ExecutionRegistryCompatibilityException.class,
                () -> storage.deserialize(unknownJson)
        );

        assertEquals(
                ExecutionRegistryCompatibilityClassification.INDETERMINATE_RECOVERY_REQUIRED,
                exception.observation().classification()
        );
        assertTrue(exception.observation().operatorRecoveryRequired());
    }

    @Test
    void operationCannotClaimNewHandlerUnderHistoricalRegistry() {
        ExecutionRegistryCompatibilityObservation observation = classify(
                OLD_REGISTRY_IDENTITY,
                currentRegistry(),
                List.of(operation(CUTTING_TABLE, "anachronistic"))
        );

        assertEquals(ExecutionRegistryCompatibilityClassification.INCOMPATIBLE, observation.classification());
        assertEquals(ExecutionRetainedOperationValidation.INVALID, observation.retainedOperationValidation());
        assertTrue(observation.failures().stream().anyMatch(failure ->
                failure.code() == ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID));
    }

    @Test
    void additiveCompatibleLoadAndUnchangedSaveDoNotRewriteHistoricalFile() throws IOException {
        Path file = temporaryDirectory.resolve("read_only_startup.json");
        String historicalJson = legacyJson(List.of(operation(GRINDER, "read_only")));
        Files.writeString(file, historicalJson, StandardCharsets.UTF_8);
        ExecutionStorage storage = new ExecutionStorage(file, currentRegistry(), CONFIGURATION);

        ExecutionManager loaded = storage.load();
        storage.save(loaded);

        assertEquals(historicalJson, Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(Files.notExists(file.resolveSibling(file.getFileName() + ".tmp")));
    }

    @Test
    void releasedProfileAndCurrentRegistryIdentitiesAreStable() {
        ExecutionLegacyRegistryCompatibilityProfile profile =
                ExecutionLegacyRegistryProfiles.preCuttingTableSchema1();

        assertEquals(OLD_REGISTRY_IDENTITY, profile.persistedRegistryIdentity());
        assertEquals(
                "butchercraft:execution_legacy_registry_profile/v1/"
                        + "3eef5b61f5251d228b046522803b7a61df183bf897a884becdc38d6b6de7dc30",
                profile.profileIdentity()
        );
        assertEquals(
                "butchercraft:execution_handler_contract/v1/"
                        + "6bd9fa6236ddc4d6734a8a854c8ab7141d905071efdac82060602f5fa6c14faf",
                GRINDER.contractIdentity()
        );
        assertEquals(
                "butchercraft:execution_handler_contract/v1/"
                        + "6e58c1634bdf1b5d5d0c70970053528aad149e334831eeeb0055517796278af7",
                PATTY_FORMER.contractIdentity()
        );
        assertEquals(
                "butchercraft:execution_handler_contract/v1/"
                        + "3188a9f1c7020d622706e97afbb0749d6a1b63543204735cacf71a2ddd709705",
                CUTTING_TABLE.contractIdentity()
        );
        assertEquals(CURRENT_REGISTRY_IDENTITY, currentRegistry().registryIdentity());
        assertThrows(UnsupportedOperationException.class, () -> profile.handlers().clear());
        assertTrue(ExecutionLegacyRegistryProfiles.find(
                "butchercraft:execution_handler_registry/v1/" + "f".repeat(64)
        ).isEmpty());
    }

    private ExecutionRegistryCompatibilityObservation classify(
            String persistedRegistryIdentity,
            ExecutionHandlerRegistry current,
            List<ExecutionOperationSnapshot> operations
    ) {
        return ExecutionRegistryCompatibilityClassifier.standard().classify(
                ExecutionSchema.CURRENT_VERSION,
                persistedRegistryIdentity,
                CONFIGURATION.configurationIdentity(),
                current,
                CONFIGURATION,
                operations
        );
    }

    private ExecutionStorage currentStorage(String fileName, ExecutionHandlerRegistry registry) {
        return new ExecutionStorage(temporaryDirectory.resolve(fileName), registry, CONFIGURATION);
    }

    private static String legacyJson(List<ExecutionOperationSnapshot> operations) {
        ExecutionHandlerRegistry legacy = registry(GRINDER, PATTY_FORMER);
        ExecutionManager manager = new ExecutionManager(legacy, CONFIGURATION, operations);
        return new ExecutionStorage(Path.of("legacy.json"), legacy, CONFIGURATION).serialize(manager);
    }

    private static ExecutionOperationSnapshot operation(ExecutionHandlerContract contract, String suffix) {
        ExecutionHandlerRegistry registry = registry(contract);
        ExecutionManager manager = new ExecutionManager(registry, CONFIGURATION);
        String frozenInput = "test:frozen/" + suffix;
        ExecutionAuthorizationEvidence evidence = ExecutionAuthorizationEvidence.issued(
                "test:execution_authority",
                "test:executable_work",
                "test:work/" + suffix,
                contract.operationType(),
                contract.handlerId(),
                frozenInput,
                "test:freshness/" + suffix,
                contract.configurationIdentity(),
                "test:world/identity",
                0,
                OptionalLong.empty(),
                List.of(frozenInput)
        );
        return manager.acceptAuthorization(new ExecutionAuthorization(evidence), 0).value().orElseThrow();
    }

    private static ExecutionHandlerRegistry currentRegistry() {
        return registry(GRINDER, PATTY_FORMER, CUTTING_TABLE);
    }

    private static ExecutionHandlerRegistry registry(ExecutionHandlerContract... contracts) {
        List<ExecutionOperationHandler> handlers = new ArrayList<>();
        for (ExecutionHandlerContract contract : contracts) {
            handlers.add(new ExecutionOperationHandler() {
                @Override
                public ExecutionHandlerContract contract() {
                    return contract;
                }

                @Override
                public ExecutionHandlerValidation validateAuthorization(ExecutionAuthorizationEvidence evidence) {
                    return ExecutionHandlerValidation.acceptedResult();
                }

                @Override
                public ExecutionHandlerResult execute(ExecutionHandlerContext context) {
                    return ExecutionHandlerResult.failed(
                            ExecutionFailure.of(
                                    ExecutionFailureCode.HANDLER_FAILED,
                                    "Compatibility fixture handlers do not execute",
                                    context.operation().operationId().value()
                            ),
                            1
                    );
                }
            });
        }
        return new ExecutionHandlerRegistry(handlers);
    }
}
