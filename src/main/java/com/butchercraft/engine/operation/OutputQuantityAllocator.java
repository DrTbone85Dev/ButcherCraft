package com.butchercraft.engine.operation;

import com.butchercraft.engine.quantity.ProductQuantity;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Deterministic exact quantity allocator for ordered operation outputs.
 */
public final class OutputQuantityAllocator {
    private OutputQuantityAllocator() {
    }

    public static List<ProductQuantity> allocate(
            ProductQuantity inputQuantity,
            List<ProcessingOutputDefinition> outputs,
            int yieldBasisPointsDelta
    ) {
        Objects.requireNonNull(inputQuantity, "inputQuantity");
        List<ProcessingOutputDefinition> copiedOutputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (copiedOutputs.isEmpty()) {
            throw new IllegalArgumentException("Operation must define at least one output");
        }
        copiedOutputs.forEach(output -> {
            if (output.quantityUnit() != inputQuantity.unit()) {
                throw new IllegalArgumentException("Output quantity unit must match input quantity unit");
            }
        });

        List<ProductQuantity> quantities = copiedOutputs.size() == 1
                ? allocateSingle(inputQuantity, copiedOutputs.getFirst(), yieldBasisPointsDelta)
                : allocateMultiple(inputQuantity, copiedOutputs, yieldBasisPointsDelta);
        ProductQuantity total = sum(quantities);
        if (total.amount() > inputQuantity.amount()) {
            throw new IllegalArgumentException("Total output quantity cannot exceed input quantity");
        }
        return quantities;
    }

    public static ProductQuantity total(
            ProductQuantity inputQuantity,
            List<ProcessingOutputDefinition> outputs,
            int yieldBasisPointsDelta
    ) {
        return sum(allocate(inputQuantity, outputs, yieldBasisPointsDelta));
    }

    private static List<ProductQuantity> allocateSingle(
            ProductQuantity inputQuantity,
            ProcessingOutputDefinition output,
            int yieldBasisPointsDelta
    ) {
        return List.of(output.yield().apply(inputQuantity, yieldBasisPointsDelta));
    }

    private static List<ProductQuantity> allocateMultiple(
            ProductQuantity inputQuantity,
            List<ProcessingOutputDefinition> outputs,
            int yieldBasisPointsDelta
    ) {
        if (yieldBasisPointsDelta != 0) {
            throw new IllegalArgumentException("Yield modifiers are not supported for multi-output operations");
        }

        BigInteger commonDenominator = commonDenominator(outputs);
        BigInteger totalNumerator = BigInteger.ZERO;
        List<ExactOutput> exactOutputs = IntStream.range(0, outputs.size())
                .mapToObj(index -> {
                    ProcessingOutputDefinition output = outputs.get(index);
                    BigInteger denominator = BigInteger.valueOf(output.yield().denominator());
                    BigInteger scaledNumerator = BigInteger.valueOf(inputQuantity.amount())
                            .multiply(BigInteger.valueOf(output.yield().numerator()));
                    BigInteger[] divided = scaledNumerator.divideAndRemainder(denominator);
                    return new ExactOutput(index, divided[0], divided[1], denominator);
                })
                .toList();

        for (ProcessingOutputDefinition output : outputs) {
            BigInteger denominator = BigInteger.valueOf(output.yield().denominator());
            totalNumerator = totalNumerator.add(BigInteger.valueOf(inputQuantity.amount())
                    .multiply(BigInteger.valueOf(output.yield().numerator()))
                    .multiply(commonDenominator.divide(denominator)));
        }

        BigInteger intendedTotal = totalNumerator.divide(commonDenominator);
        BigInteger floorTotal = exactOutputs.stream()
                .map(ExactOutput::floor)
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger residue = intendedTotal.subtract(floorTotal);
        if (residue.signum() < 0 || residue.compareTo(BigInteger.valueOf(outputs.size())) > 0) {
            throw new ArithmeticException("Output allocation residue is outside supported bounds");
        }

        long[] amounts = new long[outputs.size()];
        for (ExactOutput exactOutput : exactOutputs) {
            amounts[exactOutput.index()] = longValue(exactOutput.floor());
        }

        List<ExactOutput> remainderOrder = exactOutputs.stream()
                .sorted(OutputQuantityAllocator::compareRemaindersDescending)
                .toList();
        for (int i = 0; i < residue.intValueExact(); i++) {
            int outputIndex = remainderOrder.get(i).index();
            amounts[outputIndex] = Math.addExact(amounts[outputIndex], 1L);
        }

        return IntStream.range(0, outputs.size())
                .mapToObj(index -> new ProductQuantity(amounts[index], inputQuantity.unit()))
                .toList();
    }

    private static ProductQuantity sum(List<ProductQuantity> quantities) {
        if (quantities.isEmpty()) {
            throw new IllegalArgumentException("Cannot sum an empty output quantity list");
        }
        ProductQuantity total = new ProductQuantity(0, quantities.getFirst().unit());
        for (ProductQuantity quantity : quantities) {
            total = total.add(quantity);
        }
        return total;
    }

    private static BigInteger commonDenominator(List<ProcessingOutputDefinition> outputs) {
        BigInteger denominator = BigInteger.ONE;
        for (ProcessingOutputDefinition output : outputs) {
            denominator = lcm(denominator, BigInteger.valueOf(output.yield().denominator()));
        }
        return denominator;
    }

    private static BigInteger lcm(BigInteger first, BigInteger second) {
        return first.divide(first.gcd(second)).multiply(second);
    }

    private static long longValue(BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Yield result exceeds supported quantity range");
        }
        return value.longValueExact();
    }

    private static int compareRemaindersDescending(ExactOutput first, ExactOutput second) {
        int remainderPresence = Boolean.compare(second.hasRemainder(), first.hasRemainder());
        if (remainderPresence != 0) {
            return remainderPresence;
        }
        int remainderComparison = second.remainderNumerator()
                .multiply(first.remainderDenominator())
                .compareTo(first.remainderNumerator().multiply(second.remainderDenominator()));
        if (remainderComparison != 0) {
            return remainderComparison;
        }
        return Integer.compare(first.index(), second.index());
    }

    private record ExactOutput(
            int index,
            BigInteger floor,
            BigInteger remainderNumerator,
            BigInteger remainderDenominator
    ) {
        boolean hasRemainder() {
            return remainderNumerator.signum() > 0;
        }
    }
}
