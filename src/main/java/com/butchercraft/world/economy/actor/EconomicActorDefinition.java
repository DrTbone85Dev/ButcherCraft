package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EconomicActorDefinition(
        ActorId id,
        String displayName,
        ActorType actorType,
        IndustryId industryId,
        Set<ActorCapability> capabilities,
        List<ActorRelationship> relationships,
        int schemaVersion
) {
    public EconomicActorDefinition {
        id = Objects.requireNonNull(id, "id");
        displayName = requireDisplayName(displayName);
        actorType = Objects.requireNonNull(actorType, "actorType");
        industryId = Objects.requireNonNull(industryId, "industryId");
        capabilities = copyCapabilities(capabilities);
        relationships = copyRelationships(relationships);
        if (schemaVersion != EconomicActorSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported economic actor schema version: " + schemaVersion);
        }
        validateRelationshipCapabilities(id, capabilities, relationships);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasCapability(ActorCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    public List<ActorRelationship> relationshipsFor(GoodId goodId) {
        Objects.requireNonNull(goodId, "goodId");
        return relationships.stream().filter(relationship -> relationship.goodId().equals(goodId)).toList();
    }

    private static String requireDisplayName(String displayName) {
        String normalized = Objects.requireNonNull(displayName, "displayName").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Economic actor display name cannot be blank");
        }
        return normalized;
    }

    private static Set<ActorCapability> copyCapabilities(Set<ActorCapability> source) {
        Objects.requireNonNull(source, "capabilities");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Economic actor must declare at least one capability");
        }
        EnumSet<ActorCapability> copied = EnumSet.noneOf(ActorCapability.class);
        for (ActorCapability capability : source) {
            copied.add(Objects.requireNonNull(capability, "capability"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static List<ActorRelationship> copyRelationships(List<ActorRelationship> source) {
        Objects.requireNonNull(source, "relationships");
        List<ActorRelationship> copied = source.stream()
                .map(relationship -> Objects.requireNonNull(relationship, "relationship"))
                .sorted()
                .toList();
        Set<ActorRelationship> unique = new HashSet<>(copied);
        if (unique.size() != copied.size()) {
            throw new IllegalArgumentException("Economic actor contains duplicate relationships");
        }
        return List.copyOf(copied);
    }

    private static void validateRelationshipCapabilities(
            ActorId actorId,
            Set<ActorCapability> capabilities,
            List<ActorRelationship> relationships
    ) {
        for (ActorRelationship relationship : relationships) {
            if (!relationship.goodRole().supports(capabilities)) {
                throw new IllegalArgumentException("Economic actor capability does not support relationship role: "
                        + actorId.value() + "/" + relationship.goodRole().serializedName());
            }
        }
    }

    public static final class Builder {
        private ActorId id;
        private String displayName;
        private ActorType actorType;
        private IndustryId industryId;
        private final Set<ActorCapability> capabilities = EnumSet.noneOf(ActorCapability.class);
        private final List<ActorRelationship> relationships = new ArrayList<>();
        private int schemaVersion = EconomicActorSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(String id) {
            return id(ActorId.of(id));
        }

        public Builder id(ActorId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder actorType(ActorType actorType) {
            this.actorType = Objects.requireNonNull(actorType, "actorType");
            return this;
        }

        public Builder industryId(String industryId) {
            return industryId(IndustryId.of(industryId));
        }

        public Builder industryId(IndustryId industryId) {
            this.industryId = Objects.requireNonNull(industryId, "industryId");
            return this;
        }

        public Builder capability(ActorCapability capability) {
            capabilities.add(Objects.requireNonNull(capability, "capability"));
            return this;
        }

        public Builder capabilities(Set<ActorCapability> capabilities) {
            this.capabilities.clear();
            this.capabilities.addAll(Objects.requireNonNull(capabilities, "capabilities"));
            return this;
        }

        public Builder relationship(ActorRelationship relationship) {
            relationships.add(Objects.requireNonNull(relationship, "relationship"));
            return this;
        }

        public Builder relationships(List<ActorRelationship> relationships) {
            this.relationships.clear();
            this.relationships.addAll(Objects.requireNonNull(relationships, "relationships"));
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public EconomicActorDefinition build() {
            return new EconomicActorDefinition(
                    id,
                    displayName,
                    actorType,
                    industryId,
                    capabilities,
                    relationships,
                    schemaVersion
            );
        }
    }
}
