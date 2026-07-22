package com.butchercraft.world.workforce;

import java.util.Arrays;

public enum WorkforcePositionType {
    OWNER("owner"),
    MANAGER("manager"),
    SUPERVISOR("supervisor"),
    MEAT_CUTTER("meat_cutter"),
    APPRENTICE_CUTTER("apprentice_cutter"),
    SLAUGHTER_TECHNICIAN("slaughter_technician"),
    PACKAGING_OPERATOR("packaging_operator"),
    GRINDER_OPERATOR("grinder_operator"),
    BANDSAW_OPERATOR("bandsaw_operator"),
    SANITATION_TECHNICIAN("sanitation_technician"),
    QUALITY_ASSURANCE("quality_assurance"),
    SHIPPING_RECEIVING("shipping_receiving"),
    RETAIL_CLERK("retail_clerk"),
    CUSTOMER_SERVICE("customer_service"),
    MAINTENANCE("maintenance"),
    OFFICE_ADMINISTRATION("office_administration");

    private final String serializedName;

    WorkforcePositionType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkforcePositionType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown workforce position type: " + serializedName));
    }
}
