package com.butchercraft.world.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RegionCatalog {
    private static final List<NamingRole> REQUIRED_NAMING_ROLES = List.of(NamingRole.values());

    private final List<RegionDefinition> regions;
    private final List<RegionDefinition> deterministicSelectionOrder;
    private final List<NamingProfile> namingProfiles;
    private final Map<String, RegionDefinition> regionsById;
    private final Map<String, NamingProfile> namingProfilesById;

    private RegionCatalog(
            List<RegionDefinition> regions,
            List<RegionDefinition> deterministicSelectionOrder,
            List<NamingProfile> namingProfiles,
            Map<String, RegionDefinition> regionsById,
            Map<String, NamingProfile> namingProfilesById
    ) {
        this.regions = regions;
        this.deterministicSelectionOrder = deterministicSelectionOrder;
        this.namingProfiles = namingProfiles;
        this.regionsById = regionsById;
        this.namingProfilesById = namingProfilesById;
    }

    public static RegionCatalog builtIn() {
        return BuiltInRegionCatalog.create();
    }

    public static RegionCatalog of(Collection<RegionDefinition> regions, Collection<NamingProfile> namingProfiles) {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(namingProfiles, "namingProfiles");
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("Region catalog must contain at least one region");
        }

        Map<String, NamingProfile> profilesById = new LinkedHashMap<>();
        for (NamingProfile profile : namingProfiles) {
            Objects.requireNonNull(profile, "profile");
            if (profilesById.putIfAbsent(profile.id(), profile) != null) {
                throw new IllegalArgumentException("Duplicate naming profile id: " + profile.id());
            }
            for (NamingRole role : REQUIRED_NAMING_ROLES) {
                profile.namesFor(role);
            }
        }

        Map<String, RegionDefinition> definitionsById = new LinkedHashMap<>();
        List<RegionDefinition> copiedRegions = new ArrayList<>();
        for (RegionDefinition region : regions) {
            Objects.requireNonNull(region, "region");
            if (!profilesById.containsKey(region.namingProfileId())) {
                throw new IllegalArgumentException("Region " + region.id()
                        + " references unknown naming profile: " + region.namingProfileId());
            }
            if (definitionsById.putIfAbsent(region.id(), region) != null) {
                throw new IllegalArgumentException("Duplicate region id: " + region.id());
            }
            copiedRegions.add(region);
        }

        List<RegionDefinition> selectionOrder = copiedRegions.stream()
                .sorted(Comparator.comparing(RegionDefinition::id))
                .toList();
        return new RegionCatalog(
                List.copyOf(copiedRegions),
                selectionOrder,
                List.copyOf(profilesById.values()),
                Map.copyOf(definitionsById),
                Map.copyOf(profilesById)
        );
    }

    public boolean contains(String regionId) {
        return regionsById.containsKey(regionId);
    }

    public Optional<RegionDefinition> find(String regionId) {
        return Optional.ofNullable(regionsById.get(regionId));
    }

    public int size() {
        return regions.size();
    }

    public List<RegionDefinition> regions() {
        return regions;
    }

    public List<RegionDefinition> deterministicSelectionOrder() {
        return deterministicSelectionOrder;
    }

    public List<NamingProfile> namingProfiles() {
        return namingProfiles;
    }

    public NamingProfile namingProfile(RegionDefinition region) {
        Objects.requireNonNull(region, "region");
        NamingProfile profile = namingProfilesById.get(region.namingProfileId());
        if (profile == null) {
            throw new IllegalArgumentException("Region " + region.id()
                    + " references unknown naming profile: " + region.namingProfileId());
        }
        return profile;
    }
}
