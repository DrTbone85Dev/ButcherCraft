package com.butchercraft.world.economy.order.persistence;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.order.CommitmentPeriod;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.ContractLineDefinition;
import com.butchercraft.world.economy.order.ContractLineId;
import com.butchercraft.world.economy.order.ContractLineMetadata;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.ContractMetadata;
import com.butchercraft.world.economy.order.ContractRegistry;
import com.butchercraft.world.economy.order.ContractSchedule;
import com.butchercraft.world.economy.order.ContractScheduleType;
import com.butchercraft.world.economy.order.ContractStatus;
import com.butchercraft.world.economy.order.ContractTerms;
import com.butchercraft.world.economy.order.ContractType;
import com.butchercraft.world.economy.order.EconomicContractDefinition;
import com.butchercraft.world.economy.order.EconomicContractRuntime;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderContractSchema;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.economy.order.SubstitutionPolicy;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;
import com.butchercraft.world.goods.UnitOfMeasure;
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
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

public final class ContractStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

    private final Path filePath;
    private final EconomicActorRegistry actorRegistry;

    public ContractStorage(Path filePath, EconomicActorRegistry actorRegistry) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
    }

    public Path filePath() { return filePath; }

    public ContractManager load() {
        if (!Files.exists(filePath)) return new ContractManager(actorRegistry);
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load contracts from " + filePath, exception);
        }
    }

    public void save(ContractManager manager) {
        Objects.requireNonNull(manager, "manager");
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporary, serialize(manager), StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save contracts to " + filePath, exception);
        }
    }

    public String serialize(ContractManager manager) {
        Objects.requireNonNull(manager, "manager");
        List<ContractRecord> records = manager.definitions().stream().map(definition -> new ContractRecord(
                toDefinitionRecord(definition),
                toRuntimeRecord(manager.runtimeFor(definition.id()).orElseThrow())
        )).toList();
        return GSON.toJson(new ContractDocument(OrderContractSchema.CURRENT_VERSION, records))
                + System.lineSeparator();
    }

    public ContractManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            ContractDocument document = Objects.requireNonNull(
                    GSON.fromJson(json, ContractDocument.class), "Contract persistence root"
            );
            if (document.schemaVersion() != OrderContractSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported contract persistence schema version: "
                        + document.schemaVersion());
            }
            List<ContractRecord> records = List.copyOf(
                    Objects.requireNonNull(document.contracts(), "contracts")
            );
            List<EconomicContractDefinition> definitions = new ArrayList<>(records.size());
            List<EconomicContractRuntime> runtimes = new ArrayList<>(records.size());
            for (ContractRecord record : records) {
                ContractRecord nonNull = Objects.requireNonNull(record, "contract record");
                definitions.add(fromDefinitionRecord(nonNull.definition()));
                runtimes.add(fromRuntimeRecord(nonNull.runtime()));
            }
            return new ContractManager(ContractRegistry.of(definitions), runtimes, actorRegistry);
        } catch (JsonParseException | NullPointerException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt contract persistence", exception);
        }
    }

    private static DefinitionRecord toDefinitionRecord(EconomicContractDefinition definition) {
        return new DefinitionRecord(
                definition.schemaVersion(), definition.id().value(), definition.displayName(),
                definition.type().serializedName(), definition.principalActorId().value(),
                definition.counterpartyActorId().value(),
                definition.supportedIndustries().stream().map(IndustryId::value).toList(),
                definition.lines().stream().map(ContractStorage::toLineRecord).toList(),
                definition.effectiveSimulationTick(), optionalLong(definition.expirationSimulationTick()),
                new ScheduleRecord(
                        definition.schedule().type().serializedName(),
                        optionalLong(definition.schedule().intervalSimulationTicks()),
                        definition.schedule().periodKey().orElse(null)
                ),
                toTermsRecord(definition.terms()), toMetadataRecord(definition.metadata())
        );
    }

    private static EconomicContractDefinition fromDefinitionRecord(DefinitionRecord record) {
        Objects.requireNonNull(record, "contract definition");
        EconomicContractDefinition.Builder builder = EconomicContractDefinition.builder()
                .id(ContractId.of(record.id())).displayName(record.displayName())
                .type(ContractType.fromSerializedName(record.type()))
                .principalActorId(ActorId.of(record.principalActorId()))
                .counterpartyActorId(ActorId.of(record.counterpartyActorId()))
                .supportedIndustries(requireList(record.supportedIndustries(), "supported industries").stream()
                        .map(IndustryId::of).collect(java.util.stream.Collectors.toSet()))
                .lines(requireList(record.lines(), "contract lines").stream()
                        .map(ContractStorage::fromLineRecord).toList())
                .effectiveSimulationTick(record.effectiveSimulationTick())
                .schedule(fromScheduleRecord(record.schedule()))
                .terms(fromTermsRecord(record.terms()))
                .metadata(fromMetadataRecord(record.metadata())).schemaVersion(record.schemaVersion());
        if (record.expirationSimulationTick() != null) {
            builder.expirationSimulationTick(record.expirationSimulationTick());
        }
        return builder.build();
    }

    private static LineRecord toLineRecord(ContractLineDefinition line) {
        return new LineRecord(
                line.id().value(), line.goodId().value(), line.committedQuantity().canonicalValue(),
                line.unitOfMeasure().serializedName(), line.commitmentPeriod().serializedName(),
                line.minimumQuantity().map(GoodQuantity::canonicalValue).orElse(null),
                line.maximumQuantity().map(GoodQuantity::canonicalValue).orElse(null),
                line.allowedVariance().map(GoodQuantity::canonicalValue).orElse(null),
                new LineMetadataRecord(
                        line.metadata().externalReference().orElse(null), line.metadata().notes().orElse(null)
                )
        );
    }

    private static ContractLineDefinition fromLineRecord(LineRecord record) {
        Objects.requireNonNull(record, "contract line");
        ContractLineDefinition.Builder builder = ContractLineDefinition.builder()
                .id(ContractLineId.of(record.id())).goodId(GoodId.of(record.goodId()))
                .committedQuantity(GoodQuantity.of(record.committedQuantity()))
                .unitOfMeasure(UnitOfMeasure.fromSerializedName(record.unit()))
                .commitmentPeriod(CommitmentPeriod.fromSerializedName(record.commitmentPeriod()))
                .metadata(new ContractLineMetadata(
                        Optional.ofNullable(record.metadata().externalReference()),
                        Optional.ofNullable(record.metadata().notes())
                ));
        if (record.minimumQuantity() != null) builder.minimumQuantity(GoodQuantity.of(record.minimumQuantity()));
        if (record.maximumQuantity() != null) builder.maximumQuantity(GoodQuantity.of(record.maximumQuantity()));
        if (record.allowedVariance() != null) builder.allowedVariance(GoodQuantity.of(record.allowedVariance()));
        return builder.build();
    }

    private static ScheduleRecord requireScheduleRecord(ScheduleRecord record) {
        return Objects.requireNonNull(record, "contract schedule");
    }

    private static ContractSchedule fromScheduleRecord(ScheduleRecord source) {
        ScheduleRecord record = requireScheduleRecord(source);
        return new ContractSchedule(
                ContractScheduleType.fromSerializedName(record.type()),
                optionalLong(record.intervalSimulationTicks()), Optional.ofNullable(record.periodKey())
        );
    }

    private static TermsRecord toTermsRecord(ContractTerms terms) {
        return new TermsRecord(
                terms.partialFulfillmentAllowed(), terms.overFulfillmentAllowed(), terms.cancellationAllowed(),
                optionalLong(terms.cancellationNoticeSimulationTicks()), terms.autoRenewal(),
                terms.maximumOpenOrders().isPresent() ? terms.maximumOpenOrders().orElseThrow() : null,
                terms.lateFulfillmentPolicyId().orElse(null), terms.substitutionPolicy().serializedName()
        );
    }

    private static ContractTerms fromTermsRecord(TermsRecord record) {
        Objects.requireNonNull(record, "contract terms");
        return new ContractTerms(
                record.partialFulfillmentAllowed(), record.overFulfillmentAllowed(), record.cancellationAllowed(),
                optionalLong(record.cancellationNoticeSimulationTicks()), record.autoRenewal(),
                record.maximumOpenOrders() == null ? OptionalInt.empty()
                        : OptionalInt.of(record.maximumOpenOrders()),
                Optional.ofNullable(record.lateFulfillmentPolicyId()),
                SubstitutionPolicy.fromSerializedName(record.substitutionPolicy())
        );
    }

    private static MetadataRecord toMetadataRecord(ContractMetadata metadata) {
        return new MetadataRecord(
                metadata.externalReference().orElse(null), metadata.notes().orElse(null),
                metadata.sourceModule().orElse(null), metadata.creationReason().orElse(null),
                List.copyOf(metadata.tags())
        );
    }

    private static ContractMetadata fromMetadataRecord(MetadataRecord record) {
        Objects.requireNonNull(record, "contract metadata");
        return new ContractMetadata(
                Optional.ofNullable(record.externalReference()), Optional.ofNullable(record.notes()),
                Optional.ofNullable(record.sourceModule()), Optional.ofNullable(record.creationReason()),
                Set.copyOf(requireList(record.tags(), "contract tags"))
        );
    }

    private static RuntimeRecord toRuntimeRecord(EconomicContractRuntime runtime) {
        return new RuntimeRecord(
                runtime.schemaVersion(), runtime.contractId().value(), runtime.status().serializedName(),
                runtime.lastUpdatedSimulationTick(), optionalLong(runtime.activationSimulationTick()),
                optionalLong(runtime.terminationSimulationTick()),
                runtime.governedOrderIds().stream().map(OrderId::value).toList(),
                runtime.currentPeriodIdentity().orElse(null), runtime.statusReason().orElse(null), runtime.revision()
        );
    }

    private static EconomicContractRuntime fromRuntimeRecord(RuntimeRecord record) {
        Objects.requireNonNull(record, "contract runtime");
        return new EconomicContractRuntime(
                ContractId.of(record.contractId()), ContractStatus.fromSerializedName(record.status()),
                record.lastUpdatedSimulationTick(), optionalLong(record.activationSimulationTick()),
                optionalLong(record.terminationSimulationTick()),
                requireList(record.governedOrderIds(), "governed orders").stream().map(OrderId::of).toList(),
                Optional.ofNullable(record.currentPeriodIdentity()), Optional.ofNullable(record.statusReason()),
                record.revision(), record.schemaVersion()
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
    private static <T> List<T> requireList(List<T> value, String label) {
        return List.copyOf(Objects.requireNonNull(value, label));
    }

    private record ContractDocument(
            @SerializedName("schema_version") int schemaVersion,
            List<ContractRecord> contracts
    ) { }
    private record ContractRecord(DefinitionRecord definition, RuntimeRecord runtime) { }
    private record DefinitionRecord(
            @SerializedName("schema_version") int schemaVersion,
            String id,
            @SerializedName("display_name") String displayName,
            String type,
            @SerializedName("principal_actor_id") String principalActorId,
            @SerializedName("counterparty_actor_id") String counterpartyActorId,
            @SerializedName("supported_industries") List<String> supportedIndustries,
            List<LineRecord> lines,
            @SerializedName("effective_simulation_tick") long effectiveSimulationTick,
            @SerializedName("expiration_simulation_tick") Long expirationSimulationTick,
            ScheduleRecord schedule,
            TermsRecord terms,
            MetadataRecord metadata
    ) { }
    private record LineRecord(
            String id,
            @SerializedName("good_id") String goodId,
            @SerializedName("committed_quantity") String committedQuantity,
            String unit,
            @SerializedName("commitment_period") String commitmentPeriod,
            @SerializedName("minimum_quantity") String minimumQuantity,
            @SerializedName("maximum_quantity") String maximumQuantity,
            @SerializedName("allowed_variance") String allowedVariance,
            LineMetadataRecord metadata
    ) { }
    private record ScheduleRecord(
            String type,
            @SerializedName("interval_simulation_ticks") Long intervalSimulationTicks,
            @SerializedName("period_key") String periodKey
    ) { }
    private record TermsRecord(
            @SerializedName("partial_fulfillment_allowed") boolean partialFulfillmentAllowed,
            @SerializedName("over_fulfillment_allowed") boolean overFulfillmentAllowed,
            @SerializedName("cancellation_allowed") boolean cancellationAllowed,
            @SerializedName("cancellation_notice_simulation_ticks") Long cancellationNoticeSimulationTicks,
            @SerializedName("auto_renewal") boolean autoRenewal,
            @SerializedName("maximum_open_orders") Integer maximumOpenOrders,
            @SerializedName("late_fulfillment_policy_id") String lateFulfillmentPolicyId,
            @SerializedName("substitution_policy") String substitutionPolicy
    ) { }
    private record MetadataRecord(
            @SerializedName("external_reference") String externalReference,
            String notes,
            @SerializedName("source_module") String sourceModule,
            @SerializedName("creation_reason") String creationReason,
            List<String> tags
    ) { }
    private record LineMetadataRecord(
            @SerializedName("external_reference") String externalReference,
            String notes
    ) { }
    private record RuntimeRecord(
            @SerializedName("schema_version") int schemaVersion,
            @SerializedName("contract_id") String contractId,
            String status,
            @SerializedName("last_updated_simulation_tick") long lastUpdatedSimulationTick,
            @SerializedName("activation_simulation_tick") Long activationSimulationTick,
            @SerializedName("termination_simulation_tick") Long terminationSimulationTick,
            @SerializedName("governed_order_ids") List<String> governedOrderIds,
            @SerializedName("current_period_identity") String currentPeriodIdentity,
            @SerializedName("status_reason") String statusReason,
            long revision
    ) { }
}
