package com.butchercraft.world.execution;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutionLegacyRegistryProfiles {
    public static final String PRE_CUTTING_TABLE_SCHEMA_1_REGISTRY_IDENTITY =
            "butchercraft:execution_handler_registry/v1/"
                    + "6515eccd6845bc157a18980a1ed34e8d06f6adba736c7c8217fff149480413d1";
    public static final String STANDARD_RUNTIME_CONFIGURATION_IDENTITY =
            "butchercraft:execution_runtime_configuration/v1/standard";

    private static final ExecutionLegacyRegistryCompatibilityProfile PRE_CUTTING_TABLE_SCHEMA_1 =
            ExecutionLegacyRegistryCompatibilityProfile.create(
                    1,
                    PRE_CUTTING_TABLE_SCHEMA_1_REGISTRY_IDENTITY,
                    STANDARD_RUNTIME_CONFIGURATION_IDENTITY,
                    List.of(
                            new ExecutionHandlerContractDescriptor(
                                    1,
                                    "butchercraft:execution_handler/grinder_player_operation",
                                    "butchercraft:workstation/grinder_operation",
                                    "butchercraft:execution_handler_contract/v1/"
                                            + "6bd9fa6236ddc4d6734a8a854c8ab7141d905071efdac82060602f5fa6c14faf",
                                    "butchercraft:execution_configuration/grinder_player_operation_v1"
                            ),
                            new ExecutionHandlerContractDescriptor(
                                    1,
                                    "butchercraft:execution_handler/patty_former_player_operation",
                                    "butchercraft:workstation/patty_former_operation",
                                    "butchercraft:execution_handler_contract/v1/"
                                            + "6e58c1634bdf1b5d5d0c70970053528aad149e334831eeeb0055517796278af7",
                                    "butchercraft:execution_configuration/patty_former_player_operation_v1"
                            )
                    )
            );

    public static final String PRE_CONDITION_SCHEMA_1_REGISTRY_IDENTITY =
            "butchercraft:execution_handler_registry/v1/d0337e2d8560f661a17f3e7520c65a24bd039578e4ae337209e99e81edcc92ee";
    private static final ExecutionLegacyRegistryCompatibilityProfile PRE_CONDITION_SCHEMA_1 =
            preConditionProfile();
    private static final List<ExecutionLegacyRegistryCompatibilityProfile> ALL =
            List.of(PRE_CUTTING_TABLE_SCHEMA_1, PRE_CONDITION_SCHEMA_1);
    private static final Map<String, ExecutionLegacyRegistryCompatibilityProfile> BY_REGISTRY_IDENTITY =
            ALL.stream().collect(Collectors.toUnmodifiableMap(
                    ExecutionLegacyRegistryCompatibilityProfile::persistedRegistryIdentity,
                    Function.identity()
            ));

    private ExecutionLegacyRegistryProfiles() {
    }

    private static ExecutionLegacyRegistryCompatibilityProfile preConditionProfile() {
        List<ExecutionHandlerContractDescriptor> descriptors = new java.util.ArrayList<>(PRE_CUTTING_TABLE_SCHEMA_1.handlers());
        descriptors.add(new ExecutionHandlerContractDescriptor(1,
                "butchercraft:execution_handler/cutting_table_player_operation",
                "butchercraft:workstation/cutting_table_operation",
                "butchercraft:execution_handler_contract/v1/3188a9f1c7020d622706e97afbb0749d6a1b63543204735cacf71a2ddd709705",
                "butchercraft:execution_configuration/cutting_table_player_operation_v1"));
        return ExecutionLegacyRegistryCompatibilityProfile.create(1, PRE_CONDITION_SCHEMA_1_REGISTRY_IDENTITY,
                STANDARD_RUNTIME_CONFIGURATION_IDENTITY, descriptors);
    }

    public static List<ExecutionLegacyRegistryCompatibilityProfile> all() {
        return ALL;
    }

    public static ExecutionLegacyRegistryCompatibilityProfile preCuttingTableSchema1() {
        return PRE_CUTTING_TABLE_SCHEMA_1;
    }

    public static Optional<ExecutionLegacyRegistryCompatibilityProfile> find(String registryIdentity) {
        if (registryIdentity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_REGISTRY_IDENTITY.get(registryIdentity));
    }
}
