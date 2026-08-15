package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;
import java.util.Optional;

public record MaterialTransferIdV2(String value) implements Comparable<MaterialTransferIdV2> {
    private static final String PREFIX = "butchercraft:material_transfer/v2/";

    public MaterialTransferIdV2 {
        value = MaterialHandlingValidation.id(value, "schema-2 material transfer identity");
        if (!value.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported schema-2 Transfer Identity");
    }

    public static MaterialTransferIdV2 create(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            WorkstationEndpointReference source,
            WorkstationEndpointReference destination,
            String materialIdentity,
            int quantity,
            String assignmentTypeIdentity,
            Optional<String> employeeReference,
            String configurationIdentity
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        MaterialHandlingValidation.positive(sequence, "transfer sequence");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        materialIdentity = MaterialHandlingValidation.id(materialIdentity, "material identity");
        MaterialHandlingValidation.positive(quantity, "transfer quantity");
        assignmentTypeIdentity = MaterialHandlingValidation.id(assignmentTypeIdentity, "assignment type identity");
        employeeReference = Objects.requireNonNull(employeeReference, "employeeReference")
                .map(value -> MaterialHandlingValidation.id(value, "employee reference"));
        configurationIdentity = MaterialHandlingValidation.id(configurationIdentity, "configuration identity");
        String digest = MaterialHandlingDigest.create("butchercraft:material_transfer")
                .add(MaterialHandlingSchema.STACK_AWARE_SCHEMA_VERSION).add(worldIdentity.identity())
                .add(worldIdentity.schemaVersion()).add(worldIdentity.rootDigest()).add(sequence)
                .add(source.instanceId().value()).add(source.endpointKey().canonicalValue()).add(source.generation())
                .add(destination.instanceId().value()).add(destination.endpointKey().canonicalValue())
                .add(destination.generation()).add(materialIdentity).add(quantity).add(assignmentTypeIdentity)
                .add(employeeReference.orElse("")).add(configurationIdentity).finish();
        return new MaterialTransferIdV2(PREFIX + MaterialHandlingDigest.suffix(digest));
    }

    @Override
    public int compareTo(MaterialTransferIdV2 other) {
        return value.compareTo(other.value);
    }
}
