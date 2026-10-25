package com.example.aml.rules;

import com.example.aml.api.dto.RuleDto;
import com.example.aml.common.NotFoundException;
import com.example.aml.config.AmlProperties;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.RuleSetting;
import com.example.aml.domain.TransactionStatus;
import com.example.aml.repository.RuleSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs every enabled {@link AmlRule} against a transaction and turns the hits into a
 * score (0-100) and a decision.
 * <p>
 * Scoring is additive and capped at 100: independent weak signals (round amount + PEP +
 * high-risk customer) can add up to an alert, while one strong signal (structuring) is
 * enough alone. A sanctions match always blocks, whatever the total.
 */
@Service
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<AmlRule> rules;
    private final RuleSettingRepository settings;
    private final AmlProperties props;

    public RuleEngine(List<AmlRule> rules, RuleSettingRepository settings, AmlProperties props) {
        this.rules = rules.stream().sorted(Comparator.comparing(AmlRule::code)).toList();
        this.settings = settings;
        this.props = props;
    }

    public record Result(List<RuleHit> hits, int score, TransactionStatus decision) {
        public boolean alertRequired(int flagThreshold) {
            return score >= flagThreshold;
        }
    }

    public Result evaluate(RuleContext ctx) {
        Map<String, Boolean> enabled = enabledMap();
        List<RuleHit> hits = new ArrayList<>();
        for (AmlRule rule : rules) {
            if (!enabled.getOrDefault(rule.code(), true)) {
                continue;
            }
            try {
                rule.evaluate(ctx).ifPresent(hits::add);
            } catch (RuntimeException ex) {
                // One faulty rule must not stop monitoring; log loudly and carry on.
                log.error("Rule {} failed on transaction {}", rule.code(), ctx.transaction().getReference(), ex);
            }
        }
        hits.sort(Comparator.comparingInt(RuleHit::getScore).reversed());

        int score = Math.min(100, hits.stream().mapToInt(RuleHit::getScore).sum());
        boolean sanctions = hits.stream().anyMatch(h -> SanctionsMatchRule.CODE.equals(h.getRuleCode()));

        TransactionStatus decision;
        if (sanctions || score >= props.scoring().blockThreshold()) {
            decision = TransactionStatus.BLOCKED;
        } else if (score >= props.scoring().flagThreshold()) {
            decision = TransactionStatus.FLAGGED;
        } else {
            decision = TransactionStatus.CLEARED;
        }
        return new Result(hits, sanctions ? 100 : score, decision);
    }

    @Transactional(readOnly = true)
    public List<RuleDto> describe() {
        Map<String, Boolean> enabled = enabledMap();
        return rules.stream()
                .map(r -> new RuleDto(r.code(), r.name(), r.category(), r.description(), r.parameters(),
                        r.baseScore(), enabled.getOrDefault(r.code(), true)))
                .toList();
    }

    @Transactional
    public RuleDto setEnabled(String code, boolean enabled) {
        AmlRule rule = find(code).orElseThrow(() -> new NotFoundException("Rule", code));
        RuleSetting setting = settings.findById(rule.code()).orElseGet(() -> new RuleSetting(rule.code(), true));
        setting.setEnabled(enabled);
        settings.save(setting);
        return new RuleDto(rule.code(), rule.name(), rule.category(), rule.description(), rule.parameters(),
                rule.baseScore(), enabled);
    }

    private Optional<AmlRule> find(String code) {
        return rules.stream().filter(r -> r.code().equalsIgnoreCase(code)).findFirst();
    }

    private Map<String, Boolean> enabledMap() {
        return settings.findAll().stream()
                .collect(Collectors.toMap(RuleSetting::getRuleCode, RuleSetting::isEnabled, (a, b) -> a));
    }
}
