package com.example.aml.seed;

import com.example.aml.api.dto.AccountDto;
import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.AlertSummaryDto;
import com.example.aml.api.dto.AssignRequest;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.NoteRequest;
import com.example.aml.api.dto.SanctionsEntryDto;
import com.example.aml.api.dto.SarRequest;
import com.example.aml.api.dto.StatusChangeRequest;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.config.AmlProperties;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.CustomerType;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.TransactionType;
import com.example.aml.repository.CustomerRepository;
import com.example.aml.screening.SanctionsScreeningService;
import com.example.aml.service.AlertService;
import com.example.aml.service.CustomerService;
import com.example.aml.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Loads a fictional demo portfolio on an empty database. Every transaction goes through
 * the real monitoring pipeline, so the resulting alerts are genuine engine output.
 * <p>
 * All names below - customers and sanctions entries - are invented. Replace the sanctions
 * list with a real feed (OFAC SDN, UN Consolidated, EU, your national list) before any real use.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String[] MERCHANTS = {"Corner Grocery", "Metro Transit", "City Utilities",
            "Online Marketplace", "Fuel Station 24", "Pharmacy Plus", "Riverside Cafe", "Mobile Telecom"};

    private final AmlProperties props;
    private final CustomerRepository customerRepository;
    private final CustomerService customers;
    private final TransactionService transactions;
    private final AlertService alerts;
    private final SanctionsScreeningService sanctions;
    private final Random random = new Random(42);
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);

    public DemoDataSeeder(AmlProperties props, CustomerRepository customerRepository, CustomerService customers,
                          TransactionService transactions, AlertService alerts,
                          SanctionsScreeningService sanctions) {
        this.props = props;
        this.customerRepository = customerRepository;
        this.customers = customers;
        this.transactions = transactions;
        this.alerts = alerts;
        this.sanctions = sanctions;
    }

    private record Event(Instant at, TransactionRequest request) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedDemoData() || customerRepository.count() > 0) {
            return;
        }
        log.info("Seeding fictional demo data...");
        seedSanctions();

        List<Event> events = new ArrayList<>();

        // Clean retail customers: background noise only.
        long ayesha = account(customer("Ayesha Rahman", CustomerType.INDIVIDUAL, "BD", "BD", "Software engineer",
                false, KycStatus.VERIFIED, LocalDate.of(1991, 3, 14)), "USD");
        retail(events, ayesha, "BD", 60, 4200, "Brightline Software");
        long tom = account(customer("Tom Becker", CustomerType.INDIVIDUAL, "DE", "DE", "Teacher",
                false, KycStatus.VERIFIED, LocalDate.of(1984, 7, 2)), "USD");
        retail(events, tom, "DE", 60, 3600, "Regional School Board");

        // 1. Structuring: cash deposits just under the threshold.
        long daniel = account(customer("Daniel Okafor", CustomerType.INDIVIDUAL, "GB", "GB", "Used car dealer",
                false, KycStatus.VERIFIED, LocalDate.of(1979, 11, 23)), "USD");
        retail(events, daniel, "GB", 60, 3900, "Okafor Motors");
        add(events, daniel, TransactionType.CASH_DEPOSIT, "9400", null, null, ago(3, 10), "Cash deposit");
        add(events, daniel, TransactionType.CASH_DEPOSIT, "9750", null, null, ago(3, 4), "Cash deposit");
        add(events, daniel, TransactionType.CASH_DEPOSIT, "8900", null, null, ago(2, 9), "Cash deposit");
        add(events, daniel, TransactionType.CASH_DEPOSIT, "9600", null, null, ago(2, 2), "Cash deposit");

        // 2. Sanctions: payment to a (misspelt) listed entity -> blocked.
        long northwind = account(customer("Northwind Import Export Ltd", CustomerType.BUSINESS, "AE", "AE",
                "Wholesale trading", false, KycStatus.VERIFIED, null), "USD");
        business(events, northwind, "AE", 60);
        add(events, northwind, TransactionType.WIRE_OUT, "74500", "Golden Cresent Trading", "AE", ago(2, 6),
                "Invoice 2291 - industrial valves");

        // 3. Pass-through to a high-risk jurisdiction.
        long marina = account(customer("Marina Kovalenko", CustomerType.INDIVIDUAL, "CY", "CY", "Consultant",
                false, KycStatus.VERIFIED, LocalDate.of(1988, 5, 30)), "USD");
        retail(events, marina, "CY", 60, 5200, "Kovalenko Advisory");
        add(events, marina, TransactionType.WIRE_IN, "48000", "Baltic Ventures SIA", "LV", ago(5, 14),
                "Consulting fee");
        add(events, marina, TransactionType.WIRE_OUT, "46200", "Pars Commodity Hub", "IR", ago(4, 16),
                "Goods purchase");

        // 4. PEP receiving a large round sum from an offshore consultancy.
        long leonard = account(customer("Leonard Aziz", CustomerType.INDIVIDUAL, "GB", "GB",
                "Former deputy minister", true, KycStatus.VERIFIED, LocalDate.of(1962, 1, 9)), "USD");
        retail(events, leonard, "GB", 60, 6800, "Parliamentary Pension Fund");
        add(events, leonard, TransactionType.WIRE_IN, "25000", "Meridian Consulting FZE", "AE", ago(6, 3),
                "Advisory services");

        // 5. Velocity: a burst of transfers to new payees within an hour.
        long sam = account(customer("Sam Patel", CustomerType.INDIVIDUAL, "IN", "GB", "Student",
                false, KycStatus.VERIFIED, LocalDate.of(2002, 8, 17)), "USD");
        retail(events, sam, "GB", 60, 900, "Campus Bookshop");
        String[] payees = {"J. Morgan", "K. Lee", "R. Silva", "A. Novak", "P. Mensah", "D. Ortiz", "L. Haddad"};
        for (int i = 0; i < payees.length; i++) {
            add(events, sam, TransactionType.TRANSFER_OUT, String.valueOf(4800 + i * 410), payees[i], "GB",
                    ago(1, 0).minus(Duration.ofMinutes(55 - i * 8L)), "Transfer");
        }

        // 6. Large cash deposit far outside the customer's profile.
        long farhana = account(customer("Farhana Akter", CustomerType.INDIVIDUAL, "BD", "BD", "Nurse",
                false, KycStatus.VERIFIED, LocalDate.of(1993, 12, 1)), "USD");
        retail(events, farhana, "BD", 60, 2100, "City General Hospital");
        add(events, farhana, TransactionType.CASH_DEPOSIT, "15000", null, null, ago(1, 5), "Cash deposit");

        // 7. Onboarding hit: name close to a listed individual (alert raised at creation).
        long ivan = account(customer("Ivan Petrenkov", CustomerType.INDIVIDUAL, "UA", "CY", "Shipping broker",
                false, KycStatus.PENDING, LocalDate.of(1975, 4, 4)), "USD");
        retail(events, ivan, "CY", 20, 3000, "Petrenkov Shipping");

        events.sort(Comparator.comparing(Event::at));
        for (Event e : events) {
            transactions.submit(e.request());
        }

        workSomeAlerts();
        log.info("Demo data ready: {} transactions submitted.", events.size());
    }

    // ------------------------------------------------------------------ helpers

    private void seedSanctions() {
        String src = "SAMPLE-LIST";
        sanction("Golden Crescent Trading LLC", "Golden Crescent Trdg|GCT General Trading", "ENTITY", "AE", src, "Proliferation financing");
        sanction("Ivan Petrenko", "Ivan Petrenko Mykolayovych|I. Petrenko", "INDIVIDUAL", "UA", src, "Sanctions evasion");
        sanction("Viktor Petrovich Ivanenko", "Viktor Ivanenko", "INDIVIDUAL", "RU", src, "Cyber-related");
        sanction("Pars Commodity Hub", "Pars Commodities", "ENTITY", "IR", src, "Non-proliferation");
        sanction("Al Noor Exchange House", "Al-Nour Exchange", "ENTITY", "SY", src, "Terrorist financing");
        sanction("Red Delta Shipping Co", "Red Delta Maritime", "ENTITY", "KP", src, "DPRK");
        sanction("Hassan Ali Mahmoud", "Hasan Ali Mahmud", "INDIVIDUAL", "YE", src, "Counter-terrorism");
        sanction("Orion Blue Holdings", null, "ENTITY", "VE", src, "Corruption");
        sanction("Carlos Eduardo Ramires", "Carlos E. Ramirez", "INDIVIDUAL", "VE", src, "Narcotics trafficking");
        sanction("Black Lotus Logistics", "Black Lotus Freight", "ENTITY", "MM", src, "Human rights");
        sanctions.invalidate();
    }

    private void sanction(String name, String aliases, String type, String country, String source, String program) {
        sanctions.create(new SanctionsEntryDto(null, name, aliases, type, country, source, program));
    }

    private long customer(String name, CustomerType type, String nationality, String residence, String occupation,
                          boolean pep, KycStatus kyc, LocalDate dob) {
        CustomerDto c = customers.create(new CustomerRequest(name, type, dob, nationality, residence, occupation,
                name.toLowerCase().replaceAll("[^a-z]+", ".") + "@example.com", pep, kyc));
        return c.id();
    }

    private long account(long customerId, String currency) {
        AccountDto a = customers.openAccount(customerId, new AccountRequest("CURRENT", currency));
        return a.id();
    }

    /** Monthly salary plus everyday card spending. */
    private void retail(List<Event> events, long accountId, String country, int days, int salary, String employer) {
        for (int d = days; d >= 1; d--) {
            if (d % 30 == 2) {
                add(events, accountId, TransactionType.TRANSFER_IN, String.valueOf(salary), employer, country,
                        ago(d, 9), "Salary");
            }
            int n = random.nextInt(3);
            for (int i = 0; i < n; i++) {
                BigDecimal amount = BigDecimal.valueOf(15 + random.nextDouble() * 220).setScale(2, RoundingMode.HALF_UP);
                add(events, accountId, TransactionType.CARD_PAYMENT, amount.toPlainString(),
                        MERCHANTS[random.nextInt(MERCHANTS.length)], country, ago(d, 8 + random.nextInt(12)),
                        "Card purchase");
            }
        }
    }

    /** Regular supplier payments and customer receipts. */
    private void business(List<Event> events, long accountId, String country, int days) {
        for (int d = days; d >= 1; d -= 2) {
            BigDecimal in = BigDecimal.valueOf(6000 + random.nextDouble() * 14000).setScale(2, RoundingMode.HALF_UP);
            add(events, accountId, TransactionType.WIRE_IN, in.toPlainString(), "Gulf Retail Partners", country,
                    ago(d, 10), "Customer receipt");
            BigDecimal out = BigDecimal.valueOf(3000 + random.nextDouble() * 9000).setScale(2, RoundingMode.HALF_UP);
            add(events, accountId, TransactionType.WIRE_OUT, out.toPlainString(), "Harbor Packaging Supplies",
                    country, ago(d, 15), "Supplier payment");
        }
    }

    private void add(List<Event> events, long accountId, TransactionType type, String amount, String counterparty,
                     String country, Instant at, String description) {
        events.add(new Event(at, new TransactionRequest(accountId, type, new BigDecimal(amount), null, counterparty,
                null, country, type.isCash() ? "BRANCH" : "ONLINE", description, at)));
    }

    /** {@code days} ago, at {@code hoursBack} hours before the same time of day as now. */
    private Instant ago(int days, int hoursBack) {
        return now.minus(Duration.ofDays(days)).minus(Duration.ofHours(hoursBack));
    }

    /** Put a few alerts through the workflow so every screen has something to show. */
    private void workSomeAlerts() {
        List<AlertSummaryDto> all = alerts.search(null, false, null, null, 0, 100).content();
        boolean sarFiled = false;
        boolean falsePositive = false;
        for (AlertSummaryDto a : all) {
            if (a.customerName().equals("Daniel Okafor") && !sarFiled) {
                alerts.assign(a.id(), new AssignRequest("a.hossain"));
                alerts.addNote(a.id(), new NoteRequest("a.hossain", "Four branch cash deposits in 48h, each below "
                        + "the CTR threshold. Customer could not document the source of cash."));
                alerts.fileSar(a.id(), new SarRequest("a.hossain", "Customer made four cash deposits totalling "
                        + "37,650 USD over two days at the branch, each kept below the 10,000 reporting threshold. "
                        + "When contacted the customer stated the cash came from car sales but could not provide "
                        + "invoices or sale agreements. Pattern is consistent with structuring to evade CTR filing."));
                sarFiled = true;
            } else if (a.customerName().equals("Sam Patel") && !falsePositive) {
                alerts.assign(a.id(), new AssignRequest("m.chen"));
                alerts.changeStatus(a.id(), new StatusChangeRequest(AlertStatus.CLOSED_FALSE_POSITIVE, "m.chen",
                        "Customer confirmed by phone: splitting deposit for shared flat rental among flatmates. "
                                + "Tenancy agreement on file."));
                falsePositive = true;
            } else if (a.customerName().equals("Marina Kovalenko")) {
                alerts.assign(a.id(), new AssignRequest("a.hossain"));
                alerts.changeStatus(a.id(), new StatusChangeRequest(AlertStatus.ESCALATED, "a.hossain",
                        "Funds forwarded to Iran within 24h of receipt. Escalating to MLRO."));
            }
        }
    }
}
