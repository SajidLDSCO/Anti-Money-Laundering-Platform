package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PepTransactionRule implements AmlRule {

    private final AmlProperties props;

    public PepTransactionRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "PEP_TRANSACTION"; }
    @Override public String name() { return "PEP transaction"; }
    @Override public String category() { return "Customer"; }
    @Override public int baseScore() { return 25; }

    @Override
    public String description() {
        return "Significant transaction by a politically exposed person; requires enhanced due diligence "
                + "on source of funds.";
    }

    @Override
    public String parameters() {
        return "amount >= " + RuleContext.money(props.rules().pepThreshold());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        Transaction tx = ctx.transaction();
        if (ctx.customer().isPep() && tx.getAmount().compareTo(props.rules().pepThreshold()) >= 0) {
            return hit("Customer is a politically exposed person; transaction of "
                    + RuleContext.money(tx.getAmount()) + " " + tx.getCurrency() + ".");
        }
        return Optional.empty();
    }
}
