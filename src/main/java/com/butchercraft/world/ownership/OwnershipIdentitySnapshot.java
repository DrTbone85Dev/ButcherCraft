package com.butchercraft.world.ownership;

import java.util.List;
import java.util.Objects;

public record OwnershipIdentitySnapshot(
        List<Family> families,
        List<PersonIdentity> historicalPersons,
        List<OwnershipEntity> ownershipEntities,
        List<OwnershipHistory> ownershipHistories
) {
    public OwnershipIdentitySnapshot {
        families = List.copyOf(Objects.requireNonNull(families, "families"));
        historicalPersons = List.copyOf(Objects.requireNonNull(historicalPersons, "historicalPersons"));
        ownershipEntities = List.copyOf(Objects.requireNonNull(ownershipEntities, "ownershipEntities"));
        ownershipHistories = List.copyOf(Objects.requireNonNull(ownershipHistories, "ownershipHistories"));
    }
}
