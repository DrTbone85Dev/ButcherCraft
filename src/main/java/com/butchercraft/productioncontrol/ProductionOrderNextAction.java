package com.butchercraft.productioncontrol;

import java.util.Locale;

public enum ProductionOrderNextAction {
    CREATE_RUN,
    ASSIGN_GRINDER,
    LOAD_BEEF_TRIM,
    WAIT_FOR_GRINDER,
    CLEAR_GRINDER_OUTPUT,
    MOVE_GROUND_BEEF,
    ASSIGN_PATTY_FORMER,
    LOAD_GROUND_BEEF,
    WAIT_FOR_PATTY_FORMER,
    CLEAR_PATTY_FORMER_OUTPUT,
    COLLECT_BEEF_PATTIES,
    COMPLETE,
    CANCELLED,
    FAILED,
    UNKNOWN_OUTCOME,
    STALE_REFERENCE;

    public String translationKey() {
        return "screen.butchercraft.production_order.next."
                + name().toLowerCase(Locale.ROOT);
    }
}
