package com.example.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Typed view of the {@code aml.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "aml")
public record AmlProperties(
        boolean seedDemoData,
        String corsAllowedOrigins,
        Rules rules,
        Scoring scoring,
        Set<String> highRiskCountries,
        Set<String> increasedMonitoringCountries,
        Set<String> highRiskOccupations,
        Security security,
        Mail mail
) {

    /**
     * Email notifications. Sending also needs an SMTP server ({@code spring.mail.host}); without one,
     * notifications are only logged.
     *
     * @param enabled master switch; the Maven test run turns it off so tests never send real mail
     * @param from    sender address, e.g. {@code AML Monitor <aml@bank.example>}
     * @param appUrl  base URL of the web app, used for links in emails
     */
    public record Mail(boolean enabled, String from, String appUrl) {}

    /**
     * @param signupRequiresApproval self-registered accounts stay disabled until an admin enables them
     * @param bootstrapAdminUsername admin account created at startup when no enabled admin exists
     * @param bootstrapAdminPassword its password; leave empty to skip creating it
     * @param bootstrapAdminEmail    its email, where sign-up notifications go (also used for the demo admin)
     */
    public record Security(boolean signupRequiresApproval, String bootstrapAdminUsername,
                           String bootstrapAdminPassword, String bootstrapAdminEmail) {}

    public record Rules(
            BigDecimal largeCashThreshold,
            Structuring structuring,
            Velocity velocity,
            BigDecimal pepThreshold,
            BigDecimal roundAmountMinimum,
            RapidMovement rapidMovement,
            UnusualAmount unusualAmount,
            double sanctionsMatchThreshold
    ) {}

    public record Structuring(int windowHours, int minTransactions, double lowerBoundRatio) {}

    public record Velocity(int windowHours, int maxTransactions, BigDecimal dailyVolumeLimit) {}

    public record RapidMovement(int windowHours, double matchRatio, BigDecimal minimumAmount) {}

    public record UnusualAmount(int lookbackDays, int minHistory, int multiplier) {}

    public record Scoring(int flagThreshold, int blockThreshold) {}

    public boolean isHighRiskCountry(String isoCode) {
        return isoCode != null && upper(highRiskCountries).contains(isoCode.toUpperCase(Locale.ROOT));
    }

    public boolean isIncreasedMonitoringCountry(String isoCode) {
        return isoCode != null && upper(increasedMonitoringCountries).contains(isoCode.toUpperCase(Locale.ROOT));
    }

    public boolean isHighRiskOccupation(String occupation) {
        if (occupation == null || highRiskOccupations == null) {
            return false;
        }
        String o = occupation.toLowerCase(Locale.ROOT);
        return highRiskOccupations.stream().anyMatch(k -> o.contains(k.toLowerCase(Locale.ROOT).trim()));
    }

    private static Set<String> upper(Set<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
