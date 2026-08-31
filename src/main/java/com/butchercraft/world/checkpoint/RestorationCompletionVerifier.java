package com.butchercraft.world.checkpoint;

import java.util.List;

@FunctionalInterface
public interface RestorationCompletionVerifier {
    RestorationCompletionVerifier NONE = (context, plans) -> { };

    void verify(
            OwnerNativeRestorationContext context,
            List<OwnerNativeRestorationPlan> plans
    );
}
