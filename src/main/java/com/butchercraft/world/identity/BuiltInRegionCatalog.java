package com.butchercraft.world.identity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BuiltInRegionCatalog {
    public static final String PRAIRIE_COMMONWEALTH = "prairie_commonwealth";
    public static final String IRON_VALLEY = "iron_valley";
    public static final String GREAT_RIVER_BASIN = "great_river_basin";
    public static final String HIGH_PLAINS_TERRITORY = "high_plains_territory";
    public static final String TIMBER_RIDGE = "timber_ridge";

    private BuiltInRegionCatalog() {
    }

    public static RegionCatalog create() {
        return RegionCatalog.of(regions(), namingProfiles());
    }

    private static List<RegionDefinition> regions() {
        return List.of(
                new RegionDefinition(
                        PRAIRIE_COMMONWEALTH,
                        "Prairie Commonwealth",
                        "A broad agricultural commonwealth shaped by market towns, rail crossings, and open farm country.",
                        "Cattle routes, grain farms, hay ground, and mixed rural suppliers.",
                        "Roadside butcher shops, county markets, locker plants, and regional freight corridors.",
                        "Plainspoken county names, civic market towns, and practical farm-community identity.",
                        PRAIRIE_COMMONWEALTH
                ),
                new RegionDefinition(
                        IRON_VALLEY,
                        "Iron Valley",
                        "A working valley of older towns, quarry roads, rail yards, and compact industrial lots.",
                        "Mixed livestock farms, valley dairies, orchard edges, and feed routes serving denser towns.",
                        "Repair shops, cold storage buildings, warehouse districts, and long-running family processors.",
                        "Names favor mills, foundries, bridges, and valley landmarks with an older industrial tone.",
                        IRON_VALLEY
                ),
                new RegionDefinition(
                        GREAT_RIVER_BASIN,
                        "Great River Basin",
                        "A river-centered region of bottomland farms, ferry crossings, bluffs, and busy local markets.",
                        "River-bottom pasture, hog and cattle farms, orchards, and truck-garden produce routes.",
                        "Village storefronts, supplier routes, waterfront storage, and seasonal food markets.",
                        "Names draw from bends, landings, bridges, groves, and long-settled river communities.",
                        GREAT_RIVER_BASIN
                ),
                new RegionDefinition(
                        HIGH_PLAINS_TERRITORY,
                        "High Plains Territory",
                        "A dry, wide-open territory of range roads, auction traffic, and far-spaced service towns.",
                        "Ranch leases, hardy herds, hay fields, dryland grain, and feedlot service routes.",
                        "Auction yards, trucking services, larger processing lots, and warehouse-scale suppliers.",
                        "Names emphasize mesas, range stations, draws, flats, and territorial crossroads.",
                        HIGH_PLAINS_TERRITORY
                ),
                new RegionDefinition(
                        TIMBER_RIDGE,
                        "Timber Ridge",
                        "A wooded ridge country of small dairies, forest-edge grazing, and close rural settlements.",
                        "Cool-season pasture, dairy routes, small beef herds, maple groves, and lake-country farms.",
                        "Family shops, rural cold storage, seasonal retail, and small-town specialty suppliers.",
                        "Names favor timber, ridge, lake, pine, cedar, and old road-crossing patterns.",
                        TIMBER_RIDGE
                )
        );
    }

    private static List<NamingProfile> namingProfiles() {
        return List.of(
                new NamingProfile(PRAIRIE_COMMONWEALTH, "Prairie Commonwealth Names", names(
                        List.of("Wheatland County", "Prairie County", "Sunfield County", "Meadow County"),
                        List.of("Market County", "Rail Junction County", "Crossroads County", "Granary County"),
                        List.of("Bison Plains County", "Range County", "Longfield County", "Windbreak County"),
                        List.of("Meadow Hollow", "Oak Spur", "Clover Bend", "Wheat Fork"),
                        List.of("Sunfield", "Prairie Grove", "Wheat Junction", "Meadow Crossing"),
                        List.of("Rail Market", "Commonwealth Center", "Grain Exchange", "Market Junction"),
                        List.of("Crossroads", "County Center", "Hearthfield", "Wheatland"),
                        List.of("Bison Spur", "Windbreak", "South Meadow", "Range Hollow"),
                        List.of("Freight Crossing", "Stockyard Bend", "Common Market", "Railfield"),
                        List.of("Commonwealth City", "Prairie Capital", "Marketgate", "Sunfield City")
                )),
                new NamingProfile(IRON_VALLEY, "Iron Valley Names", names(
                        List.of("Foundry County", "Millstone County", "Iron County", "Forge County"),
                        List.of("Bridgeworks County", "Railworks County", "Valley Market County", "Quarry County"),
                        List.of("Cinder Ridge County", "Granite County", "Coal Run County", "Slate County"),
                        List.of("Ash Hollow", "Mill Creek", "Stone Spur", "Forge Hollow"),
                        List.of("Millbridge", "Iron Grove", "Valley Junction", "Foundry Cross"),
                        List.of("Rail Market", "Bridgeworks", "Quarry Exchange", "Depot Square"),
                        List.of("Iron Center", "Millstone", "Bridgeport", "Valley Seat"),
                        List.of("Cinder Hollow", "Slate Fork", "Granite Bend", "Coal Run"),
                        List.of("Forge Junction", "Valley Works", "Foundry Market", "Railgate"),
                        List.of("Iron Valley City", "Foundry City", "Bridgegate", "Millstone City")
                )),
                new NamingProfile(GREAT_RIVER_BASIN, "Great River Basin Names", names(
                        List.of("Riverbend County", "Willow County", "Bluff County", "Delta County"),
                        List.of("Bridge County", "Ferry County", "Market Landing County", "Orchard County"),
                        List.of("Cypress County", "Ford County", "Bottomland County", "Drift County"),
                        List.of("Willow Bend", "Old Ferry", "Cedar Landing", "River Hollow"),
                        List.of("Bridge Grove", "Orchard Ford", "River Crossing", "Willow Market"),
                        List.of("Basin Market", "Ferry Exchange", "Bridge Landing", "River Square"),
                        List.of("Riverbend", "Bluff Center", "County Landing", "Bridgeport"),
                        List.of("Cypress Hollow", "Drift Bend", "Bottomland", "Ford Creek"),
                        List.of("Great Landing", "Rivergate", "Basin Crossing", "Market Bluff"),
                        List.of("Basin City", "Great River City", "Bridgehaven", "Rivergate City")
                )),
                new NamingProfile(HIGH_PLAINS_TERRITORY, "High Plains Territory Names", names(
                        List.of("Mesa County", "Sage County", "Range County", "Canyon County"),
                        List.of("Auction County", "Stockyard County", "Highland County", "Railhead County"),
                        List.of("Dry Creek County", "Draw County", "Red Flats County", "Outpost County"),
                        List.of("Sage Hollow", "Mesa Spur", "Canyon Fork", "Range Well"),
                        List.of("Highland Crossing", "Sage Junction", "Mesa Grove", "Range Market"),
                        List.of("Auction Springs", "Railhead Market", "Stockyard Crossing", "High Plains Exchange"),
                        List.of("Territory Center", "Mesa Seat", "Highland", "Canyon Center"),
                        List.of("Dry Creek", "Red Flats", "Draw Station", "Outpost Wells"),
                        List.of("Range Station", "Auction Junction", "Territory Crossing", "Railhead"),
                        List.of("Territory City", "High Plains City", "Mesa City", "Rangegate")
                )),
                new NamingProfile(TIMBER_RIDGE, "Timber Ridge Names", names(
                        List.of("Pine County", "Cedar County", "Maple County", "Ridge County"),
                        List.of("Timber County", "Lake Shore County", "Mill County", "Harbor County"),
                        List.of("Northwood County", "Clearwater County", "Birch County", "Spruce County"),
                        List.of("Pine Hollow", "Cedar Brook", "Maple Fork", "Ridge Hollow"),
                        List.of("Timber Crossing", "Lake Grove", "Cedar Falls", "Pine Market"),
                        List.of("Mill Harbor", "Ridge Market", "Lake Exchange", "Timber Square"),
                        List.of("Timber Ridge", "County Harbor", "Maple Center", "Pine Seat"),
                        List.of("Northwood", "Clearwater", "Birch Hollow", "Spruce Bend"),
                        List.of("Lake Crossing", "Ridge Station", "Mill Junction", "Harbor Market"),
                        List.of("Timber Ridge City", "Lake Country City", "Pinegate", "Cedar Harbor")
                ))
        );
    }

    private static Map<NamingRole, List<String>> names(
            List<String> countyPrimary,
            List<String> countyMarket,
            List<String> countyFrontier,
            List<String> ruralHamlet,
            List<String> agriculturalVillage,
            List<String> marketVillage,
            List<String> countyTown,
            List<String> remoteHamlet,
            List<String> tradeTown,
            List<String> regionalCity
    ) {
        EnumMap<NamingRole, List<String>> names = new EnumMap<>(NamingRole.class);
        names.put(NamingRole.COUNTY_PRIMARY, countyPrimary);
        names.put(NamingRole.COUNTY_MARKET, countyMarket);
        names.put(NamingRole.COUNTY_FRONTIER, countyFrontier);
        names.put(NamingRole.SETTLEMENT_RURAL_HAMLET, ruralHamlet);
        names.put(NamingRole.SETTLEMENT_AGRICULTURAL_VILLAGE, agriculturalVillage);
        names.put(NamingRole.SETTLEMENT_MARKET_VILLAGE, marketVillage);
        names.put(NamingRole.SETTLEMENT_COUNTY_TOWN, countyTown);
        names.put(NamingRole.SETTLEMENT_REMOTE_HAMLET, remoteHamlet);
        names.put(NamingRole.SETTLEMENT_TRADE_TOWN, tradeTown);
        names.put(NamingRole.SETTLEMENT_REGIONAL_CITY, regionalCity);
        return names;
    }
}
