package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ActorRelationship(
        GoodId goodId,
        GoodRole goodRole,
        Set<IndustryId> supportedIndustryIds,
        Optional<ActorId> dependsOnActorId,
        int schemaVersion
) implements Comparable<ActorRelationship> {
    public ActorRelationship {
        goodId = Objects.requireNonNull(goodId, "goodId");
        goodRole = Objects.requireNonNull(goodRole, "goodRole");
        supportedIndustryIds = copySupportedIndustries(supportedIndustryIds);
        dependsOnActorId = Objects.requireNonNull(dependsOnActorId, "dependsOnActorId");
        if (schemaVersion != EconomicActorSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported actor relationship schema version: " + schemaVersion);
        }
    }

    public ActorRelationship(GoodId goodId, GoodRole goodRole) {
        this(goodId, goodRole, Set.of(), Optional.empty(), EconomicActorSchema.CURRENT_VERSION);
    }

    public static ActorRelationship of(GoodId goodId, GoodRole goodRole) {
        return new ActorRelationship(goodId, goodRole);
    }

    public static ActorRelationship dependingOn(GoodId goodId, GoodRole goodRole, ActorId actorId) {
        return new ActorRelationship(
                goodId,
                goodRole,
                Set.of(),
                Optional.of(Objects.requireNonNull(actorId, "actorId")),
                EconomicActorSchema.CURRENT_VERSION
        );
    }

    public static ActorRelationship supportingIndustries(
            GoodId goodId,
            GoodRole goodRole,
            Collection<IndustryId> industryIds
    ) {
        return new ActorRelationship(
                goodId,
                goodRole,
                Set.copyOf(Objects.requireNonNull(industryIds, "industryIds")),
                Optional.empty(),
                EconomicActorSchema.CURRENT_VERSION
        );
    }

    @Override
    public int compareTo(ActorRelationship other) {
        Objects.requireNonNull(other, "other");
        int goodComparison = goodId.compareTo(other.goodId);
        if (goodComparison != 0) {
            return goodComparison;
        }
        int roleComparison = goodRole.serializedName().compareTo(other.goodRole.serializedName());
        if (roleComparison != 0) {
            return roleComparison;
        }
        int industryComparison = compareIndustries(supportedIndustryIds, other.supportedIndustryIds);
        if (industryComparison != 0) {
            return industryComparison;
        }
        return dependsOnActorId.map(ActorId::value).orElse("")
                .compareTo(other.dependsOnActorId.map(ActorId::value).orElse(""));
    }

    private static Set<IndustryId> copySupportedIndustries(Set<IndustryId> source) {
        Objects.requireNonNull(source, "supportedIndustryIds");
        return Collections.unmodifiableSet(new LinkedHashSet<>(source.stream()
                .map(industryId -> Objects.requireNonNull(industryId, "supportedIndustryId"))
                .sorted()
                .toList()));
    }

    private static int compareIndustries(Set<IndustryId> first, Set<IndustryId> second) {
        String firstValue = first.stream().map(IndustryId::value).reduce((left, right) -> left + "\u0000" + right).orElse("");
        String secondValue = second.stream().map(IndustryId::value).reduce((left, right) -> left + "\u0000" + right).orElse("");
        return firstValue.compareTo(secondValue);
    }
}
