package com.butchercraft.content;

import com.butchercraft.product.datapack.ProductDatapackValidationError;
import com.butchercraft.transformation.datapack.TransformationDatapackValidationError;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of assembling a product and transformation content snapshot.
 */
public record ContentSnapshotLoadResult(
        Optional<ContentSnapshot> snapshot,
        List<ProductDatapackValidationError> productErrors,
        List<TransformationDatapackValidationError> transformationErrors
) {
    public ContentSnapshotLoadResult {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        productErrors = List.copyOf(Objects.requireNonNull(productErrors, "productErrors"));
        transformationErrors = List.copyOf(Objects.requireNonNull(transformationErrors, "transformationErrors"));
        boolean hasErrors = !productErrors.isEmpty() || !transformationErrors.isEmpty();
        if ((snapshot.isPresent() && hasErrors) || (snapshot.isEmpty() && !hasErrors)) {
            throw new IllegalArgumentException("Content snapshot load result must contain either a snapshot or errors");
        }
    }

    public static ContentSnapshotLoadResult success(ContentSnapshot snapshot) {
        return new ContentSnapshotLoadResult(Optional.of(snapshot), List.of(), List.of());
    }

    public static ContentSnapshotLoadResult failure(
            List<ProductDatapackValidationError> productErrors,
            List<TransformationDatapackValidationError> transformationErrors
    ) {
        return new ContentSnapshotLoadResult(Optional.empty(), productErrors, transformationErrors);
    }

    public boolean succeeded() {
        return snapshot.isPresent();
    }

    public String describeErrors() {
        StringBuilder builder = new StringBuilder();
        productErrors.forEach(error -> append(builder, "product", error.source(), error.code().name(),
                error.productId().orElse(null), error.message()));
        transformationErrors.forEach(error -> append(builder, "transformation", error.source(), error.code().name(),
                error.transformationId().orElse(null), error.message()));
        if (builder.isEmpty()) {
            return "No content datapack errors";
        }
        return builder.toString();
    }

    private static void append(
            StringBuilder builder,
            String domain,
            String source,
            String code,
            String id,
            String message
    ) {
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(domain)
                .append(": ")
                .append(source)
                .append(" [")
                .append(code)
                .append("] ");
        if (id != null) {
            builder.append(id).append(": ");
        }
        builder.append(message);
    }
}
