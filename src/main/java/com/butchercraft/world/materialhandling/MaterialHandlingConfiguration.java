package com.butchercraft.world.materialhandling;

public record MaterialHandlingConfiguration(
        int maximumTransfers,
        int maximumCustodyPayloadBytes,
        int maximumReconciliationsPerStartup,
        String configurationIdentity
) {
    public MaterialHandlingConfiguration {
        if (maximumTransfers <= 0 || maximumCustodyPayloadBytes <= 0 || maximumReconciliationsPerStartup <= 0) {
            throw new IllegalArgumentException("Material Handling limits must be positive");
        }
        configurationIdentity = MaterialHandlingValidation.id(
                configurationIdentity,
                "Material Handling configuration identity"
        );
    }

    public static MaterialHandlingConfiguration standard() {
        return new MaterialHandlingConfiguration(
                4_096,
                1_048_576,
                256,
                "butchercraft:material_handling_configuration/v1/standard"
        );
    }
}
