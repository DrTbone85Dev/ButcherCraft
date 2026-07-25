package com.butchercraft.machine.grinder.execution;

import com.butchercraft.engine.product.Product;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.ResolvedWorkstationOperation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class GrinderExecutionIdentities {
    private GrinderExecutionIdentities() {
    }

    static String inputIdentity(List<ItemStack> inputs) {
        CanonicalDigest digest = CanonicalDigest.create("butchercraft:grinder_input_identity")
                .add(inputs.size());
        for (ItemStack stack : inputs) {
            ProductStackData data = ProductStackAdapter.readProductData(stack).orThrow();
            digest.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    .add(stack.getCount())
                    .add(data.productTypeId())
                    .add(data.sourceCategoryId())
                    .add(data.processingStateId())
                    .add(data.quantityValue())
                    .add(data.quantityUnitId())
                    .add(data.qualityScore())
                    .add(data.packaging().isPresent());
            data.packaging().ifPresent(packaging -> digest
                    .add(packaging.packagingDefinitionId())
                    .add(packaging.packagingFormatId())
                    .add(packaging.sourceProductId()));
        }
        return "butchercraft:workstation_input/v1/" + digestIdSuffix(digest.finish());
    }

    static String expectedOutputIdentity(List<Product> expectedOutputs) {
        CanonicalDigest digest = CanonicalDigest.create("butchercraft:grinder_expected_output_identity")
                .add(expectedOutputs.size());
        for (Product product : expectedOutputs) {
            digest.add(product.typeId().value())
                    .add(product.sourceCategory().id().value())
                    .add(product.processingState().id().value())
                    .add(product.quantity().amount())
                    .add(product.quantity().unit().id())
                    .add(product.quality().score());
        }
        return "butchercraft:workstation_output/v1/" + digestIdSuffix(digest.finish());
    }

    static String sourceFreshnessIdentity(
            String workstationIdentity,
            ResolvedWorkstationOperation operation,
            String frozenInputIdentity,
            String expectedOutputIdentity
    ) {
        String digest = CanonicalDigest.create("butchercraft:grinder_slot_freshness")
                .add(workstationIdentity)
                .add(operation.operationId().toString())
                .add(frozenInputIdentity)
                .add(expectedOutputIdentity)
                .add("output_empty")
                .finish();
        return "butchercraft:workstation_freshness/v1/" + digestIdSuffix(digest);
    }

    private static String digestIdSuffix(String digest) {
        return digest.substring("sha256:".length());
    }

    private static final class CanonicalDigest {
        private final MessageDigest digest;

        private CanonicalDigest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        static CanonicalDigest create(String domain) {
            return new CanonicalDigest(domain);
        }

        CanonicalDigest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) bytes.length);
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 24));
            digest.update(bytes);
            return this;
        }

        CanonicalDigest add(long value) {
            return add(Long.toString(value));
        }

        CanonicalDigest add(int value) {
            return add(Integer.toString(value));
        }

        CanonicalDigest add(boolean value) {
            return add(Boolean.toString(value));
        }

        String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
