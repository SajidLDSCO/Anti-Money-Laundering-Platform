package com.example.aml.rules;

import com.example.aml.domain.RuleHit;

import java.util.Optional;

/**
 * A single detection scenario. Implementations are Spring beans and are picked up by
 * {@link RuleEngine} automatically, so adding a scenario means adding one class.
 */
public interface AmlRule {

    /** Stable identifier, stored on alerts. Never rename once in production. */
    String code();

    String name();

    String category();

    String description();

    /** Current thresholds, human-readable, for the rules screen. */
    String parameters();

    int baseScore();

    Optional<RuleHit> evaluate(RuleContext ctx);

    default Optional<RuleHit> hit(String reason) {
        return hit(baseScore(), reason);
    }

    default Optional<RuleHit> hit(int score, String reason) {
        return Optional.of(new RuleHit(code(), name(), score, reason));
    }
}
