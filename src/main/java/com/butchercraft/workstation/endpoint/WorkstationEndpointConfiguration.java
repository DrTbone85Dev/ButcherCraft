package com.butchercraft.workstation.endpoint;

public record WorkstationEndpointConfiguration(
        int maximumInstanceRecords,
        int maximumJournalRecords,
        int maximumPayloadBytes,
        int maximumReconciliationsPerAction,
        String instanceAllocationConfigurationIdentity,
        String endpointConfigurationIdentity
) {
    public WorkstationEndpointConfiguration {
        maximumInstanceRecords = WorkstationEndpointValidation.positive(
                maximumInstanceRecords,
                "maximum instance records"
        );
        maximumJournalRecords = WorkstationEndpointValidation.positive(
                maximumJournalRecords,
                "maximum journal records"
        );
        maximumPayloadBytes = WorkstationEndpointValidation.positive(
                maximumPayloadBytes,
                "maximum endpoint payload bytes"
        );
        maximumReconciliationsPerAction = WorkstationEndpointValidation.positive(
                maximumReconciliationsPerAction,
                "maximum endpoint reconciliations per action"
        );
        instanceAllocationConfigurationIdentity = WorkstationEndpointValidation.id(
                instanceAllocationConfigurationIdentity,
                "instance allocation configuration identity"
        );
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
    }

    public static WorkstationEndpointConfiguration standard() {
        return new WorkstationEndpointConfiguration(
                4_096,
                16_384,
                1_048_576,
                1_024,
                "butchercraft:workstation_instance_configuration/v1/standard",
                "butchercraft:workstation_endpoint_configuration/v1/standard"
        );
    }
}
