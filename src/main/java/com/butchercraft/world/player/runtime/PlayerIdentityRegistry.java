package com.butchercraft.world.player.runtime;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.ownership.Family;
import com.butchercraft.world.ownership.FamilyId;
import com.butchercraft.world.ownership.OwnershipEntity;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.ownership.OwnershipHistory;
import com.butchercraft.world.player.PlayerIdentityId;
import com.butchercraft.world.player.PlayerRegistry;
import com.butchercraft.world.player.StartingScenario;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PlayerIdentityRegistry {
    private static final PlayerIdentityRegistry EMPTY = new PlayerIdentityRegistry(List.of(), Map.of(), Map.of());

    private final List<PlayerIdentityRecord> identities;
    private final Map<UUID, PlayerIdentityRecord> identitiesByUuid;
    private final Map<PlayerIdentityId, PlayerIdentityRecord> identitiesById;

    private PlayerIdentityRegistry(
            List<PlayerIdentityRecord> identities,
            Map<UUID, PlayerIdentityRecord> identitiesByUuid,
            Map<PlayerIdentityId, PlayerIdentityRecord> identitiesById
    ) {
        this.identities = identities;
        this.identitiesByUuid = identitiesByUuid;
        this.identitiesById = identitiesById;
    }

    public static PlayerIdentityRegistry empty() {
        return EMPTY;
    }

    public static PlayerIdentityRegistry of(Collection<PlayerIdentityRecord> identities) {
        Objects.requireNonNull(identities, "identities");
        List<PlayerIdentityRecord> deterministicIdentities = identities.stream()
                .map(identity -> Objects.requireNonNull(identity, "identity"))
                .sorted(Comparator.comparing(identity -> identity.minecraftUuid().toString()))
                .toList();
        rejectDuplicateMinecraftUuids(deterministicIdentities);
        rejectDuplicateIdentityIds(deterministicIdentities);
        Map<UUID, PlayerIdentityRecord> byUuid = deterministicIdentities.stream()
                .collect(Collectors.toUnmodifiableMap(PlayerIdentityRecord::minecraftUuid, Function.identity()));
        Map<PlayerIdentityId, PlayerIdentityRecord> byId = deterministicIdentities.stream()
                .collect(Collectors.toUnmodifiableMap(PlayerIdentityRecord::identityId, Function.identity()));
        return new PlayerIdentityRegistry(List.copyOf(deterministicIdentities), byUuid, byId);
    }

    public PlayerIdentityRegistry with(
            PlayerIdentityRecord identity,
            WorldIdentity worldIdentity,
            PlayerRegistry startingScenarioRegistry
    ) {
        Objects.requireNonNull(identity, "identity");
        if (contains(identity.minecraftUuid())) {
            throw new IllegalArgumentException("Duplicate Minecraft UUID: " + identity.minecraftUuid());
        }
        if (contains(identity.identityId())) {
            throw new IllegalArgumentException("Duplicate player identity id: " + identity.identityId().value());
        }
        List<PlayerIdentityRecord> updated = new ArrayList<>(identities);
        updated.add(identity);
        PlayerIdentityRegistry candidate = of(updated);
        candidate.validateReferences(worldIdentity, startingScenarioRegistry);
        return candidate;
    }

    public boolean contains(UUID minecraftUuid) {
        return identitiesByUuid.containsKey(minecraftUuid);
    }

    public boolean contains(PlayerIdentityId identityId) {
        return identitiesById.containsKey(identityId);
    }

    public Optional<PlayerIdentityRecord> find(UUID minecraftUuid) {
        return Optional.ofNullable(identitiesByUuid.get(minecraftUuid));
    }

    public Optional<PlayerIdentityRecord> find(PlayerIdentityId identityId) {
        return Optional.ofNullable(identitiesById.get(identityId));
    }

    public int size() {
        return identities.size();
    }

    public List<PlayerIdentityRecord> identities() {
        return identities;
    }

    public Stream<PlayerIdentityRecord> stream() {
        return identities.stream();
    }

    public void validateReferences(WorldIdentity worldIdentity, PlayerRegistry startingScenarioRegistry) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(startingScenarioRegistry, "startingScenarioRegistry");
        ReferenceIndexes indexes = ReferenceIndexes.from(worldIdentity);
        for (PlayerIdentityRecord identity : identities) {
            validateIdentityReferences(identity, startingScenarioRegistry, indexes);
        }
    }

    private static void validateIdentityReferences(
            PlayerIdentityRecord identity,
            PlayerRegistry startingScenarioRegistry,
            ReferenceIndexes indexes
    ) {
        StartingScenario scenario = startingScenarioRegistry.find(identity.startingScenarioId())
                .orElseThrow(() -> new IllegalArgumentException("Player identity references unknown starting scenario: "
                        + identity.startingScenarioId().value()));
        if (!scenario.careerProfiles().contains(identity.careerProfile())) {
            throw new IllegalArgumentException("Player identity career profile is not valid for starting scenario: "
                    + identity.careerProfile().serializedName());
        }
        if (!indexes.settlementsById.containsKey(identity.settlementId())) {
            throw new IllegalArgumentException("Player identity references missing settlement: " + identity.settlementId());
        }
        identity.commercialPropertyId().ifPresent(propertyId -> validateProperty(identity, propertyId, indexes));
        identity.businessId().ifPresent(businessId -> validateBusiness(identity, businessId, indexes));
        identity.familyId().ifPresent(familyId -> validateFamily(familyId, indexes));
        identity.ownershipEntityId().ifPresent(entityId -> validateOwnershipEntity(identity, entityId, indexes));
    }

    private static void validateProperty(
            PlayerIdentityRecord identity,
            CommercialPropertyId propertyId,
            ReferenceIndexes indexes
    ) {
        CommercialProperty property = indexes.propertiesById.get(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("Player identity references missing commercial property: " + propertyId.value());
        }
        if (!property.settlementId().equals(identity.settlementId())) {
            throw new IllegalArgumentException("Player identity commercial property is outside the starting settlement: "
                    + propertyId.value());
        }
    }

    private static void validateBusiness(PlayerIdentityRecord identity, BusinessId businessId, ReferenceIndexes indexes) {
        Business business = indexes.businessesById.get(businessId);
        if (business == null) {
            throw new IllegalArgumentException("Player identity references missing business: " + businessId.value());
        }
        if (!business.primarySettlementId().equals(identity.settlementId())) {
            throw new IllegalArgumentException("Player identity business is outside the starting settlement: "
                    + businessId.value());
        }
        identity.commercialPropertyId().ifPresent(propertyId -> {
            if (!business.associatedCommercialPropertyIds().contains(propertyId)) {
                throw new IllegalArgumentException("Player identity business does not occupy referenced property: "
                        + businessId.value());
            }
        });
    }

    private static void validateFamily(FamilyId familyId, ReferenceIndexes indexes) {
        if (!indexes.familiesById.containsKey(familyId)) {
            throw new IllegalArgumentException("Player identity references missing family: " + familyId.value());
        }
    }

    private static void validateOwnershipEntity(
            PlayerIdentityRecord identity,
            OwnershipEntityId entityId,
            ReferenceIndexes indexes
    ) {
        OwnershipEntity entity = indexes.ownershipEntitiesById.get(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("Player identity references missing ownership entity: " + entityId.value());
        }
        identity.familyId().ifPresent(familyId -> entity.familyId().ifPresent(entityFamilyId -> {
            if (!entityFamilyId.equals(familyId)) {
                throw new IllegalArgumentException("Player identity ownership entity belongs to a different family: "
                        + entityId.value());
            }
        }));
        identity.businessId().ifPresent(businessId -> {
            OwnershipHistory history = indexes.ownershipHistoriesByBusinessId.get(businessId);
            if (history == null || history.ownershipRecords().stream()
                    .noneMatch(record -> record.ownershipEntityId().equals(entityId))) {
                throw new IllegalArgumentException("Player identity ownership entity does not own referenced business: "
                        + entityId.value());
            }
        });
    }

    private static void rejectDuplicateMinecraftUuids(List<PlayerIdentityRecord> identities) {
        Set<UUID> duplicates = duplicates(identities, PlayerIdentityRecord::minecraftUuid);
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate Minecraft UUIDs: " + duplicates);
        }
    }

    private static void rejectDuplicateIdentityIds(List<PlayerIdentityRecord> identities) {
        Set<PlayerIdentityId> duplicates = duplicates(identities, PlayerIdentityRecord::identityId);
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate player identity ids: " + duplicates);
        }
    }

    private static <T, K> Set<K> duplicates(List<T> values, Function<T, K> keyFunction) {
        Set<K> seen = new HashSet<>();
        Set<K> duplicates = new HashSet<>();
        for (T value : values) {
            K key = keyFunction.apply(value);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        return duplicates;
    }

    private record ReferenceIndexes(
            Map<String, Settlement> settlementsById,
            Map<CommercialPropertyId, CommercialProperty> propertiesById,
            Map<BusinessId, Business> businessesById,
            Map<FamilyId, Family> familiesById,
            Map<OwnershipEntityId, OwnershipEntity> ownershipEntitiesById,
            Map<BusinessId, OwnershipHistory> ownershipHistoriesByBusinessId
    ) {
        private static ReferenceIndexes from(WorldIdentity worldIdentity) {
            return new ReferenceIndexes(
                    worldIdentity.settlements().stream()
                            .collect(Collectors.toUnmodifiableMap(Settlement::id, Function.identity())),
                    worldIdentity.commercialProperties().stream()
                            .collect(Collectors.toUnmodifiableMap(CommercialProperty::id, Function.identity())),
                    worldIdentity.businesses().stream()
                            .collect(Collectors.toUnmodifiableMap(Business::id, Function.identity())),
                    worldIdentity.families().stream()
                            .collect(Collectors.toUnmodifiableMap(Family::id, Function.identity())),
                    worldIdentity.ownershipEntities().stream()
                            .collect(Collectors.toUnmodifiableMap(OwnershipEntity::id, Function.identity())),
                    worldIdentity.ownershipHistories().stream()
                            .collect(Collectors.toUnmodifiableMap(OwnershipHistory::businessId, Function.identity()))
            );
        }
    }
}
