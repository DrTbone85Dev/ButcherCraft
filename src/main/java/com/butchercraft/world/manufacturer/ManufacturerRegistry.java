package com.butchercraft.world.manufacturer;

import com.butchercraft.world.identity.RegionCatalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ManufacturerRegistry {
    private final List<Manufacturer> manufacturers;
    private final Map<String, Manufacturer> manufacturersById;

    private ManufacturerRegistry(List<Manufacturer> manufacturers, Map<String, Manufacturer> manufacturersById) {
        this.manufacturers = manufacturers;
        this.manufacturersById = manufacturersById;
    }

    public static ManufacturerRegistry builtIn() {
        return BuiltInManufacturerCatalog.createRegistry();
    }

    public static ManufacturerRegistry of(Collection<Manufacturer> manufacturers, RegionCatalog regions) {
        Objects.requireNonNull(manufacturers, "manufacturers");
        Objects.requireNonNull(regions, "regions");
        if (manufacturers.isEmpty()) {
            throw new IllegalArgumentException("Manufacturer registry must contain at least one manufacturer");
        }

        List<Manufacturer> deterministicManufacturers = manufacturers.stream()
                .map(manufacturer -> validateRegion(manufacturer, regions))
                .sorted(Comparator.comparing(Manufacturer::id))
                .toList();
        rejectDuplicates(deterministicManufacturers, Manufacturer::id, "manufacturer id");
        rejectDuplicates(deterministicManufacturers, Manufacturer::displayName, "manufacturer name");
        rejectDuplicates(deterministicManufacturers, Manufacturer::slogan, "manufacturer slogan");

        Map<String, Manufacturer> byId = deterministicManufacturers.stream()
                .collect(Collectors.toUnmodifiableMap(Manufacturer::id, Function.identity()));
        return new ManufacturerRegistry(List.copyOf(deterministicManufacturers), byId);
    }

    public boolean contains(String id) {
        return manufacturersById.containsKey(id);
    }

    public Optional<Manufacturer> find(String id) {
        return Optional.ofNullable(manufacturersById.get(id));
    }

    public int size() {
        return manufacturers.size();
    }

    public List<Manufacturer> manufacturers() {
        return manufacturers;
    }

    public Stream<Manufacturer> stream() {
        return manufacturers.stream();
    }

    public List<Manufacturer> findByCategory(ManufacturerCategory category) {
        Objects.requireNonNull(category, "category");
        return manufacturers.stream()
                .filter(manufacturer -> manufacturer.servesCategory(category))
                .toList();
    }

    public List<Manufacturer> findByTier(ManufacturerTier tier) {
        Objects.requireNonNull(tier, "tier");
        return manufacturers.stream()
                .filter(manufacturer -> manufacturer.marketTier() == tier)
                .toList();
    }

    public List<Manufacturer> findByRegion(String regionId) {
        Objects.requireNonNull(regionId, "regionId");
        return manufacturers.stream()
                .filter(manufacturer -> manufacturer.headquarters().regionId().equals(regionId))
                .toList();
    }

    public List<Manufacturer> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return manufacturers.stream()
                .filter(manufacturer -> searchableText(manufacturer).contains(normalizedQuery))
                .toList();
    }

    private static Manufacturer validateRegion(Manufacturer manufacturer, RegionCatalog regions) {
        Objects.requireNonNull(manufacturer, "manufacturer");
        if (!regions.contains(manufacturer.headquarters().regionId())) {
            throw new IllegalArgumentException("Manufacturer " + manufacturer.id()
                    + " references unknown headquarters region: " + manufacturer.headquarters().regionId());
        }
        return manufacturer;
    }

    private static void rejectDuplicates(List<Manufacturer> manufacturers, Function<Manufacturer, String> keyFunction, String label) {
        Set<String> duplicates = manufacturers.stream()
                .map(keyFunction)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate " + label + ": " + duplicates);
        }
    }

    private static String searchableText(Manufacturer manufacturer) {
        List<String> parts = new ArrayList<>();
        parts.add(manufacturer.id());
        parts.add(manufacturer.displayName());
        parts.add(manufacturer.slogan());
        parts.add(manufacturer.reputation());
        parts.add(manufacturer.engineeringPhilosophy().serializedName());
        parts.add(manufacturer.marketTier().serializedName());
        parts.add(manufacturer.headquarters().regionId());
        manufacturer.categories().stream().map(ManufacturerCategory::serializedName).forEach(parts::add);
        parts.addAll(manufacturer.primarySpecialties());
        return parts.stream().map(ManufacturerRegistry::normalize).collect(Collectors.joining(" "));
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT);
    }
}
