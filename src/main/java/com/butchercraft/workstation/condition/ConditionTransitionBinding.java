package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.projection.WorkstationOperatingStateReference;
import java.util.Objects;
import java.util.Optional;

public record ConditionTransitionBinding(Optional<WorkstationOperatingStateReference> previous,
        WorkstationOperatingStateReference successor, long tick) {
    public ConditionTransitionBinding {
        previous = Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(successor, "successor");
        if (tick < 0 || previous.filter(prior -> !prior.workstationInstanceIdentity().equals(
                successor.workstationInstanceIdentity()) || prior.revision() >= successor.revision()).isPresent()) {
            throw new IllegalArgumentException("Invalid condition/operating transition binding");
        }
    }

    public String identity() {
        ConditionDigest digest = new ConditionDigest("butchercraft:condition_operating_transition/v1")
                .add(previous.isPresent());
        previous.ifPresent(value -> add(digest, value));
        add(digest, successor);
        return digest.add(tick).finish();
    }

    private static void add(ConditionDigest digest, WorkstationOperatingStateReference value) {
        digest.add(value.workstationInstanceIdentity()).add(value.revision()).add(value.state()).add(value.contentDigest());
    }
}
