package com.butchercraft.world.player.runtime;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityDeterminism;
import com.butchercraft.world.ownership.Family;
import com.butchercraft.world.ownership.FamilyId;
import com.butchercraft.world.ownership.OwnershipEntity;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.ownership.OwnershipHistory;
import com.butchercraft.world.ownership.OwnershipRecord;
import com.butchercraft.world.player.CareerProfile;
import com.butchercraft.world.player.PlayerIdentityId;
import com.butchercraft.world.player.PlayerRegistry;
import com.butchercraft.world.player.StartingScenario;
import com.butchercraft.world.player.StartingScenarioType;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;
import com.butchercraft.world.property.CommercialPropertyType;
import com.butchercraft.world.property.PropertyStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PlayerIdentityFactory {
    private static final long ID_SALT = 0x6b49658c5d1f0a91L;
    private static final long SCENARIO_SALT = 0x52d4d7235af1c96dL;
    private static final long CAREER_SALT = 0x1d50d352fb9ac071L;
    private static final long PROPERTY_SALT = 0x3c2c7fdcace1f0b5L;
    private static final long BUSINESS_SALT = 0x5eda0310226823d1L;
    private static final long OWNERSHIP_SALT = 0x28a24b6d3ba640b7L;
    private static final long FAMILY_SALT = 0x43607dfb0a58a1d3L;
    private static final long TIMESTAMP_SALT = 0x2adf0d27ec40516fL;
    private static final Instant TIMESTAMP_EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final long TIMESTAMP_RANGE_SECONDS = 366L * 24L * 60L * 60L;

    private final PlayerRegistry startingScenarioRegistry;

    public PlayerIdentityFactory() {
        this(PlayerRegistry.builtIn());
    }

    public PlayerIdentityFactory(PlayerRegistry startingScenarioRegistry) {
        this.startingScenarioRegistry = Objects.requireNonNull(startingScenarioRegistry, "startingScenarioRegistry");
    }

    public PlayerIdentityRecord createFirstTimeIdentity(UUID minecraftUuid, WorldIdentity worldIdentity) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        StartingScenario scenario = chooseScenario(minecraftUuid, worldIdentity);
        CareerProfile careerProfile = chooseCareerProfile(minecraftUuid, worldIdentity, scenario);
        ResolvedReferences references = resolveReferences(minecraftUuid, worldIdentity, scenario);
        PlayerIdentityRecord identity = new PlayerIdentityRecord(
                minecraftUuid,
                identityId(minecraftUuid, worldIdentity),
                scenario.id(),
                careerProfile,
                references.settlementId(),
                references.commercialPropertyId(),
                references.businessId(),
                references.ownershipEntityId(),
                references.familyId(),
                creationTimestamp(minecraftUuid, worldIdentity),
                PlayerIdentityRuntimeSchema.CURRENT_VERSION
        );
        PlayerIdentityRegistry.of(List.of(identity)).validateReferences(worldIdentity, startingScenarioRegistry);
        return identity;
    }

    public PlayerRegistry startingScenarioRegistry() {
        return startingScenarioRegistry;
    }

    private StartingScenario chooseScenario(UUID minecraftUuid, WorldIdentity worldIdentity) {
        List<StartingScenario> scenarios = startingScenarioRegistry.startingScenarios();
        return stablePick(scenarios, worldIdentity.worldSeed(), SCENARIO_SALT, minecraftUuid, "scenario");
    }

    private CareerProfile chooseCareerProfile(UUID minecraftUuid, WorldIdentity worldIdentity, StartingScenario scenario) {
        return stablePick(scenario.careerProfiles(), worldIdentity.worldSeed(), CAREER_SALT, minecraftUuid, scenario.id().value());
    }

    private PlayerIdentityId identityId(UUID minecraftUuid, WorldIdentity worldIdentity) {
        long score = WorldIdentityDeterminism.stableScore(worldIdentity.worldSeed(), ID_SALT, minecraftUuid.toString());
        return new PlayerIdentityId("player_" + Long.toUnsignedString(score, 36));
    }

    private Instant creationTimestamp(UUID minecraftUuid, WorldIdentity worldIdentity) {
        long offset = Long.remainderUnsigned(
                WorldIdentityDeterminism.stableScore(worldIdentity.worldSeed(), TIMESTAMP_SALT, minecraftUuid.toString()),
                TIMESTAMP_RANGE_SECONDS
        );
        return TIMESTAMP_EPOCH.plusSeconds(offset);
    }

    private ResolvedReferences resolveReferences(UUID minecraftUuid, WorldIdentity worldIdentity, StartingScenario scenario) {
        Optional<Business> business = requiresExistingBusiness(scenario.scenarioType())
                ? chooseBusiness(minecraftUuid, worldIdentity, scenario)
                : Optional.empty();
        Optional<CommercialProperty> property = business
                .map(businessRecord -> propertyById(worldIdentity, businessRecord.primaryPropertyId()))
                .orElseGet(() -> chooseStandaloneProperty(minecraftUuid, worldIdentity, scenario));
        String settlementId = property
                .map(CommercialProperty::settlementId)
                .orElseGet(() -> chooseSettlement(minecraftUuid, worldIdentity).id());
        Optional<OwnershipEntity> ownershipEntity = business.flatMap(value -> chooseOwnershipEntity(minecraftUuid, worldIdentity, value));
        Optional<Family> family = ownershipEntity.flatMap(entity -> entity.familyId().flatMap(familyId -> familyById(worldIdentity, familyId)))
                .or(() -> scenario.scenarioType() == StartingScenarioType.INHERITED_FAMILY_BUSINESS
                        ? chooseFamily(minecraftUuid, worldIdentity, settlementId)
                        : Optional.empty());
        return new ResolvedReferences(
                settlementId,
                property.map(CommercialProperty::id),
                business.map(Business::id),
                ownershipEntity.map(OwnershipEntity::id),
                family.map(Family::id)
        );
    }

    private static boolean requiresExistingBusiness(StartingScenarioType scenarioType) {
        return switch (scenarioType) {
            case INHERITED_FAMILY_BUSINESS, EXISTING_BUSINESS_MANAGER, COUNTY_CONTRACT, COOPERATIVE_ASSIGNMENT -> true;
            case VACANT_PROPERTY_PURCHASE, STARTUP_OPERATION -> false;
        };
    }

    private Optional<Business> chooseBusiness(UUID minecraftUuid, WorldIdentity worldIdentity, StartingScenario scenario) {
        List<Business> candidates = worldIdentity.businesses().stream()
                .filter(business -> business.status().hasActiveOccupancy())
                .filter(business -> scenario.scenarioType() != StartingScenarioType.INHERITED_FAMILY_BUSINESS
                        || businessHasFamilyOwnership(worldIdentity, business))
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = worldIdentity.businesses().stream()
                    .sorted(Comparator.comparing(business -> business.id().value()))
                    .toList();
        }
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.of(stablePick(candidates, worldIdentity.worldSeed(), BUSINESS_SALT, minecraftUuid, scenario.id().value()));
    }

    private boolean businessHasFamilyOwnership(WorldIdentity worldIdentity, Business business) {
        return worldIdentity.ownershipHistories().stream()
                .filter(history -> history.businessId().equals(business.id()))
                .flatMap(history -> history.ownershipRecords().stream())
                .map(OwnershipRecord::ownershipEntityId)
                .map(entityId -> ownershipEntityById(worldIdentity, entityId))
                .flatMap(Optional::stream)
                .anyMatch(entity -> entity.familyId().isPresent());
    }

    private Optional<CommercialProperty> chooseStandaloneProperty(
            UUID minecraftUuid,
            WorldIdentity worldIdentity,
            StartingScenario scenario
    ) {
        Set<CommercialPropertyId> businessPropertyIds = worldIdentity.businesses().stream()
                .flatMap(business -> business.associatedCommercialPropertyIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        List<CommercialProperty> candidates = worldIdentity.commercialProperties().stream()
                .filter(property -> !businessPropertyIds.contains(property.id())
                        || property.propertyType() == CommercialPropertyType.EMPTY_COMMERCIAL_LOT
                        || property.status() == PropertyStatus.VACANT
                        || property.status() == PropertyStatus.RESERVED
                        || property.status() == PropertyStatus.UNDER_RENOVATION)
                .sorted(Comparator.comparing(property -> property.id().value()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = worldIdentity.commercialProperties().stream()
                    .sorted(Comparator.comparing(property -> property.id().value()))
                    .toList();
        }
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.of(stablePick(candidates, worldIdentity.worldSeed(), PROPERTY_SALT, minecraftUuid, scenario.id().value()));
    }

    private Optional<OwnershipEntity> chooseOwnershipEntity(UUID minecraftUuid, WorldIdentity worldIdentity, Business business) {
        List<OwnershipEntityId> currentOwnerIds = worldIdentity.ownershipHistories().stream()
                .filter(history -> history.businessId().equals(business.id()))
                .flatMap(history -> history.ownershipRecords().stream())
                .filter(OwnershipRecord::isCurrent)
                .map(OwnershipRecord::ownershipEntityId)
                .sorted(Comparator.comparing(OwnershipEntityId::value))
                .toList();
        List<OwnershipEntityId> candidates = currentOwnerIds.isEmpty()
                ? worldIdentity.ownershipHistories().stream()
                .filter(history -> history.businessId().equals(business.id()))
                .flatMap(history -> history.ownershipRecords().stream())
                .map(OwnershipRecord::ownershipEntityId)
                .distinct()
                .sorted(Comparator.comparing(OwnershipEntityId::value))
                .toList()
                : currentOwnerIds;
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        OwnershipEntityId entityId = stablePick(candidates, worldIdentity.worldSeed(), OWNERSHIP_SALT, minecraftUuid, business.id().value());
        return ownershipEntityById(worldIdentity, entityId);
    }

    private Optional<Family> chooseFamily(UUID minecraftUuid, WorldIdentity worldIdentity, String settlementId) {
        List<Family> candidates = worldIdentity.families().stream()
                .filter(family -> family.foundingSettlementId().equals(settlementId))
                .sorted(Comparator.comparing(family -> family.id().value()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = worldIdentity.families().stream()
                    .sorted(Comparator.comparing(family -> family.id().value()))
                    .toList();
        }
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.of(stablePick(candidates, worldIdentity.worldSeed(), FAMILY_SALT, minecraftUuid, settlementId));
    }

    private Settlement chooseSettlement(UUID minecraftUuid, WorldIdentity worldIdentity) {
        List<Settlement> settlements = worldIdentity.settlements().stream()
                .sorted(Comparator.comparing(Settlement::id))
                .toList();
        return stablePick(settlements, worldIdentity.worldSeed(), PROPERTY_SALT, minecraftUuid, "settlement");
    }

    private static Optional<CommercialProperty> propertyById(WorldIdentity worldIdentity, CommercialPropertyId propertyId) {
        Map<CommercialPropertyId, CommercialProperty> propertiesById = worldIdentity.commercialProperties().stream()
                .collect(Collectors.toUnmodifiableMap(CommercialProperty::id, Function.identity()));
        return Optional.ofNullable(propertiesById.get(propertyId));
    }

    private static Optional<OwnershipEntity> ownershipEntityById(WorldIdentity worldIdentity, OwnershipEntityId entityId) {
        Map<OwnershipEntityId, OwnershipEntity> ownershipEntitiesById = worldIdentity.ownershipEntities().stream()
                .collect(Collectors.toUnmodifiableMap(OwnershipEntity::id, Function.identity()));
        return Optional.ofNullable(ownershipEntitiesById.get(entityId));
    }

    private static Optional<Family> familyById(WorldIdentity worldIdentity, FamilyId familyId) {
        Map<FamilyId, Family> familiesById = worldIdentity.families().stream()
                .collect(Collectors.toUnmodifiableMap(Family::id, Function.identity()));
        return Optional.ofNullable(familiesById.get(familyId));
    }

    private static <T> T stablePick(List<T> candidates, long worldSeed, long salt, UUID minecraftUuid, String discriminator) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose from an empty candidate list");
        }
        int index = WorldIdentityDeterminism.stableIndex(worldSeed, salt, candidates.size(), minecraftUuid.toString(), discriminator);
        return candidates.get(index);
    }

    private record ResolvedReferences(
            String settlementId,
            Optional<CommercialPropertyId> commercialPropertyId,
            Optional<BusinessId> businessId,
            Optional<OwnershipEntityId> ownershipEntityId,
            Optional<FamilyId> familyId
    ) {
    }
}
