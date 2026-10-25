package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class VelocityRule implements AmlRule {

    private final AmlProperties props;

    public VelocityRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "HIGH_VELOCITY"; }
    @Override public String name() { return "High transaction velocity"; }
    @Override public String category() { return "Behavioural"; }
    @Override public int baseScore() { return 35; }

    @Override
    public String description() {
        return "Burst of transactions in a short window, or 24-hour volume above the daily limit - "
                + "typical of account takeover, mule activity or layering.";
    }

    @Override
    public String parameters() {
        AmlProperties.Velocity v = props.rules().velocity();
        return "> " + v.maxTransactions() + " tx in " + v.windowHours() + "h, or 24h volume > "
                + RuleContext.money(v.dailyVolumeLimit());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        AmlProperties.Velocity cfg = props.rules().velocity();
        List<String> reasons = new ArrayList<>();

        int burst = ctx.within(Duration.ofHours(cfg.windowHours())).size() + 1;
        if (burst > cfg.maxTransactions()) {
            reasons.add(burst + " transactions within " + cfg.windowHours() + "h (limit " + cfg.maxTransactions() + ")");
        }

        List<Transaction> day = new ArrayList<>(ctx.within(Duration.ofHours(24)));
        day.add(ctx.transaction());
        BigDecimal volume = RuleContext.sum(day);
        if (volume.compareTo(cfg.dailyVolumeLimit()) > 0) {
            reasons.add("24h volume of " + RuleContext.money(volume) + " exceeds "
                    + RuleContext.money(cfg.dailyVolumeLimit()));
        }

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return hit(baseScore() + (reasons.size() - 1) * 15, String.join("; ", reasons) + ".");
    }
}
