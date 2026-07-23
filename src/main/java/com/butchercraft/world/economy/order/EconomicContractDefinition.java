package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.IndustryId;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public record EconomicContractDefinition(
        ContractId id,
        String displayName,
        ContractType type,
        ActorId principalActorId,
        ActorId counterpartyActorId,
        Set<IndustryId> supportedIndustries,
        List<ContractLineDefinition> lines,
        long effectiveSimulationTick,
        OptionalLong expirationSimulationTick,
        ContractSchedule schedule,
        ContractTerms terms,
        ContractMetadata metadata,
        int schemaVersion
) {
    public EconomicContractDefinition {
        id = Objects.requireNonNull(id, "id");
        displayName = DomainValidation.requireText(displayName, "Contract display name", 256);
        type = Objects.requireNonNull(type, "type");
        principalActorId = Objects.requireNonNull(principalActorId, "principalActorId");
        counterpartyActorId = Objects.requireNonNull(counterpartyActorId, "counterpartyActorId");
        supportedIndustries = copyIndustries(supportedIndustries);
        lines = copyLines(lines);
        effectiveSimulationTick = DomainValidation.requireTick(effectiveSimulationTick, "Contract effective tick");
        expirationSimulationTick = Objects.requireNonNull(expirationSimulationTick, "expirationSimulationTick");
        if (expirationSimulationTick.isPresent()) {
            long expiration = expirationSimulationTick.orElseThrow();
            DomainValidation.requireTick(expiration, "Contract expiration tick");
            if (expiration <= effectiveSimulationTick) {
                throw new IllegalArgumentException("Contract expiration tick must follow effective tick");
            }
        }
        schedule = Objects.requireNonNull(schedule, "schedule");
        terms = Objects.requireNonNull(terms, "terms");
        metadata = Objects.requireNonNull(metadata, "metadata");
        schemaVersion = DomainValidation.requireSchema(schemaVersion, "contract definition");
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Set<IndustryId> copyIndustries(Set<IndustryId> source) {
        LinkedHashSet<IndustryId> copied = new LinkedHashSet<>();
        Objects.requireNonNull(source, "supportedIndustries").stream()
                .map(industry -> Objects.requireNonNull(industry, "industry"))
                .sorted()
                .forEach(copied::add);
        return Collections.unmodifiableSet(copied);
    }

    private static List<ContractLineDefinition> copyLines(List<ContractLineDefinition> source) {
        List<ContractLineDefinition> copied = Objects.requireNonNull(source, "lines").stream()
                .map(line -> Objects.requireNonNull(line, "line"))
                .sorted(Comparator.comparing(ContractLineDefinition::id))
                .toList();
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("Contract definition requires at least one line");
        }
        Set<ContractLineId> ids = new HashSet<>();
        Set<SemanticContractLine> semantics = new HashSet<>();
        for (ContractLineDefinition line : copied) {
            if (!ids.add(line.id())) {
                throw new IllegalArgumentException("Duplicate contract line id: " + line.id().value());
            }
            if (!semantics.add(new SemanticContractLine(
                    line.goodId(), line.unitOfMeasure(), line.commitmentPeriod()
            ))) {
                throw new IllegalArgumentException("Duplicate semantic contract line: " + line.goodId().value());
            }
        }
        return List.copyOf(copied);
    }

    private record SemanticContractLine(
            com.butchercraft.world.goods.GoodId goodId,
            com.butchercraft.world.goods.UnitOfMeasure unit,
            CommitmentPeriod period
    ) {
    }

    public static final class Builder {
        private ContractId id;
        private String displayName;
        private ContractType type;
        private ActorId principalActorId;
        private ActorId counterpartyActorId;
        private Set<IndustryId> supportedIndustries = Set.of();
        private List<ContractLineDefinition> lines = List.of();
        private long effectiveSimulationTick;
        private OptionalLong expirationSimulationTick = OptionalLong.empty();
        private ContractSchedule schedule = ContractSchedule.oneTime();
        private ContractTerms terms = ContractTerms.standard();
        private ContractMetadata metadata = ContractMetadata.empty();
        private int schemaVersion = OrderContractSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(ContractId value) { id = value; return this; }
        public Builder displayName(String value) { displayName = value; return this; }
        public Builder type(ContractType value) { type = value; return this; }
        public Builder principalActorId(ActorId value) { principalActorId = value; return this; }
        public Builder counterpartyActorId(ActorId value) { counterpartyActorId = value; return this; }
        public Builder supportedIndustries(Set<IndustryId> value) { supportedIndustries = value; return this; }
        public Builder lines(List<ContractLineDefinition> value) { lines = value; return this; }
        public Builder effectiveSimulationTick(long value) { effectiveSimulationTick = value; return this; }
        public Builder expirationSimulationTick(long value) { expirationSimulationTick = OptionalLong.of(value); return this; }
        public Builder schedule(ContractSchedule value) { schedule = value; return this; }
        public Builder terms(ContractTerms value) { terms = value; return this; }
        public Builder metadata(ContractMetadata value) { metadata = value; return this; }
        public Builder schemaVersion(int value) { schemaVersion = value; return this; }

        public EconomicContractDefinition build() {
            return new EconomicContractDefinition(
                    id, displayName, type, principalActorId, counterpartyActorId, supportedIndustries, lines,
                    effectiveSimulationTick, expirationSimulationTick, schedule, terms, metadata, schemaVersion
            );
        }
    }
}
