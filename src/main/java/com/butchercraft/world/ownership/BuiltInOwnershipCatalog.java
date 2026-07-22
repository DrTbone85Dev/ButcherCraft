package com.butchercraft.world.ownership;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessOwnershipType;
import com.butchercraft.world.business.BusinessReputation;
import com.butchercraft.world.business.BusinessStatus;
import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentityDeterminism;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class BuiltInOwnershipCatalog {
    private static final long TYPE_SALT = 0x0ec74a8d95b3f612L;
    private static final long FAMILY_SALT = 0x785390ad2b1c64efL;
    private static final long PERSON_SALT = 0x513d0e4a69cb2781L;
    private static final long REPUTATION_SALT = 0x1fae439c70d526b8L;
    private static final long LEGACY_SCORE_SALT = 0x628e9cd13705a4b2L;
    private static final long YEAR_SALT = 0x744a91be038fd2c5L;

    private BuiltInOwnershipCatalog() {
    }

    public static OwnershipIdentitySnapshot generate(
            long worldSeed,
            Region region,
            List<Settlement> settlements,
            List<Business> businesses
    ) {
        Map<String, Settlement> settlementsById = settlements.stream()
                .collect(Collectors.toUnmodifiableMap(Settlement::id, settlement -> settlement));
        List<Family> families = new ArrayList<>();
        List<PersonIdentity> people = new ArrayList<>();
        List<OwnershipEntity> ownershipEntities = new ArrayList<>();
        List<OwnershipHistory> ownershipHistories = new ArrayList<>();

        businesses.stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .forEach(business -> {
                    Settlement settlement = settlementsById.get(business.primarySettlementId());
                    if (settlement == null) {
                        throw new IllegalArgumentException("Business references unknown settlement: " + business.primarySettlementId());
                    }
                    GeneratedOwnership generated = generateForBusiness(worldSeed, region, settlement, business);
                    families.add(generated.family());
                    people.add(generated.person());
                    ownershipEntities.add(generated.ownershipEntity());
                    ownershipHistories.add(generated.ownershipHistory());
                });

        return new OwnershipIdentitySnapshot(
                families.stream().sorted(Comparator.comparing(family -> family.id().value())).toList(),
                people.stream().sorted(Comparator.comparing(person -> person.id().value())).toList(),
                ownershipEntities.stream().sorted(Comparator.comparing(entity -> entity.id().value())).toList(),
                ownershipHistories.stream().sorted(Comparator.comparing(history -> history.businessId().value())).toList()
        );
    }

    private static GeneratedOwnership generateForBusiness(
            long worldSeed,
            Region region,
            Settlement settlement,
            Business business
    ) {
        String stem = business.id().value();
        String surname = surname(worldSeed, stem, business);
        FamilyId familyId = new FamilyId(stem + "_family");
        PersonId personId = new PersonId(stem + "_founder");
        OwnershipEntityId entityId = new OwnershipEntityId(stem + "_owner");
        OwnershipEntityType entityType = ownershipEntityType(worldSeed, business);
        FamilyReputation familyReputation = familyReputation(worldSeed, business, stem);
        int familyFoundingYear = familyFoundingYear(worldSeed, business, stem);
        int personBirthYear = personBirthYear(worldSeed, business, stem);

        Family family = new Family(
                familyId,
                surname,
                settlement.id(),
                familySummary(region, settlement, surname, business, familyFoundingYear, familyReputation),
                legacyScore(worldSeed, stem, familyReputation),
                familyReputation,
                familyFoundingYear
        );
        PersonIdentity person = new PersonIdentity(
                personId,
                fullName(worldSeed, stem, surname),
                personBirthYear,
                deathYear(worldSeed, stem, personBirthYear, business.status()),
                familyId,
                personSummary(settlement, surname, business, personBirthYear)
        );
        OwnershipEntity entity = new OwnershipEntity(
                entityId,
                ownershipEntityName(entityType, settlement, business, surname, person.fullName()),
                entityType,
                business.foundingYear(),
                familyReference(entityType, familyId),
                personReference(entityType, personId),
                entitySummary(entityType, settlement, business),
                List.of()
        );
        OwnershipHistory history = new OwnershipHistory(
                business.id(),
                List.of(new OwnershipRecord(
                        entityId,
                        business.id(),
                        new OwnershipShare(OwnershipShare.FULL_OWNERSHIP),
                        business.foundingYear(),
                        business.occupancyHistory().getFirst().endYear(),
                        acquisitionMethod(worldSeed, business, stem),
                        ownershipNotes(entityType, business.status())
                ))
        );
        return new GeneratedOwnership(family, person, entity, history);
    }

    private static OwnershipEntityType ownershipEntityType(long worldSeed, Business business) {
        List<OwnershipEntityType> options = switch (business.ownershipModel().ownershipType()) {
            case FAMILY -> List.of(OwnershipEntityType.FAMILY, OwnershipEntityType.INDIVIDUAL, OwnershipEntityType.PARTNERSHIP, OwnershipEntityType.ESTATE);
            case INDEPENDENT_OPERATOR -> List.of(OwnershipEntityType.INDIVIDUAL, OwnershipEntityType.PARTNERSHIP, OwnershipEntityType.FAMILY);
            case COOPERATIVE -> List.of(OwnershipEntityType.COOPERATIVE, OwnershipEntityType.PARTNERSHIP);
            case REGIONAL_COMPANY -> List.of(OwnershipEntityType.CORPORATION, OwnershipEntityType.COOPERATIVE);
            case ESTATE -> List.of(OwnershipEntityType.ESTATE, OwnershipEntityType.FAMILY);
            case BANK_MANAGED -> List.of(OwnershipEntityType.ESTATE, OwnershipEntityType.CORPORATION, OwnershipEntityType.MUNICIPALITY);
        };
        return options.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                TYPE_SALT,
                options.size(),
                business.id().value(),
                business.status().serializedName(),
                business.businessType().serializedName()
        ));
    }

    private static FamilyReputation familyReputation(long worldSeed, Business business, String stem) {
        List<FamilyReputation> options = switch (business.reputation()) {
            case LEGENDARY -> List.of(FamilyReputation.LEGENDARY, FamilyReputation.RESPECTED);
            case EXCELLENT -> List.of(FamilyReputation.RESPECTED, FamilyReputation.ESTABLISHED);
            case GOOD -> List.of(FamilyReputation.RESPECTED, FamilyReputation.ESTABLISHED, FamilyReputation.ORDINARY);
            case AVERAGE -> List.of(FamilyReputation.ESTABLISHED, FamilyReputation.ORDINARY, FamilyReputation.DECLINING);
            case DECLINING -> List.of(FamilyReputation.ORDINARY, FamilyReputation.DECLINING, FamilyReputation.DISGRACED);
            case POOR -> List.of(FamilyReputation.DECLINING, FamilyReputation.DISGRACED);
        };
        return options.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                REPUTATION_SALT,
                options.size(),
                stem,
                business.reputation().serializedName()
        ));
    }

    private static OwnershipAcquisitionMethod acquisitionMethod(long worldSeed, Business business, String stem) {
        List<OwnershipAcquisitionMethod> options = switch (business.status()) {
            case BANKRUPT -> List.of(OwnershipAcquisitionMethod.BANKRUPTCY_PURCHASE, OwnershipAcquisitionMethod.PURCHASED);
            case MERGED -> List.of(OwnershipAcquisitionMethod.MERGER, OwnershipAcquisitionMethod.PARTNERSHIP);
            case RELOCATED -> List.of(OwnershipAcquisitionMethod.PURCHASED, OwnershipAcquisitionMethod.INHERITED);
            case VACANT_RECORD, CLOSED -> List.of(OwnershipAcquisitionMethod.PURCHASED, OwnershipAcquisitionMethod.INHERITED, OwnershipAcquisitionMethod.FOUNDED);
            case OPERATING, SEASONAL -> List.of(OwnershipAcquisitionMethod.FOUNDED, OwnershipAcquisitionMethod.INHERITED, OwnershipAcquisitionMethod.PARTNERSHIP);
        };
        if (business.ownershipModel().ownershipType() == BusinessOwnershipType.BANK_MANAGED) {
            options = List.of(OwnershipAcquisitionMethod.BANKRUPTCY_PURCHASE, OwnershipAcquisitionMethod.GOVERNMENT_TRANSFER);
        }
        return options.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                TYPE_SALT,
                options.size(),
                stem,
                business.status().serializedName(),
                "acquisition"
        ));
    }

    private static Optional<FamilyId> familyReference(OwnershipEntityType type, FamilyId familyId) {
        return switch (type) {
            case FAMILY, INDIVIDUAL, PARTNERSHIP, ESTATE -> Optional.of(familyId);
            case COOPERATIVE, CORPORATION, MUNICIPALITY -> Optional.empty();
        };
    }

    private static Optional<PersonId> personReference(OwnershipEntityType type, PersonId personId) {
        return type == OwnershipEntityType.INDIVIDUAL ? Optional.of(personId) : Optional.empty();
    }

    private static String surname(long worldSeed, String stem, Business business) {
        String firstWord = business.displayName().split(" ")[0].replaceAll("[^A-Za-z]", "");
        if (!firstWord.isBlank() && !firstWord.equalsIgnoreCase("The")) {
            return firstWord;
        }
        return surnames().get(WorldIdentityDeterminism.stableIndex(worldSeed, FAMILY_SALT, surnames().size(), stem));
    }

    private static String fullName(long worldSeed, String stem, String surname) {
        String givenName = givenNames().get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                PERSON_SALT,
                givenNames().size(),
                stem,
                surname
        ));
        return givenName + " " + surname;
    }

    private static int familyFoundingYear(long worldSeed, Business business, String stem) {
        int offset = 12 + WorldIdentityDeterminism.stableIndex(worldSeed, YEAR_SALT, 42, stem, "family");
        return Math.max(1850, business.foundingYear() - offset);
    }

    private static int personBirthYear(long worldSeed, Business business, String stem) {
        int offset = 24 + WorldIdentityDeterminism.stableIndex(worldSeed, YEAR_SALT, 18, stem, "person");
        return Math.max(1850, business.foundingYear() - offset);
    }

    private static OptionalInt deathYear(long worldSeed, String stem, int birthYear, BusinessStatus status) {
        if (status == BusinessStatus.OPERATING || status == BusinessStatus.SEASONAL) {
            return OptionalInt.empty();
        }
        int year = Math.min(2026, birthYear + 63 + WorldIdentityDeterminism.stableIndex(worldSeed, YEAR_SALT, 25, stem, "death"));
        return OptionalInt.of(Math.max(birthYear, year));
    }

    private static int legacyScore(long worldSeed, String stem, FamilyReputation reputation) {
        int base = switch (reputation) {
            case LEGENDARY -> 85;
            case RESPECTED -> 70;
            case ESTABLISHED -> 55;
            case ORDINARY -> 40;
            case DECLINING -> 25;
            case DISGRACED -> 10;
        };
        return Math.min(100, base + WorldIdentityDeterminism.stableIndex(worldSeed, LEGACY_SCORE_SALT, 16, stem));
    }

    private static String ownershipEntityName(
            OwnershipEntityType type,
            Settlement settlement,
            Business business,
            String surname,
            String personName
    ) {
        return switch (type) {
            case INDIVIDUAL -> personName;
            case FAMILY -> surname + " Family";
            case PARTNERSHIP -> surname + " Partnership";
            case COOPERATIVE -> settlement.displayName() + " Producers Cooperative";
            case CORPORATION -> business.displayName() + " Holding Company";
            case ESTATE -> surname + " Estate";
            case MUNICIPALITY -> settlement.displayName() + " Municipal Development Office";
        };
    }

    private static String familySummary(
            Region region,
            Settlement settlement,
            String surname,
            Business business,
            int foundingYear,
            FamilyReputation reputation
    ) {
        return "The " + surname + " family appears in " + settlement.displayName()
                + " commercial records around " + foundingYear + " within the " + region.displayName() + ". "
                + "Their legacy is recorded as " + reputation.serializedName().replace('_', ' ')
                + " through their connection to " + business.displayName() + ".";
    }

    private static String personSummary(Settlement settlement, String surname, Business business, int birthYear) {
        return "Born around " + birthYear + ", this " + surname
                + " family member is preserved as historical ownership identity for " + business.displayName() + ". "
                + "The record is archival only and does not create an NPC in " + settlement.displayName() + ".";
    }

    private static String entitySummary(OwnershipEntityType type, Settlement settlement, Business business) {
        return "This " + type.serializedName().replace('_', ' ')
                + " ownership entity controls the historical record for " + business.displayName() + ". "
                + "It is preserved as identity data for future succession, purchase, and archive systems in "
                + settlement.displayName() + ".";
    }

    private static String ownershipNotes(OwnershipEntityType type, BusinessStatus status) {
        return "Ownership is recorded as " + type.serializedName().replace('_', ' ')
                + " control with business status " + status.serializedName().replace('_', ' ')
                + "; no gameplay, economy, or player ownership is attached.";
    }

    private static List<String> surnames() {
        return List.of("Alder", "Benton", "Carver", "Dawson", "Ellis", "Harlan", "Mercer", "Whitcomb");
    }

    private static List<String> givenNames() {
        return List.of("Clara", "Elias", "Martha", "Samuel", "Nora", "Isaac", "Helen", "Walter", "Ada", "Thomas");
    }

    private record GeneratedOwnership(
            Family family,
            PersonIdentity person,
            OwnershipEntity ownershipEntity,
            OwnershipHistory ownershipHistory
    ) {
    }
}
