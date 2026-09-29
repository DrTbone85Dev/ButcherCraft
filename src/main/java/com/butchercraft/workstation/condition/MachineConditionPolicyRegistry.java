package com.butchercraft.workstation.condition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable lookup. Retained policies are explicit; changing a default never rewrites settled history. */
public final class MachineConditionPolicyRegistry {
    private final Map<String, MachineConditionPolicy> policies;
    private final Map<String, String> defaults;

    public MachineConditionPolicyRegistry(Collection<MachineConditionPolicy> policies, Map<String, String> defaults) {
        Map<String, MachineConditionPolicy> indexed = new LinkedHashMap<>();
        for (MachineConditionPolicy policy : policies) {
            if (indexed.putIfAbsent(policy.identity(), policy) != null) {
                throw new IllegalArgumentException("Duplicate condition policy identity");
            }
        }
        this.policies = Map.copyOf(indexed);
        this.defaults = Map.copyOf(defaults);
        this.defaults.forEach((type, identity) -> require(identity, type));
    }

    public Optional<MachineConditionPolicy> forMachine(String type) {
        return Optional.ofNullable(defaults.get(type)).map(identity -> require(identity, type));
    }

    public MachineConditionPolicy require(String identity, String type) {
        MachineConditionPolicy policy = policies.get(identity);
        if (policy == null || !policy.machineType().equals(type)) {
            throw new IllegalArgumentException("Unknown or wrong-machine condition policy: " + identity);
        }
        return policy;
    }

    public String identity() {
        ConditionDigest digest = new ConditionDigest("butchercraft:condition_policy_registry/v1");
        policies.keySet().stream().sorted().forEach(digest::add);
        defaults.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> digest.add(entry.getKey()).add(entry.getValue()));
        return "butchercraft:condition_policy_registry/v1/" + digest.finish().substring(7);
    }
}
