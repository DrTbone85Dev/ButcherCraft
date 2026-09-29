package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import java.util.Objects;

public record ConditionFault(
        WorkstationInstanceId instanceId, Type type, String causeEffectIdentity,
        long conditionRevision, String policyIdentity, long authoritativeTick, String identity
) {
    public enum Type { WEAR_LIMIT_REACHED, DRY_RUNNING_DAMAGE, MECHANICAL_DAMAGE }

    public ConditionFault {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(type, "type");
        ConditionDigest.text(causeEffectIdentity, "causeEffectIdentity");
        ConditionDigest.text(policyIdentity, "policyIdentity");
        if (conditionRevision <= 0 || authoritativeTick < 0) throw new IllegalArgumentException("Invalid fault evidence");
        if (!identity.equals(identity(instanceId, type, causeEffectIdentity, conditionRevision, policyIdentity,
                authoritativeTick))) throw new IllegalArgumentException("Condition fault identity mismatch");
    }

    public static ConditionFault create(WorkstationInstanceId instance, Type type, String effect, long revision,
            String policy, long tick) {
        return new ConditionFault(instance, type, effect, revision, policy, tick,
                identity(instance, type, effect, revision, policy, tick));
    }

    private static String identity(WorkstationInstanceId instance, Type type, String effect, long revision,
            String policy, long tick) {
        return ConditionDigest.identity("butchercraft:condition_fault/v1", instance.value(), type.name(), effect,
                Long.toString(revision), policy, Long.toString(tick));
    }
}
