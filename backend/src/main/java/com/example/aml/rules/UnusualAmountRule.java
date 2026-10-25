package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Compares the amount against the customer's own baseline instead of a fixed threshold. */
@Component
public class UnusualAmountRule implements AmlRule {

    private final AmlProperties props;

    public UnusualAmountRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "UNUSUAL_AMOUNT"; }
    @Override public String name() { return "Out of profile amount"; }
    @Override public String category() { return "Behavioural"; }
    @Override public int baseScore() { return 35; }

    @Override
    public String description() {
        return "Amount is far above the customer's own average over the look-back period.";
    }

    @Override
    public String parameters() {
        AmlProperties.UnusualAmount u = props.rules().unusualAmount();
        return "> " + u.multiplier() + "x the " + u.lookbackDays() + "-day average (needs " + u.minHistory()
                + "+ prior transactions)";
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        AmlProperties.UnusualAmount cfg = props.rules().unusualAmount();
        List<Transaction> past = ctx.within(Duration.ofDays(cfg.lookbackDays()));
        if (past.size() < cfg.minHistory()) {
            return Optional.empty();
        }
        BigDecimal avg = RuleContext.sum(past).divide(BigDecimal.valueOf(past.size()), 2, RoundingMode.HALF_UP);
        BigDecimal amount = ctx.transaction().getAmount();
        if (avg.signum() > 0 && amount.compareTo(avg.multiply(BigDecimal.valueOf(cfg.multiplier()))) > 0) {
            BigDecimal ratio = amount.divide(avg, 1, RoundingMode.HALF_UP);
            return hit("Amount " + RuleContext.money(amount) + " is " + ratio + "x the customer's "
                    + cfg.lookbackDays() + "-day average of " + RuleContext.money(avg) + " (" + past.size()
                    + " transactions).");
        }
        return Optional.empty();
    }
}
