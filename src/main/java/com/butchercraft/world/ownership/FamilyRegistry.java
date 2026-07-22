package com.butchercraft.world.ownership;

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

public final class FamilyRegistry {
    private final List<Family> families;
    private final List<PersonIdentity> people;
    private final Map<FamilyId, Family> familiesById;
    private final Map<PersonId, PersonIdentity> peopleById;

    private FamilyRegistry(
            List<Family> families,
            List<PersonIdentity> people,
            Map<FamilyId, Family> familiesById,
            Map<PersonId, PersonIdentity> peopleById
    ) {
        this.families = families;
        this.people = people;
        this.familiesById = familiesById;
        this.peopleById = peopleById;
    }

    public static FamilyRegistry of(
            Collection<Family> families,
            Collection<PersonIdentity> people,
            Collection<Settlement> settlements
    ) {
        Objects.requireNonNull(families, "families");
        Objects.requireNonNull(people, "people");
        Objects.requireNonNull(settlements, "settlements");
        if (families.isEmpty()) {
            throw new IllegalArgumentException("Family registry must contain at least one family");
        }
        if (people.isEmpty()) {
            throw new IllegalArgumentException("Family registry must contain at least one historical person");
        }
        Set<String> settlementIds = settlements.stream()
                .map(Settlement::id)
                .collect(Collectors.toUnmodifiableSet());
        if (settlementIds.isEmpty()) {
            throw new IllegalArgumentException("Family registry requires at least one settlement");
        }

        List<Family> deterministicFamilies = families.stream()
                .map(family -> validateSettlementReference(family, settlementIds))
                .sorted(Comparator.comparing(family -> family.id().value()))
                .toList();
        rejectDuplicates(deterministicFamilies, Family::id, "family id");

        Map<FamilyId, Family> byFamilyId = deterministicFamilies.stream()
                .collect(Collectors.toUnmodifiableMap(Family::id, Function.identity()));
        List<PersonIdentity> deterministicPeople = people.stream()
                .map(person -> validateFamilyReference(person, byFamilyId.keySet()))
                .sorted(Comparator.comparing(person -> person.id().value()))
                .toList();
        rejectDuplicates(deterministicPeople, PersonIdentity::id, "person id");

        Map<PersonId, PersonIdentity> byPersonId = deterministicPeople.stream()
                .collect(Collectors.toUnmodifiableMap(PersonIdentity::id, Function.identity()));
        return new FamilyRegistry(
                List.copyOf(deterministicFamilies),
                List.copyOf(deterministicPeople),
                byFamilyId,
                byPersonId
        );
    }

    public boolean containsFamily(FamilyId id) {
        return familiesById.containsKey(id);
    }

    public Optional<Family> findFamily(FamilyId id) {
        return Optional.ofNullable(familiesById.get(id));
    }

    public boolean containsPerson(PersonId id) {
        return peopleById.containsKey(id);
    }

    public Optional<PersonIdentity> findPerson(PersonId id) {
        return Optional.ofNullable(peopleById.get(id));
    }

    public int familyCount() {
        return families.size();
    }

    public int personCount() {
        return people.size();
    }

    public List<Family> families() {
        return families;
    }

    public List<PersonIdentity> people() {
        return people;
    }

    public Stream<Family> familyStream() {
        return families.stream();
    }

    public Stream<PersonIdentity> personStream() {
        return people.stream();
    }

    public List<Family> findBySettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return families.stream()
                .filter(family -> family.foundingSettlementId().equals(settlementId))
                .toList();
    }

    public List<Family> findByReputation(FamilyReputation reputation) {
        Objects.requireNonNull(reputation, "reputation");
        return families.stream()
                .filter(family -> family.reputation() == reputation)
                .toList();
    }

    public List<Family> searchFamilies(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        return families.stream()
                .filter(family -> searchableText(family).contains(normalized))
                .toList();
    }

    private static Family validateSettlementReference(Family family, Set<String> settlementIds) {
        Objects.requireNonNull(family, "family");
        if (!settlementIds.contains(family.foundingSettlementId())) {
            throw new IllegalArgumentException("Family " + family.id().value()
                    + " references unknown founding settlement: " + family.foundingSettlementId());
        }
        return family;
    }

    private static PersonIdentity validateFamilyReference(PersonIdentity person, Set<FamilyId> familyIds) {
        Objects.requireNonNull(person, "person");
        if (!familyIds.contains(person.primaryFamilyId())) {
            throw new IllegalArgumentException("Person " + person.id().value()
                    + " references unknown family: " + person.primaryFamilyId().value());
        }
        return person;
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

    private static String searchableText(Family family) {
        return normalize(String.join(" ",
                family.id().value(),
                family.surname(),
                family.foundingSettlementId(),
                family.reputation().serializedName(),
                family.historicalSummary()
        ));
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT);
    }
}
