package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class AllocationRuntimeRegistry {
    private static final AllocationRuntimeRegistry EMPTY =
            new AllocationRuntimeRegistry(List.of());

    private final List<AllocationRuntimeView> views;
    private final Map<AllocationSetId, AllocationRuntimeView> bySet;
    private final Map<AllocationRuntimeStatus, List<AllocationRuntimeView>> byStatus;

    private AllocationRuntimeRegistry(Collection<AllocationRuntimeView> source) {
        List<AllocationRuntimeView> canonical = new ArrayList<>(
                AllocationValidation.required(source, "views")
        );
        canonical.forEach(view -> AllocationValidation.required(view, "view"));
        canonical.sort(Comparator.naturalOrder());
        Map<AllocationSetId, AllocationRuntimeView> index = new LinkedHashMap<>();
        for (AllocationRuntimeView view : canonical) {
            if (index.putIfAbsent(view.allocationSetId(), view) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_RUNTIME,
                        view.allocationSetId().value(),
                        "Duplicate AllocationSet runtime"
                );
            }
        }
        views = List.copyOf(canonical);
        bySet = Collections.unmodifiableMap(index);
        Map<AllocationRuntimeStatus, List<AllocationRuntimeView>> statuses =
                new EnumMap<>(AllocationRuntimeStatus.class);
        for (AllocationRuntimeStatus status : AllocationRuntimeStatus.values()) {
            statuses.put(
                    status,
                    views.stream().filter(view -> view.status() == status).toList()
            );
        }
        byStatus = Collections.unmodifiableMap(statuses);
    }

    public static AllocationRuntimeRegistry empty() {
        return EMPTY;
    }

    public static AllocationRuntimeRegistry of(
            Collection<AllocationRuntimeView> source
    ) {
        Collection<AllocationRuntimeView> views = AllocationValidation.required(
                source,
                "views"
        );
        return views.isEmpty() ? EMPTY : new AllocationRuntimeRegistry(views);
    }

    public boolean contains(AllocationSetId id) {
        return bySet.containsKey(AllocationValidation.required(id, "id"));
    }

    public Optional<AllocationRuntimeView> find(AllocationSetId id) {
        return Optional.ofNullable(bySet.get(AllocationValidation.required(id, "id")));
    }

    public int size() {
        return views.size();
    }

    public List<AllocationRuntimeView> views() {
        return views;
    }

    public Stream<AllocationRuntimeView> stream() {
        return views.stream();
    }

    public List<AllocationRuntimeView> findByStatus(AllocationRuntimeStatus status) {
        return byStatus.get(AllocationValidation.required(status, "status"));
    }
}
