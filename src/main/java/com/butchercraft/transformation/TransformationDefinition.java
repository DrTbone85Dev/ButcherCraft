package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical immutable schema for deterministic material transformations.
 */
public record TransformationDefinition(
        TransformationId id,
        String displayName,
        int schemaVersion,
        Optional<EngineId> requiredCapability,
        List<TransformationInput> inputs,
        List<TransformationOutput> outputs,
        ProcessingDuration duration,
        YieldRatio yieldRatio,
        Map<EngineId, String> metadata
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public TransformationDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Transformation display name cannot be blank");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Transformation schema version must be positive");
        }
        requiredCapability = Objects.requireNonNull(requiredCapability, "requiredCapability");
        requiredCapability.ifPresent(capability -> Objects.requireNonNull(capability, "requiredCapability value"));

        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one input");
        }
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one output");
        }
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(yieldRatio, "yieldRatio");
        metadata = copyMetadata(metadata);

        validateNoDuplicateInputs(inputs);
        validateNoDuplicateOutputs(outputs);
        validateYieldConsistency(inputs, outputs, yieldRatio);
    }

    public TransformationDefinition(
            TransformationId id,
            List<TransformationInput> inputs,
            List<TransformationOutput> outputs,
            ProcessingDuration duration,
            Optional<EngineId> workstationCapability
    ) {
        this(
                id,
                id.value(),
                CURRENT_SCHEMA_VERSION,
                workstationCapability,
                inputs,
                outputs,
                duration,
                deriveYield(inputs, outputs),
                Map.of()
        );
    }

    public TransformationDefinition(
            TransformationId id,
            String displayName,
            List<TransformationInput> inputs,
            List<TransformationOutput> outputs,
            ProcessingDuration duration,
            Optional<EngineId> requiredCapability
    ) {
        this(
                id,
                displayName,
                CURRENT_SCHEMA_VERSION,
                requiredCapability,
                inputs,
                outputs,
                duration,
                deriveYield(inputs, outputs),
                Map.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<EngineId> workstationCapability() {
        return requiredCapability;
    }

    public YieldRatio yield() {
        return yieldRatio;
    }

    public TransformationDefinition withInputQuantity(ProductQuantity inputQuantity) {
        Objects.requireNonNull(inputQuantity, "inputQuantity");
        if (inputs.size() != 1) {
            throw new IllegalStateException("Only single-input transformation rebasing is supported");
        }

        TransformationInput input = inputs.getFirst();
        ProductQuantity basisQuantity = input.requiredAmount().quantity();
        if (basisQuantity.unit() != inputQuantity.unit()) {
            throw new IllegalArgumentException("Rebased input quantity must use the definition input unit");
        }

        List<TransformationOutput> rebasedOutputs = outputs.stream()
                .map(output -> new TransformationOutput(
                        new MaterialAmount(
                                output.producedAmount().materialId(),
                                scaleQuantity(output.producedAmount().quantity(), basisQuantity, inputQuantity)
                        ),
                        output.classification()
                ))
                .toList();

        return new TransformationDefinition(
                id,
                displayName,
                schemaVersion,
                requiredCapability,
                List.of(new TransformationInput(new MaterialAmount(input.requiredAmount().materialId(), inputQuantity))),
                rebasedOutputs,
                duration,
                yieldRatio,
                metadata
        );
    }

    private static void validateNoDuplicateInputs(List<TransformationInput> inputs) {
        Set<EngineId> seenMaterials = new HashSet<>();
        for (TransformationInput input : inputs) {
            EngineId materialId = input.requiredAmount().materialId();
            if (!seenMaterials.add(materialId)) {
                throw new IllegalArgumentException("Duplicate transformation input material: " + materialId.value());
            }
        }
    }

    private static void validateNoDuplicateOutputs(List<TransformationOutput> outputs) {
        Set<EngineId> seenMaterials = new HashSet<>();
        for (TransformationOutput output : outputs) {
            EngineId materialId = output.producedAmount().materialId();
            if (!seenMaterials.add(materialId)) {
                throw new IllegalArgumentException("Duplicate or contradictory transformation output material: " + materialId.value());
            }
        }
    }

    private static void validateYieldConsistency(
            List<TransformationInput> inputs,
            List<TransformationOutput> outputs,
            YieldRatio yield
    ) {
        Optional<QuantityUnit> commonUnit = commonQuantityUnit(inputs, outputs);
        if (commonUnit.isEmpty()) {
            return;
        }

        ProductQuantity expectedOutput = yield.apply(new ProductQuantity(safeLong(totalInput(inputs), "Total input quantity"), commonUnit.orElseThrow()));
        long actualOutput = safeLong(totalOutput(outputs), "Total output quantity");
        if (expectedOutput.amount() != actualOutput) {
            throw new IllegalArgumentException("Transformation yield must match declared output quantities");
        }
    }

    private static Optional<QuantityUnit> commonQuantityUnit(List<TransformationInput> inputs, List<TransformationOutput> outputs) {
        QuantityUnit unit = inputs.getFirst().requiredAmount().quantity().unit();
        for (TransformationInput input : inputs) {
            if (input.requiredAmount().quantity().unit() != unit) {
                return Optional.empty();
            }
        }
        for (TransformationOutput output : outputs) {
            if (output.producedAmount().quantity().unit() != unit) {
                return Optional.empty();
            }
        }
        return Optional.of(unit);
    }

    private static YieldRatio deriveYield(List<TransformationInput> inputs, List<TransformationOutput> outputs) {
        BigInteger inputQuantity = totalInput(inputs);
        BigInteger outputQuantity = totalOutput(outputs);
        BigInteger gcd = inputQuantity.gcd(outputQuantity);
        return new YieldRatio(
                outputQuantity.divide(gcd).longValueExact(),
                inputQuantity.divide(gcd).longValueExact()
        );
    }

    private static BigInteger totalInput(List<TransformationInput> inputs) {
        List<TransformationInput> copiedInputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (copiedInputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one input");
        }
        BigInteger total = BigInteger.ZERO;
        for (TransformationInput input : copiedInputs) {
            total = total.add(BigInteger.valueOf(input.requiredAmount().quantity().amount()));
        }
        return total;
    }

    private static BigInteger totalOutput(List<TransformationOutput> outputs) {
        List<TransformationOutput> copiedOutputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (copiedOutputs.isEmpty()) {
            throw new IllegalArgumentException("Transformation must define at least one output");
        }
        BigInteger total = BigInteger.ZERO;
        for (TransformationOutput output : copiedOutputs) {
            total = total.add(BigInteger.valueOf(output.producedAmount().quantity().amount()));
        }
        return total;
    }

    private static ProductQuantity scaleQuantity(
            ProductQuantity outputQuantity,
            ProductQuantity basisInputQuantity,
            ProductQuantity targetInputQuantity
    ) {
        if (outputQuantity.unit() != basisInputQuantity.unit() || targetInputQuantity.unit() != basisInputQuantity.unit()) {
            throw new IllegalArgumentException("Transformation quantities must use matching units to be rebased");
        }

        BigInteger raw = BigInteger.valueOf(targetInputQuantity.amount()).multiply(BigInteger.valueOf(outputQuantity.amount()));
        BigInteger divisor = BigInteger.valueOf(basisInputQuantity.amount());
        BigInteger[] divided = raw.divideAndRemainder(divisor);
        BigInteger rounded = divided[0];
        if (divided[1].multiply(BigInteger.TWO).compareTo(divisor) >= 0) {
            rounded = rounded.add(BigInteger.ONE);
        }
        if (rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Rebased transformation quantity exceeds supported range");
        }
        return new ProductQuantity(rounded.longValueExact(), outputQuantity.unit());
    }

    private static Map<EngineId, String> copyMetadata(Map<EngineId, String> source) {
        LinkedHashMap<EngineId, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(source, "metadata").entrySet()) {
            EngineId key = Objects.requireNonNull(entry.getKey(), "metadata key");
            String value = Objects.requireNonNull(entry.getValue(), "metadata value").strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Transformation metadata values cannot be blank");
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }

    private static long safeLong(BigInteger value, String description) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException(description + " exceeds supported range");
        }
        return value.longValueExact();
    }

    public static final class Builder {
        private TransformationId id;
        private String displayName;
        private Integer schemaVersion;
        private Optional<EngineId> requiredCapability;
        private boolean requiredCapabilityConfigured;
        private final List<TransformationInput> inputs = new ArrayList<>();
        private final List<TransformationOutput> outputs = new ArrayList<>();
        private ProcessingDuration duration;
        private YieldRatio yield;
        private final Map<EngineId, String> metadata = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder id(TransformationId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder id(String id) {
            return id(TransformationId.of(id));
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder requiredCapability(EngineId requiredCapability) {
            this.requiredCapability = Optional.of(Objects.requireNonNull(requiredCapability, "requiredCapability"));
            this.requiredCapabilityConfigured = true;
            return this;
        }

        public Builder requiredCapability(String requiredCapability) {
            return requiredCapability(EngineId.of(requiredCapability));
        }

        public Builder requiredCapability(Optional<EngineId> requiredCapability) {
            this.requiredCapability = Objects.requireNonNull(requiredCapability, "requiredCapability");
            this.requiredCapability.ifPresent(capability -> Objects.requireNonNull(capability, "requiredCapability value"));
            this.requiredCapabilityConfigured = true;
            return this;
        }

        public Builder noRequiredCapability() {
            this.requiredCapability = Optional.empty();
            this.requiredCapabilityConfigured = true;
            return this;
        }

        public Builder input(TransformationInput input) {
            inputs.add(Objects.requireNonNull(input, "input"));
            return this;
        }

        public Builder input(EngineId materialId, ProductQuantity quantity) {
            return input(new TransformationInput(new MaterialAmount(materialId, quantity)));
        }

        public Builder output(TransformationOutput output) {
            outputs.add(Objects.requireNonNull(output, "output"));
            return this;
        }

        public Builder output(
                EngineId materialId,
                ProductQuantity quantity,
                TransformationOutputClassification classification
        ) {
            return output(new TransformationOutput(new MaterialAmount(materialId, quantity), classification));
        }

        public Builder duration(ProcessingDuration duration) {
            this.duration = Objects.requireNonNull(duration, "duration");
            return this;
        }

        public Builder yield(YieldRatio yield) {
            this.yield = Objects.requireNonNull(yield, "yield");
            return this;
        }

        public Builder metadata(EngineId key, String value) {
            metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder metadata(String key, String value) {
            return metadata(EngineId.of(key), value);
        }

        public Builder metadata(Map<EngineId, String> metadata) {
            this.metadata.putAll(Objects.requireNonNull(metadata, "metadata"));
            return this;
        }

        public TransformationDefinition build() {
            require(id != null, "Transformation id is required");
            require(displayName != null, "Transformation display name is required");
            require(schemaVersion != null, "Transformation schema version is required");
            require(requiredCapabilityConfigured, "Transformation required capability must be configured");
            require(duration != null, "Transformation duration is required");
            require(yield != null, "Transformation yield is required");
            return new TransformationDefinition(
                    id,
                    displayName,
                    schemaVersion,
                    requiredCapability,
                    inputs,
                    outputs,
                    duration,
                    yield,
                    metadata
            );
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }
    }
}
