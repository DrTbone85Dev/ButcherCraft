package com.butchercraft.world.ownership;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OwnershipRegistry {
    private final List<OwnershipEntity> ownershipEntities;
    private final List<OwnershipHistory> ownershipHistories;
    private final Map<OwnershipEntityId, OwnershipEntity> entitiesById;
    private final Map<BusinessId, OwnershipHistory> historiesByBusinessId;

    private OwnershipRegistry(
            List<OwnershipEntity> ownershipEntities,
            List<OwnershipHistory> ownershipHistories,
            Map<OwnershipEntityId, OwnershipEntity> entitiesById,
            Map<BusinessId, OwnershipHistory> historiesByBusinessId
    ) {
        this.ownershipEntities = ownershipEntities;
        this.ownershipHistories = ownershipHistories;
        this.entitiesById = entitiesById;
        this.historiesByBusinessId = historiesByBusinessId;
    }

    public static OwnershipRegistry of(
            Collection<OwnershipEntity> ownershipEntities,
            Collection<OwnershipHistory> ownershipHistories,
            Collection<Family> families,
            Collection<PersonIdentity> people,
            Collection<Business> businesses
    ) {
        Objects.requireNonNull(ownershipEntities, "ownershipEntities");
        Objects.requireNonNull(ownershipHistories, "ownershipHistories");
        Objects.requireNonNull(families, "families");
        Objects.requireNonNull(people, "people");
        Objects.requireNonNull(businesses, "businesses");
        if (ownershipEntities.isEmpty()) {
            throw new IllegalArgumentException("Ownership registry must contain at least one ownership entity");
        }
        if (ownershipHistories.isEmpty()) {
            throw new IllegalArgumentException("Ownership registry must contain at least one ownership history");
        }

        Set<FamilyId> familyIds = families.stream().map(Family::id).collect(Collectors.toUnmodifiableSet());
        Set<PersonId> personIds = people.stream().map(PersonIdentity::id).collect(Collectors.toUnmodifiableSet());
        Set<BusinessId> businessIds = businesses.stream().map(Business::id).collect(Collectors.toUnmodifiableSet());

        List<OwnershipEntity> deterministicEntities = ownershipEntities.stream()
                .map(entity -> validateEntityReferences(entity, familyIds, personIds))
                .sorted(Comparator.comparing(entity -> entity.id().value()))
                .toList();
        rejectDuplicates(deterministicEntities, OwnershipEntity::id, "ownership entity id");

        Map<OwnershipEntityId, OwnershipEntity> byEntityId = deterministicEntities.stream()
                .collect(Collectors.toUnmodifiableMap(OwnershipEntity::id, Function.identity()));

        List<OwnershipHistory> deterministicHistories = ownershipHistories.stream()
                .map(history -> validateHistoryReferences(history, businessIds, byEntityId.keySet()))
                .sorted(Comparator.comparing(history -> history.businessId().value()))
                .toList();
        rejectDuplicates(deterministicHistories, OwnershipHistory::businessId, "ownership history business id");
        rejectMissingHistories(deterministicHistories, businessIds);
        rejectOrphanedOwnershipEntities(deterministicHistories, byEntityId.keySet());

        Map<BusinessId, OwnershipHistory> byBusinessId = deterministicHistories.stream()
                .collect(Collectors.toUnmodifiableMap(OwnershipHistory::businessId, Function.identity()));
        return new OwnershipRegistry(
                List.copyOf(deterministicEntities),
                List.copyOf(deterministicHistories),
                byEntityId,
                byBusinessId
        );
    }

    public boolean contains(OwnershipEntityId id) {
        return entitiesById.containsKey(id);
    }

    public Optional<OwnershipEntity> find(OwnershipEntityId id) {
        return Optional.ofNullable(entitiesById.get(id));
    }

    public Optional<OwnershipHistory> findHistory(BusinessId businessId) {
        return Optional.ofNullable(historiesByBusinessId.get(businessId));
    }

    public int entityCount() {
        return ownershipEntities.size();
    }

    public int historyCount() {
        return ownershipHistories.size();
    }

    public List<OwnershipEntity> ownershipEntities() {
        return ownershipEntities;
    }

    public List<OwnershipHistory> ownershipHistories() {
        return ownershipHistories;
    }

    public Stream<OwnershipEntity> entityStream() {
        return ownershipEntities.stream();
    }

    public Stream<OwnershipHistory> historyStream() {
        return ownershipHistories.stream();
    }

    public List<OwnershipEntity> findByType(OwnershipEntityType type) {
        Objects.requireNonNull(type, "type");
        return ownershipEntities.stream()
                .filter(entity -> entity.type() == type)
                .toList();
    }

    public List<OwnershipEntity> findByFamily(FamilyId familyId) {
        Objects.requireNonNull(familyId, "familyId");
        return ownershipEntities.stream()
                .filter(entity -> entity.familyId().filter(familyId::equals).isPresent())
                .toList();
    }

    public List<OwnershipRecord> findRecordsByBusiness(BusinessId businessId) {
        return findHistory(businessId)
                .map(OwnershipHistory::ownershipRecords)
                .orElse(List.of());
    }

    public List<OwnershipRecord> findRecordsByEntity(OwnershipEntityId entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return ownershipHistories.stream()
                .flatMap(history -> history.ownershipRecords().stream())
                .filter(record -> record.ownershipEntityId().equals(entityId))
                .toList();
    }

    public List<OwnershipEntity> search(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return ownershipEntities.stream()
                .filter(entity -> searchableText(entity).contains(normalized))
                .toList();
    }

    private static OwnershipEntity validateEntityReferences(
            OwnershipEntity entity,
            Set<FamilyId> familyIds,
            Set<PersonId> personIds
    ) {
        Objects.requireNonNull(entity, "ownershipEntity");
        entity.familyId().ifPresent(familyId -> {
            if (!familyIds.contains(familyId)) {
                throw new IllegalArgumentException("Ownership entity " + entity.id().value()
                        + " references unknown family: " + familyId.value());
            }
        });
        entity.personId().ifPresent(personId -> {
            if (!personIds.contains(personId)) {
                throw new IllegalArgumentException("Ownership entity " + entity.id().value()
                        + " references unknown person: " + personId.value());
            }
        });
        for (FamilyRelationship relationship : entity.familyRelationships()) {
            if (entity.familyId().filter(relationship.relatedFamilyId()::equals).isPresent()) {
                throw new IllegalArgumentException("Ownership entity family relationship must not reference its own family");
            }
            if (!familyIds.contains(relationship.relatedFamilyId())) {
                throw new IllegalArgumentException("Ownership entity " + entity.id().value()
                        + " references unknown related family: " + relationship.relatedFamilyId().value());
            }
        }
        return entity;
    }

    private static OwnershipHistory validateHistoryReferences(
            OwnershipHistory history,
            Set<BusinessId> businessIds,
            Set<OwnershipEntityId> entityIds
    ) {
        Objects.requireNonNull(history, "ownershipHistory");
        if (!businessIds.contains(history.businessId())) {
            throw new IllegalArgumentException("Ownership history references unknown business: " + history.businessId().value());
        }
        for (OwnershipRecord record : history.ownershipRecords()) {
            if (!entityIds.contains(record.ownershipEntityId())) {
                throw new IllegalArgumentException("Ownership record references unknown ownership entity: "
                        + record.ownershipEntityId().value());
            }
        }
        return history;
    }

    private static void rejectMissingHistories(List<OwnershipHistory> histories, Set<BusinessId> businessIds) {
        Set<BusinessId> ownedBusinesses = histories.stream()
                .map(OwnershipHistory::businessId)
                .collect(Collectors.toUnmodifiableSet());
        Set<BusinessId> missing = businessIds.stream()
                .filter(id -> !ownedBusinesses.contains(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing ownership histories for businesses: " + missing);
        }
    }

    private static void rejectOrphanedOwnershipEntities(
            List<OwnershipHistory> histories,
            Set<OwnershipEntityId> ownershipEntityIds
    ) {
        Set<OwnershipEntityId> referenced = histories.stream()
                .flatMap(history -> history.ownershipRecords().stream())
                .map(OwnershipRecord::ownershipEntityId)
                .collect(Collectors.toUnmodifiableSet());
        Set<OwnershipEntityId> orphaned = ownershipEntityIds.stream()
                .filter(id -> !referenced.contains(id))
                .collect(Collectors.toSet());
        if (!orphaned.isEmpty()) {
            throw new IllegalArgumentException("Orphaned ownership entities: " + orphaned);
        }
    }

    private static <T, K> void rejectDuplicates(List<T> values, Function<T, K> keyFunction, String label) {
        Set<K> duplicates = values.stream()
                .map(keyFunction)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate " + label + ": " + duplicates);
        }
    }

    private static String searchableText(OwnershipEntity entity) {
        return normalize(String.join(" ",
                entity.id().value(),
                entity.displayName(),
                entity.type().serializedName(),
                entity.historicalSummary()
        ));
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT);
    }
}
