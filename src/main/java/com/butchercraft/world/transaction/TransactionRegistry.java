package com.butchercraft.world.transaction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class TransactionRegistry {
    private final Map<TransactionId, EconomicTransaction> transactions = new LinkedHashMap<>();

    public TransactionRegistry() {
    }

    public TransactionRegistry(Collection<EconomicTransaction> loadedTransactions) {
        Objects.requireNonNull(loadedTransactions, "loadedTransactions").forEach(this::register);
    }

    public synchronized EconomicTransaction register(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        EconomicTransaction previous = transactions.putIfAbsent(transaction.id(), transaction);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate transaction id: " + transaction.id().value());
        }
        return transaction;
    }

    public synchronized EconomicTransaction replace(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transactions.containsKey(transaction.id())) {
            throw new IllegalArgumentException("Cannot replace unknown transaction: " + transaction.id().value());
        }
        transactions.put(transaction.id(), transaction);
        return transaction;
    }

    public synchronized boolean contains(TransactionId id) {
        return transactions.containsKey(Objects.requireNonNull(id, "id"));
    }

    public synchronized Optional<EconomicTransaction> find(TransactionId id) {
        return Optional.ofNullable(transactions.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized int size() {
        return transactions.size();
    }

    public synchronized List<EconomicTransaction> history() {
        return List.copyOf(transactions.values());
    }

    public synchronized Stream<EconomicTransaction> stream() {
        return history().stream();
    }

    public synchronized List<EconomicTransaction> findByType(TransactionType type) {
        Objects.requireNonNull(type, "type");
        return transactions.values().stream().filter(transaction -> transaction.type() == type).toList();
    }

    public synchronized List<EconomicTransaction> findByStatus(TransactionStatus status) {
        Objects.requireNonNull(status, "status");
        return transactions.values().stream().filter(transaction -> transaction.status() == status).toList();
    }
}
