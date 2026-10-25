package com.example.aml.rules;

import com.example.aml.api.dto.ScreeningMatchDto;
import com.example.aml.domain.RuleHit;
import com.example.aml.screening.SanctionsScreeningService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Real-time counterparty screening. A hit always blocks the transaction (see RuleEngine). */
@Component
public class SanctionsMatchRule implements AmlRule {

    public static final String CODE = "SANCTIONS_MATCH";

    private final SanctionsScreeningService screening;

    public SanctionsMatchRule(SanctionsScreeningService screening) {
        this.screening = screening;
    }

    @Override public String code() { return CODE; }
    @Override public String name() { return "Sanctions list match"; }
    @Override public String category() { return "Screening"; }
    @Override public int baseScore() { return 100; }

    @Override
    public String description() {
        return "Counterparty name fuzzy-matches an entry on the sanctions list. The transaction is held "
                + "until an analyst confirms or discounts the match.";
    }

    @Override
    public String parameters() {
        return "similarity >= " + screening.threshold();
    }

    @Override
    public Optional<RuleHit> evaluate(RuleContext ctx) {
        ScreeningMatchDto match = screening.bestHit(ctx.transaction().getCounterpartyName());
        if (match == null) {
            return Optional.empty();
        }
        return hit("Counterparty \"" + ctx.transaction().getCounterpartyName() + "\" matches listed "
                + (match.entityType() == null ? "party" : match.entityType().toLowerCase()) + " \""
                + match.listedName() + "\" (" + match.listSource()
                + (match.program() == null ? "" : ", " + match.program()) + ") with "
                + Math.round(match.score() * 100) + "% similarity.");
    }
}
