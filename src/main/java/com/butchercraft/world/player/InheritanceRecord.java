package com.butchercraft.world.player;

import java.util.Objects;

public record InheritanceRecord(
        String previousOwnerReference,
        String businessReference,
        String propertyReference,
        LegacyAcquisitionType acquisitionType,
        String historicalNotes
) {
    public InheritanceRecord {
        previousOwnerReference = PlayerValidation.requireNonBlank(previousOwnerReference, "inheritance previousOwnerReference");
        businessReference = PlayerValidation.requireNonBlank(businessReference, "inheritance businessReference");
        propertyReference = PlayerValidation.requireNonBlank(propertyReference, "inheritance propertyReference");
        acquisitionType = Objects.requireNonNull(acquisitionType, "acquisitionType");
        historicalNotes = PlayerValidation.requireNonBlank(historicalNotes, "inheritance historicalNotes");
    }
}
