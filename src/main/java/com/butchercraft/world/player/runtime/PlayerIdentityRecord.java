package com.butchercraft.world.player.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.ownership.FamilyId;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.player.CareerProfile;
import com.butchercraft.world.player.PlayerIdentityId;
import com.butchercraft.world.player.StartingScenarioId;
import com.butchercraft.world.property.CommercialPropertyId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerIdentityRecord(
        UUID minecraftUuid,
        PlayerIdentityId identityId,
        StartingScenarioId startingScenarioId,
        CareerProfile careerProfile,
        String settlementId,
        Optional<CommercialPropertyId> commercialPropertyId,
        Optional<BusinessId> businessId,
        Optional<OwnershipEntityId> ownershipEntityId,
        Optional<FamilyId> familyId,
        Instant creationTimestamp,
        int schemaVersion
) {
    public PlayerIdentityRecord {
        minecraftUuid = Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        identityId = Objects.requireNonNull(identityId, "identityId");
        startingScenarioId = Objects.requireNonNull(startingScenarioId, "startingScenarioId");
        careerProfile = Objects.requireNonNull(careerProfile, "careerProfile");
        settlementId = requireNonBlank(settlementId, "settlementId");
        commercialPropertyId = copyOptional(commercialPropertyId, "commercialPropertyId");
        businessId = copyOptional(businessId, "businessId");
        ownershipEntityId = copyOptional(ownershipEntityId, "ownershipEntityId");
        familyId = copyOptional(familyId, "familyId");
        creationTimestamp = Objects.requireNonNull(creationTimestamp, "creationTimestamp");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Player identity schema version must be positive: " + schemaVersion);
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Player identity " + fieldName + " must not be blank");
        }
        return value;
    }

    private static <T> Optional<T> copyOptional(Optional<T> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        value.ifPresent(ignored -> Objects.requireNonNull(ignored, fieldName + " value"));
        return value;
    }
}
