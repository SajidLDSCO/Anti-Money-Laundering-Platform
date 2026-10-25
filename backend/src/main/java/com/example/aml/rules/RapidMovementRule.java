package com.example.aml.rules;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;

/** Pass-through / layering: funds arrive and most of them leave again almost immediately. */
@Component
public class RapidMovementRule implements AmlRule {

    private final AmlProperties props;

    public RapidMovementRule(AmlProperties props) {
        this.props = props;
    }

    @Override public String code() { return "RAPID_MOVEMENT"; }
    @Override public String name() { return "Rapid movement of funds"; }
    @Override public String category() { return "Behavioural"; }
    @Override public int baseScore() { return 50; }

    @Override
    public String description() {
        return "An outgoing payment that moves on most of a recent incoming credit - the account is being "
                + "used as a pass-through (layering / money mule behaviour).";
    }

    @Override
    public String parameters() {
        AmlProperties.RapidMovement r = props.rules().rapidMovement();
        return "out >= " + Math.round(r.matchRatio() * 100) + "% of an inbound credit within "
                + r.windowHours() + "h, min " + RuleContext.money(r.minimumAmount());
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        AmlProperties.RapidMovement cfg = props.rules().rapidMovement();
        Transaction out = ctx.transaction();
        if (out.getType().isInbound() || out.getAmount().compareTo(cfg.minimumAmount()) < 0) {
            return Optional.empty();
        }
        BigDecimal lowerOut = BigDecimal.valueOf(cfg.matchRatio());
        BigDecimal upperOut = new BigDecimal("1.10");

        return ctx.within(Duration.ofHours(cfg.windowHours()), t -> t.getType().isInbound()).stream()
                .filter(in -> out.getAmount().compareTo(in.getAmount().multiply(lowerOut)) >= 0
                        && out.getAmount().compareTo(in.getAmount().multiply(upperOut)) <= 0)
                .max(Comparator.comparing(Transaction::getTimestamp))
                .flatMap(in -> {
                    long hours = ChronoUnit.HOURS.between(in.getTimestamp(), out.getTimestamp());
                    return hit("Outgoing " + RuleContext.money(out.getAmount()) + " follows an incoming "
                            + RuleContext.money(in.getAmount()) + " (" + in.getType() + " " + in.getReference()
                            + ") received " + hours + "h earlier.");
                });
    }
}
