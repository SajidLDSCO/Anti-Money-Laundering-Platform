package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class LargeCashRule implements AmlRule {

    private final AmlProperties props;

    public LargeCashRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "LARGE_CASH"; }
    @Override public String name() { return "Large cash transaction"; }
    @Override public String category() { return "Threshold"; }
    @Override public int baseScore() { return 30; }

    @Override
    public String description() {
        return "Cash deposit or withdrawal at or above the currency transaction reporting threshold. "
                + "Usually a mandatory report (CTR) rather than suspicion on its own.";
    }

    @Override
    public String parameters() {
        return "threshold = " + RuleContext.money(threshold());
    }

    private BigDecimal threshold() {
        return props.rules().largeCashThreshold();
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        Transaction tx = ctx.transaction();
        if (tx.getType().isCash() && tx.getAmount().compareTo(threshold()) >= 0) {
            return hit("Cash " + (tx.getType().isInbound() ? "deposit" : "withdrawal") + " of "
                    + RuleContext.money(tx.getAmount()) + " " + tx.getCurrency()
                    + " meets the reporting threshold of " + RuleContext.money(threshold()) + ".");
        }
        return Optional.empty();
    }
}
