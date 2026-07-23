package com.butchercraft.world.economy.order.persistence;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.EconomicOrderDefinition;
import com.butchercraft.world.economy.order.EconomicOrderRuntime;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderContractSchema;
import com.butchercraft.world.economy.order.OrderFulfillmentAllocation;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.economy.order.OrderLineDefinition;
import com.butchercraft.world.economy.order.OrderLineId;
import com.butchercraft.world.economy.order.OrderLineMetadata;
import com.butchercraft.world.economy.order.OrderLineRole;
import com.butchercraft.world.economy.order.OrderLineRuntime;
import com.butchercraft.world.economy.order.OrderLineStatus;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.economy.order.OrderMetadata;
import com.butchercraft.world.economy.order.OrderPriority;
import com.butchercraft.world.economy.order.OrderRegistry;
import com.butchercraft.world.economy.order.OrderStatus;
import com.butchercraft.world.economy.order.OrderTag;
import com.butchercraft.world.economy.order.OrderType;
import com.butchercraft.world.economy.order.SubstitutionPolicy;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryRegistry;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public final class OrderStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

    private final Path filePath;
    private final EconomicActorRegistry actorRegistry;
    private final InventoryRegistry inventoryRegistry;
    private final TransactionManager transactionManager;
    private final ContractManager contractManager;

    public OrderStorage(
            Path filePath,
            EconomicActorRegistry actorRegistry,
            InventoryRegistry inventoryRegistry,
            TransactionManager transactionManager,
            ContractManager contractManager
    ) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
        this.inventoryRegistry = Objects.requireNonNull(inventoryRegistry, "inventoryRegistry");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.contractManager = Objects.requireNonNull(contractManager, "contractManager");
    }

    public Path filePath() { return filePath; }

    public OrderManager load() {
        if (!Files.exists(filePath)) {
            return new OrderManager(actorRegistry, inventoryRegistry, transactionManager, contractManager);
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load orders from " + filePath, exception);
        }
    }

    public void save(OrderManager manager) {
        Objects.requireNonNull(manager, "manager");
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporary, serialize(manager), StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save orders to " + filePath, exception);
        }
    }

    public String serialize(OrderManager manager) {
        Objects.requireNonNull(manager, "manager");
        List<EconomicOrderDefinition> definitions = manager.definitions();
        List<OrderRecord> records = new ArrayList<>(definitions.size());
        for (EconomicOrderDefinition definition : definitions) {
            EconomicOrderRuntime runtime = manager.runtimeFor(definition.id()).orElseThrow();
            records.add(new OrderRecord(toDefinitionRecord(definition), toRuntimeRecord(runtime)));
        }
        return GSON.toJson(new OrderDocument(OrderContractSchema.CURRENT_VERSION, records))
                + System.lineSeparator();
    }

    public OrderManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            OrderDocument document = Objects.requireNonNull(
                    GSON.fromJson(json, OrderDocument.class), "Order persistence root"
            );
            if (document.schemaVersion() != OrderContractSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported order persistence schema version: "
                        + document.schemaVersion());
            }
            List<OrderRecord> records = List.copyOf(Objects.requireNonNull(document.orders(), "orders"));
            List<EconomicOrderDefinition> definitions = new ArrayList<>(records.size());
            List<EconomicOrderRuntime> runtimes = new ArrayList<>(records.size());
            for (OrderRecord record : records) {
                OrderRecord nonNull = Objects.requireNonNull(record, "order record");
                definitions.add(fromDefinitionRecord(nonNull.definition()));
                runtimes.add(fromRuntimeRecord(nonNull.runtime()));
            }
            return new OrderManager(
                    OrderRegistry.of(definitions), runtimes, actorRegistry, inventoryRegistry,
                    transactionManager, contractManager
            );
        } catch (JsonParseException | NullPointerException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt order persistence", exception);
        }
    }

    private static DefinitionRecord toDefinitionRecord(EconomicOrderDefinition definition) {
        return new DefinitionRecord(
                definition.schemaVersion(), definition.id().value(), definition.displayName(),
                definition.type().serializedName(), definition.requesterActorId().value(),
                definition.counterpartyActorId().map(ActorId::value).orElse(null),
                definition.governingContractId().map(ContractId::value).orElse(null),
                definition.lines().stream().map(OrderStorage::toLineRecord).toList(),
                definition.createdSimulationTick(), optionalLong(definition.requestedFulfillmentTick()),
                optionalLong(definition.latestAcceptableFulfillmentTick()),
                definition.priority().serializedName(),
                definition.tags().stream().map(OrderTag::value).toList(),
                toMetadataRecord(definition.metadata())
        );
    }

    private static EconomicOrderDefinition fromDefinitionRecord(DefinitionRecord record) {
        Objects.requireNonNull(record, "order definition");
        EconomicOrderDefinition.Builder builder = EconomicOrderDefinition.builder()
                .id(OrderId.of(record.id())).displayName(record.displayName())
                .type(OrderType.fromSerializedName(record.type()))
                .requesterActorId(ActorId.of(record.requesterActorId()))
                .lines(requireList(record.lines(), "order lines").stream()
                        .map(OrderStorage::fromLineRecord).toList())
                .createdSimulationTick(record.createdSimulationTick())
                .priority(OrderPriority.fromSerializedName(record.priority()))
                .tags(requireList(record.tags(), "order tags").stream()
                        .map(OrderTag::of).collect(java.util.stream.Collectors.toSet()))
                .metadata(fromMetadataRecord(record.metadata())).schemaVersion(record.schemaVersion());
        if (record.counterpartyActorId() != null) builder.counterpartyActorId(ActorId.of(record.counterpartyActorId()));
        if (record.governingContractId() != null) builder.governingContractId(ContractId.of(record.governingContractId()));
        if (record.requestedFulfillmentTick() != null) builder.requestedFulfillmentTick(record.requestedFulfillmentTick());
        if (record.latestAcceptableFulfillmentTick() != null) {
            builder.latestAcceptableFulfillmentTick(record.latestAcceptableFulfillmentTick());
        }
        return builder.build();
    }

    private static LineRecord toLineRecord(OrderLineDefinition line) {
        return new LineRecord(
                line.id().value(), line.goodId().value(), line.requestedQuantity().canonicalValue(),
                line.unitOfMeasure().serializedName(), line.role().serializedName(),
                line.preferredSourceInventoryId().map(InventoryId::value).orElse(null),
                line.preferredDestinationInventoryId().map(InventoryId::value).orElse(null),
                line.substitutionPolicy().map(SubstitutionPolicy::serializedName).orElse(null),
                new LineMetadataRecord(
                        line.metadata().externalReference().orElse(null), line.metadata().notes().orElse(null)
                )
        );
    }

    private static OrderLineDefinition fromLineRecord(LineRecord record) {
        Objects.requireNonNull(record, "order line");
        OrderLineDefinition.Builder builder = OrderLineDefinition.builder()
                .id(OrderLineId.of(record.id())).goodId(GoodId.of(record.goodId()))
                .requestedQuantity(GoodQuantity.of(record.requestedQuantity()))
                .unitOfMeasure(UnitOfMeasure.fromSerializedName(record.unit()))
                .role(OrderLineRole.fromSerializedName(record.role()))
                .metadata(new OrderLineMetadata(
                        Optional.ofNullable(requireNullableString(record.metadata().externalReference())),
                        Optional.ofNullable(requireNullableString(record.metadata().notes()))
                ));
        if (record.preferredSourceInventoryId() != null) {
            builder.preferredSourceInventoryId(InventoryId.of(record.preferredSourceInventoryId()));
        }
        if (record.preferredDestinationInventoryId() != null) {
            builder.preferredDestinationInventoryId(InventoryId.of(record.preferredDestinationInventoryId()));
        }
        if (record.substitutionPolicy() != null) {
            builder.substitutionPolicy(SubstitutionPolicy.fromSerializedName(record.substitutionPolicy()));
        }
        return builder.build();
    }

    private static RuntimeRecord toRuntimeRecord(EconomicOrderRuntime runtime) {
        return new RuntimeRecord(
                runtime.schemaVersion(), runtime.orderId().value(), runtime.status().serializedName(),
                runtime.lastUpdatedSimulationTick(), optionalLong(runtime.acceptedSimulationTick()),
                optionalLong(runtime.closedSimulationTick()), runtime.closureReason().orElse(null), runtime.revision(),
                runtime.lines().stream().map(OrderStorage::toLineRuntimeRecord).toList()
        );
    }

    private static EconomicOrderRuntime fromRuntimeRecord(RuntimeRecord record) {
        Objects.requireNonNull(record, "order runtime");
        return new EconomicOrderRuntime(
                OrderId.of(record.orderId()), OrderStatus.fromSerializedName(record.status()),
                record.lastUpdatedSimulationTick(), requireList(record.lines(), "order runtime lines").stream()
                        .map(OrderStorage::fromLineRuntimeRecord).toList(),
                optionalLong(record.acceptedSimulationTick()), optionalLong(record.closedSimulationTick()),
                Optional.ofNullable(requireNullableString(record.closureReason())), record.revision(),
                record.schemaVersion()
        );
    }

    private static LineRuntimeRecord toLineRuntimeRecord(OrderLineRuntime runtime) {
        return new LineRuntimeRecord(
                runtime.schemaVersion(), runtime.orderLineId().value(), runtime.fulfilledQuantity().canonicalValue(),
                runtime.status().serializedName(), optionalLong(runtime.lastFulfillmentTick()),
                runtime.allocations().stream().map(allocation -> new AllocationRecord(
                        allocation.transactionId().value(), allocation.quantity().canonicalValue(),
                        allocation.simulationTick()
                )).toList()
        );
    }

    private static OrderLineRuntime fromLineRuntimeRecord(LineRuntimeRecord record) {
        Objects.requireNonNull(record, "order line runtime");
        return new OrderLineRuntime(
                OrderLineId.of(record.orderLineId()), GoodQuantity.of(record.fulfilledQuantity()),
                OrderLineStatus.fromSerializedName(record.status()),
                requireList(record.allocations(), "order allocations").stream().map(allocation ->
                        new OrderFulfillmentAllocation(
                                TransactionId.of(allocation.transactionId()),
                                GoodQuantity.of(allocation.quantity()), allocation.simulationTick()
                        )).toList(),
                optionalLong(record.lastFulfillmentTick()), record.schemaVersion()
        );
    }

    private static MetadataRecord toMetadataRecord(OrderMetadata metadata) {
        return new MetadataRecord(
                metadata.externalReference().orElse(null), metadata.notes().orElse(null),
                metadata.sourceModule().orElse(null), metadata.creationReason().orElse(null)
        );
    }

    private static OrderMetadata fromMetadataRecord(MetadataRecord record) {
        Objects.requireNonNull(record, "order metadata");
        return new OrderMetadata(
                Optional.ofNullable(requireNullableString(record.externalReference())),
                Optional.ofNullable(requireNullableString(record.notes())),
                Optional.ofNullable(requireNullableString(record.sourceModule())),
                Optional.ofNullable(requireNullableString(record.creationReason()))
        );
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Long optionalLong(OptionalLong value) { return value.isPresent() ? value.orElseThrow() : null; }
    private static OptionalLong optionalLong(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
    private static String requireNullableString(String value) { return value; }
    private static <T> List<T> requireList(List<T> value, String label) {
        return List.copyOf(Objects.requireNonNull(value, label));
    }

    private record OrderDocument(
            @SerializedName("schema_version") int schemaVersion,
            List<OrderRecord> orders
    ) { }
    private record OrderRecord(DefinitionRecord definition, RuntimeRecord runtime) { }
    private record DefinitionRecord(
            @SerializedName("schema_version") int schemaVersion,
            String id,
            @SerializedName("display_name") String displayName,
            String type,
            @SerializedName("requester_actor_id") String requesterActorId,
            @SerializedName("counterparty_actor_id") String counterpartyActorId,
            @SerializedName("governing_contract_id") String governingContractId,
            List<LineRecord> lines,
            @SerializedName("created_simulation_tick") long createdSimulationTick,
            @SerializedName("requested_fulfillment_tick") Long requestedFulfillmentTick,
            @SerializedName("latest_acceptable_fulfillment_tick") Long latestAcceptableFulfillmentTick,
            String priority,
            List<String> tags,
            MetadataRecord metadata
    ) { }
    private record LineRecord(
            String id,
            @SerializedName("good_id") String goodId,
            @SerializedName("requested_quantity") String requestedQuantity,
            String unit,
            String role,
            @SerializedName("preferred_source_inventory_id") String preferredSourceInventoryId,
            @SerializedName("preferred_destination_inventory_id") String preferredDestinationInventoryId,
            @SerializedName("substitution_policy") String substitutionPolicy,
            LineMetadataRecord metadata
    ) { }
    private record RuntimeRecord(
            @SerializedName("schema_version") int schemaVersion,
            @SerializedName("order_id") String orderId,
            String status,
            @SerializedName("last_updated_simulation_tick") long lastUpdatedSimulationTick,
            @SerializedName("accepted_simulation_tick") Long acceptedSimulationTick,
            @SerializedName("closed_simulation_tick") Long closedSimulationTick,
            @SerializedName("closure_reason") String closureReason,
            long revision,
            List<LineRuntimeRecord> lines
    ) { }
    private record LineRuntimeRecord(
            @SerializedName("schema_version") int schemaVersion,
            @SerializedName("order_line_id") String orderLineId,
            @SerializedName("fulfilled_quantity") String fulfilledQuantity,
            String status,
            @SerializedName("last_fulfillment_tick") Long lastFulfillmentTick,
            List<AllocationRecord> allocations
    ) { }
    private record AllocationRecord(
            @SerializedName("transaction_id") String transactionId,
            String quantity,
            @SerializedName("simulation_tick") long simulationTick
    ) { }
    private record MetadataRecord(
            @SerializedName("external_reference") String externalReference,
            String notes,
            @SerializedName("source_module") String sourceModule,
            @SerializedName("creation_reason") String creationReason
    ) { }
    private record LineMetadataRecord(
            @SerializedName("external_reference") String externalReference,
            String notes
    ) { }
}
