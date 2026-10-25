package com.example.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted on/off switch for each detection rule, so analysts can tune the engine at runtime. */
@Entity
@Table(name = "rule_settings")
public class RuleSetting {

    @Id
    @Column(length = 40)
    private String ruleCode;

    private boolean enabled = true;

    protected RuleSetting() {
    }

    public RuleSetting(String ruleCode, boolean enabled) {
        this.ruleCode = ruleCode;
        this.enabled = enabled;
    }

    public String getRuleCode() { return ruleCode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
