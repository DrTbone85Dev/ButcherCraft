package com.butchercraft.world.identity;

import java.util.Arrays;

public enum NamingRole {
    COUNTY_PRIMARY("county_primary"),
    COUNTY_MARKET("county_market"),
    COUNTY_FRONTIER("county_frontier"),
    SETTLEMENT_RURAL_HAMLET("settlement_rural_hamlet"),
    SETTLEMENT_AGRICULTURAL_VILLAGE("settlement_agricultural_village"),
    SETTLEMENT_MARKET_VILLAGE("settlement_market_village"),
    SETTLEMENT_COUNTY_TOWN("settlement_county_town"),
    SETTLEMENT_REMOTE_HAMLET("settlement_remote_hamlet"),
    SETTLEMENT_TRADE_TOWN("settlement_trade_town"),
    SETTLEMENT_REGIONAL_CITY("settlement_regional_city");

    private final String serializedName;

    NamingRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static NamingRole fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(role -> role.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown naming role: " + serializedName));
    }
}
