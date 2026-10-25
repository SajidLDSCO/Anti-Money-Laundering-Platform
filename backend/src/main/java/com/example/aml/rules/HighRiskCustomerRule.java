package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RiskLevel;
import com.example.aml.domain.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HighRiskCustomerRule implements AmlRule {

    private final AmlProperties props;

    public HighRiskCustomerRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "HIGH_RISK_CUSTOMER"; }
    @Override public String name() { return "High-risk customer"; }
    @Override public String category() { return "Customer"; }
    @Override public int baseScore() { return 15; }

    @Override
    public String description() {
        return "Material transaction by a customer currently rated HIGH risk. Adds weight to other findings.";
    }

    @Override
    public String parameters() {
        return "customer risk = HIGH and amount >= " + RuleContext.money(props.rules().roundAmountMinimum());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        if (ctx.customer().getRiskLevel() == RiskLevel.HIGH
                && ctx.transaction().getAmount().compareTo(props.rules().roundAmountMinimum()) >= 0) {
            return hit("Customer is rated HIGH risk (score " + ctx.customer().getRiskScore() + ").");
        }
        return Optional.empty();
    }
}
