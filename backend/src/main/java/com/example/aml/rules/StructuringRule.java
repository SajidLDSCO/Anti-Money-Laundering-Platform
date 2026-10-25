package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import com.example.aml.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects "smurfing": several cash deposits each kept just under the reporting threshold
 * that together exceed it.
 */
@Component
public class StructuringRule implements AmlRule {

    private final AmlProperties props;

    public StructuringRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "STRUCTURING"; }
    @Override public String name() { return "Possible structuring"; }
    @Override public String category() { return "Behavioural"; }
    @Override public int baseScore() { return 60; }

    @Override
    public String description() {
        return "Multiple cash deposits just below the reporting threshold within a short window whose total "
                + "exceeds it - a classic pattern for avoiding currency transaction reports.";
    }

    @Override
    public String parameters() {
        AmlProperties.Structuring s = props.rules().structuring();
        return "window = " + s.windowHours() + "h, min deposits = " + s.minTransactions()
                + ", band = " + Math.round(s.lowerBoundRatio() * 100) + "-100% of "
                + RuleContext.money(props.rules().largeCashThreshold());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        AmlProperties.Structuring cfg = props.rules().structuring();
        BigDecimal threshold = props.rules().largeCashThreshold();
        BigDecimal lower = threshold.multiply(BigDecimal.valueOf(cfg.lowerBoundRatio()));

        if (!inBand(ctx.transaction(), lower, threshold)) {
            return Optional.empty();
        }
        List<Transaction> deposits = new ArrayList<>(ctx.within(Duration.ofHours(cfg.windowHours()),
                t -> inBand(t, lower, threshold)));
        deposits.add(ctx.transaction());

        BigDecimal total = RuleContext.sum(deposits);
        if (deposits.size() >= cfg.minTransactions() && total.compareTo(threshold) >= 0) {
            int score = baseScore() + Math.min(20, (deposits.size() - cfg.minTransactions()) * 5);
            return hit(score, deposits.size() + " cash deposits between " + RuleContext.money(lower) + " and "
                    + RuleContext.money(threshold) + " in " + cfg.windowHours() + "h, totalling "
                    + RuleContext.money(total) + " - each individually below the reporting threshold.");
        }
        return Optional.empty();
    }

    private static boolean inBand(Transaction t, BigDecimal lower, BigDecimal upper) {
        return t.getType() == TransactionType.CASH_DEPOSIT
                && t.getAmount().compareTo(lower) >= 0
                && t.getAmount().compareTo(upper) < 0;
    }
}
