package com.butchercraft.world.ownership;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnershipValidationTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(111L);
    private final Family family = identity.families().getFirst();
    private final PersonIdentity person = identity.historicalPersons().getFirst();
    private final OwnershipEntity entity = identity.ownershipEntities().getFirst();
    private final OwnershipHistory history = identity.ownershipHistories().getFirst();

    @Test
    void ownershipModelsRejectInvalidCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> new FamilyId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new PersonId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipEntityId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipShare(0));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipShare(10_001));
        assertThrows(IllegalArgumentException.class, () -> familyWithLegacyScore(101));
        assertThrows(IllegalArgumentException.class, () -> personWithDeathYear(OptionalInt.of(person.birthYear() - 1)));
    }

    @Test
    void ownershipHistoryRejectsBrokenChronologyAndExcessShare() {
        OwnershipEntityId secondEntityId = new OwnershipEntityId(entity.id().value() + "_second");
        assertThrows(IllegalArgumentException.class, () -> new OwnershipHistory(history.businessId(), List.of(
                new OwnershipRecord(entity.id(), history.businessId(), new OwnershipShare(6_000), 1950, OptionalInt.empty(), OwnershipAcquisitionMethod.FOUNDED, "First owner."),
                new OwnershipRecord(secondEntityId, history.businessId(), new OwnershipShare(5_000), 1950, OptionalInt.empty(), OwnershipAcquisitionMethod.PARTNERSHIP, "Second owner.")
        )));
        assertThrows(IllegalArgumentException.class, () -> new OwnershipHistory(history.businessId(), List.of(
                new OwnershipRecord(entity.id(), history.businessId(), new OwnershipShare(10_000), 1950, OptionalInt.of(1960), OwnershipAcquisitionMethod.FOUNDED, "First owner."),
                new OwnershipRecord(entity.id(), history.businessId(), new OwnershipShare(10_000), 1960, OptionalInt.of(1970), OwnershipAcquisitionMethod.PURCHASED, "Overlapping owner.")
        )));
    }

    @Test
    void registriesRejectDuplicateIdsAndInvalidReferences() {
        Family invalidSettlement = new Family(
                new FamilyId(family.id().value() + "_invalid"),
                "Invalid",
                "missing_settlement",
                "Invalid family record. It references a missing settlement.",
                50,
                FamilyReputation.ORDINARY,
                1900
        );
        PersonIdentity invalidFamilyPerson = new PersonIdentity(
                new PersonId(person.id().value() + "_invalid"),
                "Invalid Person",
                1900,
                OptionalInt.empty(),
                new FamilyId("missing_family"),
                "Invalid person record. It references a missing family."
        );
        OwnershipEntity invalidFamilyEntity = ownershipEntityWithFamily(new FamilyId("missing_family"));
        OwnershipHistory invalidBusinessHistory = new OwnershipHistory(
                new BusinessId("missing_business"),
                List.of(new OwnershipRecord(entity.id(), new BusinessId("missing_business"), new OwnershipShare(10_000), 1950, OptionalInt.empty(), OwnershipAcquisitionMethod.FOUNDED, "Missing business."))
        );
        OwnershipHistory invalidEntityHistory = new OwnershipHistory(
                history.businessId(),
                List.of(new OwnershipRecord(new OwnershipEntityId("missing_entity"), history.businessId(), new OwnershipShare(10_000), 1950, OptionalInt.empty(), OwnershipAcquisitionMethod.FOUNDED, "Missing owner."))
        );

        assertThrows(IllegalArgumentException.class, () -> FamilyRegistry.of(List.of(family, family), identity.historicalPersons(), identity.settlements()));
        assertThrows(IllegalArgumentException.class, () -> FamilyRegistry.of(List.of(invalidSettlement), identity.historicalPersons(), identity.settlements()));
        assertThrows(IllegalArgumentException.class, () -> FamilyRegistry.of(identity.families(), List.of(invalidFamilyPerson), identity.settlements()));
        assertThrows(IllegalArgumentException.class, () -> OwnershipRegistry.of(List.of(entity, entity), identity.ownershipHistories(), identity.families(), identity.historicalPersons(), identity.businesses()));
        assertThrows(IllegalArgumentException.class, () -> OwnershipRegistry.of(List.of(invalidFamilyEntity), identity.ownershipHistories(), identity.families(), identity.historicalPersons(), identity.businesses()));
        assertThrows(IllegalArgumentException.class, () -> OwnershipRegistry.of(identity.ownershipEntities(), List.of(invalidBusinessHistory), identity.families(), identity.historicalPersons(), identity.businesses()));
        assertThrows(IllegalArgumentException.class, () -> OwnershipRegistry.of(identity.ownershipEntities(), List.of(invalidEntityHistory), identity.families(), identity.historicalPersons(), identity.businesses()));
        assertThrows(IllegalArgumentException.class, () -> OwnershipRegistry.of(identity.ownershipEntities(), identity.ownershipHistories().subList(1, identity.ownershipHistories().size()), identity.families(), identity.historicalPersons(), identity.businesses()));
    }

    @Test
    void worldIdentityRejectsInvalidOwnershipSnapshot() {
        OwnershipHistory invalidEntityHistory = new OwnershipHistory(
                history.businessId(),
                List.of(new OwnershipRecord(new OwnershipEntityId("missing_entity"), history.businessId(), new OwnershipShare(10_000), 1950, OptionalInt.empty(), OwnershipAcquisitionMethod.FOUNDED, "Missing owner."))
        );

        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                identity.id(),
                identity.worldSeed(),
                identity.region(),
                identity.counties(),
                identity.commercialProperties(),
                identity.businesses(),
                identity.families(),
                identity.historicalPersons(),
                identity.ownershipEntities(),
                List.of(invalidEntityHistory)
        ));
    }

    private Family familyWithLegacyScore(int legacyScore) {
        return new Family(
                family.id(),
                family.surname(),
                family.foundingSettlementId(),
                family.historicalSummary(),
                legacyScore,
                family.reputation(),
                family.approximateFoundingYear()
        );
    }

    private PersonIdentity personWithDeathYear(OptionalInt deathYear) {
        return new PersonIdentity(
                person.id(),
                person.fullName(),
                person.birthYear(),
                deathYear,
                person.primaryFamilyId(),
                person.historicalSummary()
        );
    }

    private OwnershipEntity ownershipEntityWithFamily(FamilyId familyId) {
        return new OwnershipEntity(
                new OwnershipEntityId(entity.id().value() + "_invalid"),
                "Invalid Entity",
                OwnershipEntityType.FAMILY,
                entity.establishedYear(),
                Optional.of(familyId),
                Optional.empty(),
                "Invalid ownership entity. It references a missing family.",
                List.of()
        );
    }
}
