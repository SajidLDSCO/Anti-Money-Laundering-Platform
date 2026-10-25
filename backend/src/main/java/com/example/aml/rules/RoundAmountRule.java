package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class RoundAmountRule implements AmlRule {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private final AmlProperties props;

    public RoundAmountRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "ROUND_AMOUNT"; }
    @Override public String name() { return "Large round amount"; }
    @Override public String category() { return "Threshold"; }
    @Override public int baseScore() { return 10; }

    @Override
    public String description() {
        return "Large, exactly round amounts are rare in genuine commerce. Weak signal on its own; "
                + "raises the score of other findings.";
    }

    @Override
    public String parameters() {
        return "multiple of 1,000 and >= " + RuleContext.money(props.rules().roundAmountMinimum());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        Transaction tx = ctx.transaction();
        BigDecimal amount = tx.getAmount();
        if (amount.compareTo(props.rules().roundAmountMinimum()) >= 0
                && amount.remainder(THOUSAND).compareTo(BigDecimal.ZERO) == 0) {
            return hit("Round amount of " + RuleContext.money(amount) + " " + tx.getCurrency() + ".");
        }
        return Optional.empty();
    }
}
