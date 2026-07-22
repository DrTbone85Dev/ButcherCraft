package com.butchercraft.world.identity;

import com.butchercraft.world.property.BuiltInCommercialPropertyCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class WorldIdentityGenerator {
    private static final long ID_SALT = 0x6d2b79f5a1c4d3e7L;
    private static final long REGION_SELECTION_SALT = 0x1f123bb5c4a19d71L;

    private static final List<CountyGenerationSlot> COUNTY_LAYOUT = List.of(
            new CountyGenerationSlot(
                    "primary_county",
                    NamingRole.COUNTY_PRIMARY,
                    List.of(
                            new SettlementGenerationSlot("rural_hamlet", SettlementType.HAMLET, NamingRole.SETTLEMENT_RURAL_HAMLET),
                            new SettlementGenerationSlot("agricultural_village", SettlementType.VILLAGE, NamingRole.SETTLEMENT_AGRICULTURAL_VILLAGE)
                    )
            ),
            new CountyGenerationSlot(
                    "market_county",
                    NamingRole.COUNTY_MARKET,
                    List.of(
                            new SettlementGenerationSlot("market_village", SettlementType.VILLAGE, NamingRole.SETTLEMENT_MARKET_VILLAGE),
                            new SettlementGenerationSlot("county_town", SettlementType.TOWN, NamingRole.SETTLEMENT_COUNTY_TOWN)
                    )
            ),
            new CountyGenerationSlot(
                    "frontier_county",
                    NamingRole.COUNTY_FRONTIER,
                    List.of(
                            new SettlementGenerationSlot("remote_hamlet", SettlementType.HAMLET, NamingRole.SETTLEMENT_REMOTE_HAMLET),
                            new SettlementGenerationSlot("trade_town", SettlementType.TOWN, NamingRole.SETTLEMENT_TRADE_TOWN),
                            new SettlementGenerationSlot("regional_city", SettlementType.REGIONAL_CITY, NamingRole.SETTLEMENT_REGIONAL_CITY)
                    )
            )
    );

    private final RegionCatalog regionCatalog;
    private final WorldIdentityNameGenerator nameGenerator;

    public WorldIdentityGenerator() {
        this(RegionCatalog.builtIn(), new WorldIdentityNameGenerator());
    }

    public WorldIdentityGenerator(RegionCatalog regionCatalog) {
        this(regionCatalog, new WorldIdentityNameGenerator());
    }

    public WorldIdentityGenerator(RegionCatalog regionCatalog, WorldIdentityNameGenerator nameGenerator) {
        this.regionCatalog = Objects.requireNonNull(regionCatalog, "regionCatalog");
        this.nameGenerator = Objects.requireNonNull(nameGenerator, "nameGenerator");
    }

    public WorldIdentity generate(long worldSeed) {
        RegionDefinition regionDefinition = selectRegion(worldSeed);
        Region region = regionDefinition.toRegion();
        List<County> counties = COUNTY_LAYOUT.stream()
                .map(slot -> generateCounty(worldSeed, regionDefinition, slot))
                .toList();
        validateGeneratedNames(counties);
        List<Settlement> settlements = counties.stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
        return new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                "world_" + Long.toUnsignedString(WorldIdentityDeterminism.mix64(worldSeed ^ ID_SALT), 36),
                worldSeed,
                region,
                counties,
                BuiltInCommercialPropertyCatalog.generate(worldSeed, settlements)
        );
    }

    public RegionDefinition selectRegion(long worldSeed) {
        RegionDefinition selected = null;
        long selectedScore = 0L;
        for (RegionDefinition region : regionCatalog.deterministicSelectionOrder()) {
            long score = WorldIdentityDeterminism.stableScore(
                    worldSeed,
                    REGION_SELECTION_SALT,
                    "region",
                    region.id()
            );
            if (selected == null
                    || Long.compareUnsigned(score, selectedScore) > 0
                    || (score == selectedScore && region.id().compareTo(selected.id()) < 0)) {
                selected = region;
                selectedScore = score;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("Region catalog did not contain any selectable regions");
        }
        return selected;
    }

    public String selectName(long worldSeed, RegionDefinition region, NamingRole role, String stableEntityId) {
        return nameGenerator.selectName(worldSeed, regionCatalog, region, role, stableEntityId);
    }

    private County generateCounty(long worldSeed, RegionDefinition region, CountyGenerationSlot slot) {
        String countyId = region.id() + "_" + slot.id();
        String countyName = selectName(worldSeed, region, slot.namingRole(), countyId);
        List<Settlement> settlements = slot.settlements().stream()
                .map(settlementSlot -> generateSettlement(worldSeed, region, countyId, settlementSlot))
                .toList();
        return new County(countyId, countyName, region.id(), settlements);
    }

    private Settlement generateSettlement(
            long worldSeed,
            RegionDefinition region,
            String countyId,
            SettlementGenerationSlot slot
    ) {
        String settlementId = countyId + "_" + slot.id();
        String settlementName = selectName(worldSeed, region, slot.namingRole(), settlementId);
        return new Settlement(settlementId, settlementName, countyId, slot.type());
    }

    private static void validateGeneratedNames(List<County> counties) {
        Set<String> countyNames = new HashSet<>();
        Set<String> settlementNames = new HashSet<>();
        for (County county : counties) {
            if (!countyNames.add(county.displayName())) {
                throw new IllegalStateException("Duplicate generated county name: " + county.displayName());
            }
            for (Settlement settlement : county.settlements()) {
                if (!settlementNames.add(settlement.displayName())) {
                    throw new IllegalStateException("Duplicate generated settlement name: " + settlement.displayName());
                }
            }
        }
    }

    private record CountyGenerationSlot(
            String id,
            NamingRole namingRole,
            List<SettlementGenerationSlot> settlements
    ) {
        private CountyGenerationSlot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namingRole, "namingRole");
            settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        }
    }

    private record SettlementGenerationSlot(
            String id,
            SettlementType type,
            NamingRole namingRole
    ) {
        private SettlementGenerationSlot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(namingRole, "namingRole");
        }
    }
}
