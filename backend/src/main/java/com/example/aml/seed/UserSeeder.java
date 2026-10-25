package com.example.aml.seed;

import com.example.aml.config.AmlProperties;
import com.example.aml.domain.Role;
import com.example.aml.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes sure someone can sign in and approve new accounts.
 * <ul>
 *   <li>With {@code aml.seed-demo-data}: creates the demo users below (all with {@link #DEMO_PASSWORD}).
 *       The usernames match the analysts in the demo case trail.</li>
 *   <li>Otherwise: if there is no enabled admin and {@code AML_ADMIN_PASSWORD} is set, creates that admin.</li>
 * </ul>
 */
@Component
@Order(0)
public class UserSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "aml-demo-2026";

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final AmlProperties props;
    private final UserService users;

    public UserSeeder(AmlProperties props, UserService users) {
        this.props = props;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        AmlProperties.Security sec = props.security();
        String adminName = sec == null || sec.bootstrapAdminUsername() == null || sec.bootstrapAdminUsername().isBlank()
                ? "admin" : sec.bootstrapAdminUsername();
        String adminPassword = sec == null ? null : sec.bootstrapAdminPassword();
        String adminEmail = sec == null || sec.bootstrapAdminEmail() == null || sec.bootstrapAdminEmail().isBlank()
                ? null : sec.bootstrapAdminEmail();

        if (!users.hasEnabledAdmin() && adminPassword != null && !adminPassword.isBlank()) {
            if (users.createIfMissing(adminName, "Administrator", adminEmail, adminPassword, Role.ADMIN)) {
                log.info("Created bootstrap admin account '{}'.", adminName);
            }
        }

        if (props.seedDemoData()) {
            // A real admin email lets you receive the sign-up notifications locally.
            boolean created = users.createIfMissing("admin", "Administrator",
                    adminEmail != null ? adminEmail : "admin@aml.example", DEMO_PASSWORD, Role.ADMIN);
            created |= users.createIfMissing("a.hossain", "Anika Hossain", "a.hossain@aml.example", DEMO_PASSWORD, Role.MLRO);
            created |= users.createIfMissing("m.chen", "Michael Chen", "m.chen@aml.example", DEMO_PASSWORD, Role.ANALYST);
            if (created) {
                log.warn("Demo users admin (ADMIN), a.hossain (MLRO) and m.chen (ANALYST) created with password '{}'."
                        + " Never enable demo data outside a local machine.", DEMO_PASSWORD);
            }
        }

        if (!users.hasEnabledAdmin()) {
            log.warn("No enabled admin account exists, so new sign-ups cannot be approved."
                    + " Set AML_ADMIN_PASSWORD (and optionally AML_ADMIN_USERNAME) and restart.");
        }
    }
}
