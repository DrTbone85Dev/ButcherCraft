package com.butchercraft.world.property;

import com.butchercraft.world.identity.Settlement;

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

public final class CommercialPropertyRegistry {
    private final List<CommercialProperty> properties;
    private final Map<CommercialPropertyId, CommercialProperty> propertiesById;

    private CommercialPropertyRegistry(
            List<CommercialProperty> properties,
            Map<CommercialPropertyId, CommercialProperty> propertiesById
    ) {
        this.properties = properties;
        this.propertiesById = propertiesById;
    }

    public static CommercialPropertyRegistry of(
            Collection<CommercialProperty> properties,
            Collection<Settlement> settlements
    ) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(settlements, "settlements");
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("Commercial property registry must contain at least one property");
        }
        Set<String> settlementIds = settlements.stream()
                .map(Settlement::id)
                .collect(Collectors.toUnmodifiableSet());
        if (settlementIds.isEmpty()) {
            throw new IllegalArgumentException("Commercial property registry requires at least one settlement");
        }

        List<CommercialProperty> deterministicProperties = properties.stream()
                .map(property -> validateSettlementReference(property, settlementIds))
                .sorted(Comparator.comparing(property -> property.id().value()))
                .toList();
        rejectDuplicateIds(deterministicProperties);
        rejectDuplicateNamesWithinSettlement(deterministicProperties);

        Map<CommercialPropertyId, CommercialProperty> byId = deterministicProperties.stream()
                .collect(Collectors.toUnmodifiableMap(CommercialProperty::id, Function.identity()));
        return new CommercialPropertyRegistry(List.copyOf(deterministicProperties), byId);
    }

    public boolean contains(CommercialPropertyId id) {
        return propertiesById.containsKey(id);
    }

    public Optional<CommercialProperty> find(CommercialPropertyId id) {
        return Optional.ofNullable(propertiesById.get(id));
    }

    public Optional<CommercialProperty> find(String id) {
        return find(new CommercialPropertyId(id));
    }

    public int size() {
        return properties.size();
    }

    public List<CommercialProperty> properties() {
        return properties;
    }

    public Stream<CommercialProperty> stream() {
        return properties.stream();
    }

    public List<CommercialProperty> findBySettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return properties.stream()
                .filter(property -> property.settlementId().equals(settlementId))
                .toList();
    }

    public List<CommercialProperty> findByPropertyType(CommercialPropertyType type) {
        Objects.requireNonNull(type, "type");
        return properties.stream()
                .filter(property -> property.propertyType() == type)
                .toList();
    }

    public List<CommercialProperty> findByStatus(PropertyStatus status) {
        Objects.requireNonNull(status, "status");
        return properties.stream()
                .filter(property -> property.status() == status)
                .toList();
    }

    public List<CommercialProperty> findByCondition(PropertyCondition condition) {
        Objects.requireNonNull(condition, "condition");
        return properties.stream()
                .filter(property -> property.condition() == condition)
                .toList();
    }

    public List<CommercialProperty> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return properties.stream()
                .filter(property -> searchableText(property).contains(normalizedQuery))
                .toList();
    }

    private static CommercialProperty validateSettlementReference(CommercialProperty property, Set<String> settlementIds) {
        Objects.requireNonNull(property, "property");
        if (!settlementIds.contains(property.settlementId())) {
            throw new IllegalArgumentException("Commercial property " + property.id().value()
                    + " references unknown settlement: " + property.settlementId());
        }
        return property;
    }

    private static void rejectDuplicateIds(List<CommercialProperty> properties) {
        Set<CommercialPropertyId> duplicates = properties.stream()
                .map(CommercialProperty::id)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate commercial property ids: " + duplicates);
        }
    }

    private static void rejectDuplicateNamesWithinSettlement(List<CommercialProperty> properties) {
        Map<String, List<CommercialProperty>> bySettlement = properties.stream()
                .collect(Collectors.groupingBy(CommercialProperty::settlementId));
        for (Map.Entry<String, List<CommercialProperty>> entry : bySettlement.entrySet()) {
            Set<String> duplicateNames = entry.getValue().stream()
                    .map(CommercialProperty::displayName)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .filter(nameEntry -> nameEntry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (!duplicateNames.isEmpty()) {
                throw new IllegalArgumentException("Duplicate commercial property names in settlement "
                        + entry.getKey() + ": " + duplicateNames);
            }
        }
    }

    private static String searchableText(CommercialProperty property) {
        return normalize(String.join(" ",
                property.id().value(),
                property.displayName(),
                property.settlementId(),
                property.propertyType().serializedName(),
                property.status().serializedName(),
                property.condition().serializedName(),
                property.history().historicalSummary()
        ));
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT);
    }
}
