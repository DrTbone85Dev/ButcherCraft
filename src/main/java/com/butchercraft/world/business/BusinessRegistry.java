package com.butchercraft.world.business;

import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.manufacturer.ManufacturerRegistry;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BusinessRegistry {
    private final List<Business> businesses;
    private final Map<BusinessId, Business> businessesById;

    private BusinessRegistry(List<Business> businesses, Map<BusinessId, Business> businessesById) {
        this.businesses = businesses;
        this.businessesById = businessesById;
    }

    public static BusinessRegistry of(
            Collection<Business> businesses,
            Region region,
            Collection<Settlement> settlements,
            Collection<CommercialProperty> properties
    ) {
        return of(businesses, region, settlements, properties, ManufacturerRegistry.builtIn());
    }

    public static BusinessRegistry of(
            Collection<Business> businesses,
            Region region,
            Collection<Settlement> settlements,
            Collection<CommercialProperty> properties,
            ManufacturerRegistry manufacturers
    ) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(settlements, "settlements");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(manufacturers, "manufacturers");
        if (businesses.isEmpty()) {
            throw new IllegalArgumentException("Business registry must contain at least one business");
        }
        Map<String, Settlement> settlementsById = settlements.stream()
                .collect(Collectors.toUnmodifiableMap(Settlement::id, Function.identity()));
        if (settlementsById.isEmpty()) {
            throw new IllegalArgumentException("Business registry requires at least one settlement");
        }
        Map<CommercialPropertyId, CommercialProperty> propertiesById = properties.stream()
                .collect(Collectors.toUnmodifiableMap(CommercialProperty::id, Function.identity()));
        if (propertiesById.isEmpty()) {
            throw new IllegalArgumentException("Business registry requires at least one commercial property");
        }

        List<Business> deterministicBusinesses = businesses.stream()
                .map(business -> validateReferences(business, region, settlementsById, propertiesById, manufacturers))
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList();
        rejectDuplicateIds(deterministicBusinesses);
        rejectDuplicateNamesWithinSettlement(deterministicBusinesses);
        rejectInvalidRelationships(deterministicBusinesses);

        Map<BusinessId, Business> byId = deterministicBusinesses.stream()
                .collect(Collectors.toUnmodifiableMap(Business::id, Function.identity()));
        return new BusinessRegistry(List.copyOf(deterministicBusinesses), byId);
    }

    public boolean contains(BusinessId id) {
        return businessesById.containsKey(id);
    }

    public Optional<Business> find(BusinessId id) {
        return Optional.ofNullable(businessesById.get(id));
    }

    public Optional<Business> find(String id) {
        return find(new BusinessId(id));
    }

    public int size() {
        return businesses.size();
    }

    public List<Business> businesses() {
        return businesses;
    }

    public Stream<Business> stream() {
        return businesses.stream();
    }

    public List<Business> findByProperty(CommercialPropertyId propertyId) {
        Objects.requireNonNull(propertyId, "propertyId");
        return businesses.stream()
                .filter(business -> business.occupancyHistory().stream()
                        .anyMatch(occupancy -> occupancy.propertyId().equals(propertyId)))
                .toList();
    }

    public List<Business> findByProperty(String propertyId) {
        return findByProperty(new CommercialPropertyId(propertyId));
    }

    public List<Business> findBySettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return businesses.stream()
                .filter(business -> business.primarySettlementId().equals(settlementId))
                .toList();
    }

    public List<Business> findByBusinessType(BusinessType type) {
        Objects.requireNonNull(type, "type");
        return businesses.stream()
                .filter(business -> business.businessType() == type)
                .toList();
    }

    public List<Business> findByStatus(BusinessStatus status) {
        Objects.requireNonNull(status, "status");
        return businesses.stream()
                .filter(business -> business.status() == status)
                .toList();
    }

    public List<Business> findByReputation(BusinessReputation reputation) {
        Objects.requireNonNull(reputation, "reputation");
        return businesses.stream()
                .filter(business -> business.reputation() == reputation)
                .toList();
    }

    public List<Business> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return businesses.stream()
                .filter(business -> searchableText(business).contains(normalizedQuery))
                .toList();
    }

    private static Business validateReferences(
            Business business,
            Region region,
            Map<String, Settlement> settlementsById,
            Map<CommercialPropertyId, CommercialProperty> propertiesById,
            ManufacturerRegistry manufacturers
    ) {
        Objects.requireNonNull(business, "business");
        if (!business.primaryRegionId().equals(region.id())) {
            throw new IllegalArgumentException("Business " + business.id().value()
                    + " references unknown primary region: " + business.primaryRegionId());
        }
        if (!settlementsById.containsKey(business.primarySettlementId())) {
            throw new IllegalArgumentException("Business " + business.id().value()
                    + " references unknown primary settlement: " + business.primarySettlementId());
        }
        CommercialProperty primaryProperty = propertiesById.get(business.primaryPropertyId());
        if (primaryProperty == null) {
            throw new IllegalArgumentException("Business " + business.id().value()
                    + " references unknown primary property: " + business.primaryPropertyId().value());
        }
        if (!primaryProperty.settlementId().equals(business.primarySettlementId())) {
            throw new IllegalArgumentException("Business " + business.id().value()
                    + " primary property is not in the primary settlement");
        }
        for (CommercialPropertyId propertyId : business.associatedCommercialPropertyIds()) {
            if (!propertiesById.containsKey(propertyId)) {
                throw new IllegalArgumentException("Business " + business.id().value()
                        + " references unknown commercial property: " + propertyId.value());
            }
        }
        for (BusinessOccupancy occupancy : business.occupancyHistory()) {
            CommercialProperty property = propertiesById.get(occupancy.propertyId());
            if (property == null) {
                throw new IllegalArgumentException("Business " + business.id().value()
                        + " occupancy references unknown commercial property: " + occupancy.propertyId().value());
            }
            if (occupancy.startYear() < property.constructionYear()) {
                throw new IllegalArgumentException("Business " + business.id().value()
                        + " occupancy starts before property construction: " + occupancy.propertyId().value());
            }
        }
        for (String manufacturerId : business.preferredManufacturerIds()) {
            if (!manufacturers.contains(manufacturerId)) {
                throw new IllegalArgumentException("Business " + business.id().value()
                        + " references unknown preferred manufacturer: " + manufacturerId);
            }
        }
        return business;
    }

    private static void rejectDuplicateIds(List<Business> businesses) {
        Set<BusinessId> duplicates = businesses.stream()
                .map(Business::id)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate business ids: " + duplicates);
        }
    }

    private static void rejectDuplicateNamesWithinSettlement(List<Business> businesses) {
        Map<String, List<Business>> bySettlement = businesses.stream()
                .collect(Collectors.groupingBy(Business::primarySettlementId));
        for (Map.Entry<String, List<Business>> entry : bySettlement.entrySet()) {
            Set<String> duplicates = entry.getValue().stream()
                    .map(Business::displayName)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .filter(nameEntry -> nameEntry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (!duplicates.isEmpty()) {
                throw new IllegalArgumentException("Duplicate business names in settlement "
                        + entry.getKey() + ": " + duplicates);
            }
        }
    }

    private static void rejectInvalidRelationships(List<Business> businesses) {
        Set<BusinessId> businessIds = businesses.stream()
                .map(Business::id)
                .collect(Collectors.toUnmodifiableSet());
        Map<BusinessId, Business> byId = new HashMap<>();
        for (Business business : businesses) {
            byId.put(business.id(), business);
        }
        for (Business business : businesses) {
            for (BusinessRelationship relationship : business.relationships()) {
                if (relationship.relatedBusinessId().equals(business.id())) {
                    throw new IllegalArgumentException("Business relationship must not reference itself: " + business.id().value());
                }
                if (!businessIds.contains(relationship.relatedBusinessId())) {
                    throw new IllegalArgumentException("Business " + business.id().value()
                            + " references unknown related business: " + relationship.relatedBusinessId().value());
                }
            }
        }
    }

    private static String searchableText(Business business) {
        return normalize(String.join(" ",
                business.id().value(),
                business.displayName(),
                business.businessType().serializedName(),
                business.status().serializedName(),
                business.reputation().serializedName(),
                business.primarySettlementId(),
                business.primaryRegionId(),
                business.historicalSummary(),
                String.join(" ", business.preferredManufacturerIds())
        ));
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT);
    }
}
