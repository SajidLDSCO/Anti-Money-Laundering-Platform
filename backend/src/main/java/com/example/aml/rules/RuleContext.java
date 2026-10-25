package com.example.aml.rules;

import com.example.aml.domain.Customer;
import com.example.aml.domain.Transaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/**
 * Everything a rule may look at: the transaction under evaluation (not yet saved), its
 * customer, and that customer's earlier transactions across all accounts. All time windows
 * are relative to the transaction's own timestamp, so back-dated batch loads behave the
 * same as real-time submissions.
 */
public record RuleContext(Transaction transaction, Customer customer, List<Transaction> history) {

    public Instant now() {
        return transaction.getTimestamp();
    }

    /** Earlier transactions inside the window ending at this transaction. */
    public List<Transaction> within(Duration window) {
        Instant from = now().minus(window);
        return history.stream()
                .filter(t -> !t.getTimestamp().isBefore(from) && !t.getTimestamp().isAfter(now()))
                .toList();
    }

    public List<Transaction> within(Duration window, Predicate<Transaction> filter) {
        return within(window).stream().filter(filter).toList();
    }

    public static BigDecimal sum(List<Transaction> txs) {
        return txs.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String money(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
