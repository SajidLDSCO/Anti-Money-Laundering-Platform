package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HighRiskJurisdictionRule implements AmlRule {

    private final AmlProperties props;

    public HighRiskJurisdictionRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "HIGH_RISK_JURISDICTION"; }
    @Override public String name() { return "High-risk jurisdiction"; }
    @Override public String category() { return "Geographic"; }
    @Override public int baseScore() { return 45; }

    @Override
    public String description() {
        return "Counterparty located in a jurisdiction on the high-risk (call for action) list, or on the "
                + "increased-monitoring list (lower score).";
    }

    @Override
    public String parameters() {
        return "high-risk = " + props.highRiskCountries() + ", increased monitoring = "
                + props.increasedMonitoringCountries();
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        Transaction tx = ctx.transaction();
        String country = tx.getCounterpartyCountry();
        if (props.isHighRiskCountry(country)) {
            return hit("Counterparty country " + country.toUpperCase() + " is on the high-risk jurisdiction list.");
        }
        if (props.isIncreasedMonitoringCountry(country)) {
            return hit(25, "Counterparty country " + country.toUpperCase()
                    + " is under increased monitoring.");
        }
        return Optional.empty();
    }
}
