package com.butchercraft.world.economy.actor;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.workforce.WorkforceDefinition;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import com.butchercraft.world.workforce.WorkforceRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EconomicActorManager {
    private EconomicActorRegistry registry;
    private final Map<ActorId, EconomicActorRuntime> runtimes = new LinkedHashMap<>();

    public EconomicActorManager(EconomicActorRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        registry.definitions().forEach(definition -> runtimes.put(
                definition.id(),
                EconomicActorRuntime.available(definition.id())
        ));
    }

    public synchronized EconomicActorDefinition register(EconomicActorDefinition definition) {
        registry = registry.withDefinition(definition);
        runtimes.put(definition.id(), EconomicActorRuntime.available(definition.id()));
        return definition;
    }

    public synchronized Optional<EconomicActorDefinition> find(ActorId actorId) {
        return registry.find(actorId);
    }

    public synchronized List<EconomicActorDefinition> findByCapability(ActorCapability capability) {
        return registry.findByCapability(capability);
    }

    public synchronized List<EconomicActorDefinition> actorsForGood(GoodId goodId) {
        return registry.findByGood(goodId);
    }

    public synchronized List<EconomicActorDefinition> actorsForGoodRole(GoodId goodId, GoodRole goodRole) {
        return registry.findByGoodRole(goodId, goodRole);
    }

    public synchronized List<ActorRelationship> relationshipsFor(ActorId actorId) {
        return registry.relationshipsFor(actorId);
    }

    public synchronized List<EconomicActorDefinition> dependenciesOf(ActorId actorId) {
        return registry.dependenciesOf(actorId);
    }

    public synchronized Optional<EconomicActorRuntime> runtimeFor(ActorId actorId) {
        return Optional.ofNullable(runtimes.get(Objects.requireNonNull(actorId, "actorId")));
    }

    public synchronized EconomicActorRuntime requireRuntime(ActorId actorId) {
        return runtimeFor(actorId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown economic actor runtime: " + actorId.value()
        ));
    }

    public synchronized EconomicActorRegistry registry() {
        return registry;
    }

    public synchronized void validate() {
        registry.validate();
        if (!runtimes.keySet().equals(registry.stream().map(EconomicActorDefinition::id)
                .collect(Collectors.toSet()))) {
            throw new IllegalArgumentException("Economic actor runtime set does not match actor registry");
        }
        runtimes.values().forEach(EconomicActorRuntime::validate);
    }

    public synchronized void validateRuntimeAssignments(
            BusinessRuntimeRegistry businessRuntimeRegistry,
            WorkforceRegistry workforceRegistry
    ) {
        Objects.requireNonNull(businessRuntimeRegistry, "businessRuntimeRegistry");
        Objects.requireNonNull(workforceRegistry, "workforceRegistry");
        for (EconomicActorRuntime runtime : runtimes.values()) {
            runtime.validate();
            Optional<BusinessId> businessId = runtime.assignedBusinessRuntime();
            businessId.ifPresent(id -> {
                if (!businessRuntimeRegistry.contains(id)) {
                    throw new IllegalArgumentException("Economic actor references unknown business runtime: " + id.value());
                }
            });
            Optional<WorkforceDefinitionId> workforceId = runtime.assignedWorkforce();
            workforceId.ifPresent(id -> {
                WorkforceDefinition definition = workforceRegistry.find(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Economic actor references unknown workforce: " + id.value()
                        ));
                businessId.ifPresent(idBusiness -> {
                    if (!definition.businessId().equals(idBusiness)) {
                        throw new IllegalArgumentException("Economic actor business and workforce assignments do not match: "
                                + runtime.actorId().value());
                    }
                });
            });
        }
    }
}
