package com.example.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One rule that fired for an alert, with the evidence that made it fire. */
@Embeddable
public class RuleHit {

    @Column(length = 40)
    private String ruleCode;

    private String ruleName;

    private int score;

    @Column(length = 1000)
    private String reason;

    protected RuleHit() {
    }

    public RuleHit(String ruleCode, String ruleName, int score, String reason) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.score = score;
        this.reason = reason;
    }

    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public int getScore() { return score; }
    public String getReason() { return reason; }
}
