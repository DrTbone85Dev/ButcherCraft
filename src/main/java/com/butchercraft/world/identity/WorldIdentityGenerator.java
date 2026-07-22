package com.butchercraft.world.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldIdentityGenerator {
    private static final long ID_SALT = 0x6d2b79f5a1c4d3e7L;
    private static final long REGION_SALT = 0x1f123bb5c4a19d71L;
    private static final long COUNTY_SALT = 0x4d9f2c7b66b8f2a5L;
    private static final long SETTLEMENT_SALT = 0x21d52f91764c3a2bL;

    private static final List<RegionTemplate> REGION_TEMPLATES = List.of(
            new RegionTemplate(
                    "northwoods_ridge",
                    "Northwoods Ridge",
                    "Cool-season livestock farms, forest-edge grazing, and small dairy routes",
                    "Locker plants, family shops, cold storage services, and seasonal rural trade",
                    List.of("Pine", "Cedar", "Iron", "Maple", "Clearwater"),
                    List.of("Ridge", "Falls", "Hollow", "Crossing", "County"),
                    List.of("Pine", "Cedar", "Mill", "Timber", "Brook"),
                    List.of("Hollow", "Crossing", "Point", "Falls", "Grove")
            ),
            new RegionTemplate(
                    "prairie_crossroads",
                    "Prairie Crossroads",
                    "Open-range cattle country, grain farms, and long-haul livestock routes",
                    "Roadside butcher shops, market towns, regional processors, and freight depots",
                    List.of("Prairie", "Wheatland", "Bison", "Rail", "Sunfield"),
                    List.of("County", "Junction", "Plains", "Crossing", "Bend"),
                    List.of("Wheat", "Rail", "Meadow", "Sun", "Prairie"),
                    List.of("Junction", "Market", "Crossing", "Springs", "Center")
            ),
            new RegionTemplate(
                    "riverbend_valley",
                    "Riverbend Valley",
                    "River-bottom farms, mixed livestock, orchards, and market gardens",
                    "Dense village trade, supplier routes, family storefronts, and local food markets",
                    List.of("River", "Valley", "Mill", "Bridge", "Willow"),
                    List.of("Bend", "County", "Crossing", "Bluff", "Parish"),
                    List.of("River", "Willow", "Bridge", "Orchard", "Mill"),
                    List.of("Bend", "Landing", "Ford", "Market", "Bluff")
            ),
            new RegionTemplate(
                    "high_plains",
                    "High Plains",
                    "Dryland ranching, hardy herds, hay ground, and broad grazing leases",
                    "Industrial lots, auction traffic, trucking services, and larger processing yards",
                    List.of("Mesa", "Sage", "Highland", "Canyon", "Range"),
                    List.of("County", "Mesa", "Draw", "Flats", "Territory"),
                    List.of("Sage", "Mesa", "Canyon", "Range", "Dust"),
                    List.of("Flats", "Station", "Draw", "Outpost", "Heights")
            ),
            new RegionTemplate(
                    "lake_country",
                    "Lake Country",
                    "Pasture farms, wetland hay, dairy routes, and small lakeside markets",
                    "Tourist-season retail, village shops, cold storage, and specialty suppliers",
                    List.of("Lake", "Harbor", "Reed", "Birch", "Northshore"),
                    List.of("County", "Shore", "Harbor", "Isle", "Bay"),
                    List.of("Lake", "Harbor", "Reed", "Birch", "Bay"),
                    List.of("Shore", "Harbor", "Landing", "Cove", "Village")
            )
    );

    private static final SettlementType[][] SETTLEMENT_LAYOUT = {
            {SettlementType.HAMLET, SettlementType.VILLAGE},
            {SettlementType.VILLAGE, SettlementType.TOWN},
            {SettlementType.HAMLET, SettlementType.TOWN, SettlementType.REGIONAL_CITY}
    };

    public WorldIdentity generate(long worldSeed) {
        RegionTemplate template = REGION_TEMPLATES.get(pick(worldSeed, REGION_SALT, REGION_TEMPLATES.size()));
        Region region = new Region(
                template.id(),
                template.displayName(),
                template.agriculturalIdentity(),
                template.economicIdentity(),
                template.namingConvention()
        );
        List<County> counties = new ArrayList<>();
        for (int countyIndex = 0; countyIndex < SETTLEMENT_LAYOUT.length; countyIndex++) {
            counties.add(generateCounty(worldSeed, region.id(), template, countyIndex));
        }
        return new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                "world_" + Long.toUnsignedString(mix64(worldSeed ^ ID_SALT), 36),
                worldSeed,
                region,
                counties
        );
    }

    private static County generateCounty(long worldSeed, String regionId, RegionTemplate template, int countyIndex) {
        String countyName = uniqueName(
                worldSeed,
                COUNTY_SALT + countyIndex * 101L,
                template.countyPrefixes(),
                template.countySuffixes(),
                countyIndex
        );
        String countyId = slug(countyName) + "_" + (countyIndex + 1);
        SettlementType[] settlementTypes = SETTLEMENT_LAYOUT[countyIndex];
        List<Settlement> settlements = new ArrayList<>();
        for (int settlementIndex = 0; settlementIndex < settlementTypes.length; settlementIndex++) {
            settlements.add(generateSettlement(
                    worldSeed,
                    template,
                    countyId,
                    countyIndex,
                    settlementIndex,
                    settlementTypes[settlementIndex]
            ));
        }
        return new County(countyId, countyName, regionId, settlements);
    }

    private static Settlement generateSettlement(
            long worldSeed,
            RegionTemplate template,
            String countyId,
            int countyIndex,
            int settlementIndex,
            SettlementType type
    ) {
        long salt = SETTLEMENT_SALT + countyIndex * 211L + settlementIndex * 37L + type.ordinal() * 17L;
        String settlementName = uniqueName(worldSeed, salt, template.settlementPrefixes(), template.settlementSuffixes(), settlementIndex);
        String settlementId = countyId + "_" + type.serializedName() + "_" + (settlementIndex + 1);
        return new Settlement(settlementId, settlementName, countyId, type);
    }

    private static String uniqueName(long seed, long salt, List<String> prefixes, List<String> suffixes, int offset) {
        String prefix = prefixes.get(pick(seed, salt, prefixes.size()));
        String suffix = suffixes.get(pick(seed, salt + 0x9e3779b97f4a7c15L + offset, suffixes.size()));
        return prefix + " " + suffix;
    }

    private static int pick(long seed, long salt, int bound) {
        return (int) Long.remainderUnsigned(mix64(seed + salt), bound);
    }

    private static long mix64(long value) {
        long mixed = value + 0x9e3779b97f4a7c15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private record RegionTemplate(
            String id,
            String displayName,
            String agriculturalIdentity,
            String economicIdentity,
            List<String> countyPrefixes,
            List<String> countySuffixes,
            List<String> settlementPrefixes,
            List<String> settlementSuffixes
    ) {
        String namingConvention() {
            return "Regional names favor " + String.join(", ", countyPrefixes.subList(0, Math.min(3, countyPrefixes.size())))
                    + " and " + String.join(", ", settlementSuffixes.subList(0, Math.min(3, settlementSuffixes.size())))
                    + " place-name patterns.";
        }
    }
}
