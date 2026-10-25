package com.example.aml.service;

import com.example.aml.api.DtoMapper;
import com.example.aml.api.dto.AccountDto;
import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.AccountStatusRequest;
import com.example.aml.api.dto.CustomerDetailDto;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.PageDto;
import com.example.aml.api.dto.ScreeningMatchDto;
import com.example.aml.common.Ids;
import com.example.aml.common.NotFoundException;
import com.example.aml.domain.Account;
import com.example.aml.domain.Customer;
import com.example.aml.domain.KycStatus;
import com.example.aml.domain.RiskLevel;
import com.example.aml.repository.AccountRepository;
import com.example.aml.repository.AlertRepository;
import com.example.aml.repository.CustomerRepository;
import com.example.aml.repository.TransactionRepository;
import com.example.aml.screening.SanctionsScreeningService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AlertRepository alerts;
    private final CustomerRiskService risk;
    private final AlertService alertService;
    private final SanctionsScreeningService screening;

    public CustomerService(CustomerRepository customers, AccountRepository accounts,
                           TransactionRepository transactions, AlertRepository alerts, CustomerRiskService risk,
                           AlertService alertService, SanctionsScreeningService screening) {
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.alerts = alerts;
        this.risk = risk;
        this.alertService = alertService;
        this.screening = screening;
    }

    @Transactional(readOnly = true)
    public PageDto<CustomerDto> search(String q, RiskLevel level, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Order.desc("riskScore"), Sort.Order.asc("fullName")));
        return PageDto.of(customers.search(q == null ? "" : q.trim(), level, pr), DtoMapper::toDto);
    }

    @Transactional(readOnly = true)
    public CustomerDetailDto detail(Long id) {
        Customer c = load(id);
        List<AccountDto> accs = accounts.findByCustomerIdOrderByIdAsc(id).stream().map(DtoMapper::toDto).toList();
        var txs = transactions.search(null, id, "",
                PageRequest.of(0, 25, Sort.by(Sort.Order.desc("timestamp")))).getContent()
                .stream().map(DtoMapper::toDto).toList();
        var als = alerts.search(null, false, CustomerRiskService.OPEN_STATUSES, null, id,
                PageRequest.of(0, 50, Sort.by(Sort.Order.desc("createdAt")))).getContent()
                .stream().map(DtoMapper::toSummary).toList();
        return new CustomerDetailDto(DtoMapper.toDto(c), accs, txs, als);
    }

    /** Onboarding: create, screen the name against sanctions lists, rate risk. */
    @Transactional
    public CustomerDto create(CustomerRequest req) {
        Customer c = new Customer();
        String number;
        do {
            number = Ids.digits("C", 8);
        } while (customers.existsByCustomerNumber(number));
        c.setCustomerNumber(number);
        apply(c, req);
        customers.save(c);

        ScreeningMatchDto match = screening.bestHit(c.getFullName());
        if (match != null) {
            alertService.createScreeningAlert(c, match);
        }
        risk.recalculate(c);
        return DtoMapper.toDto(c);
    }

    @Transactional
    public CustomerDto update(Long id, CustomerRequest req) {
        Customer c = load(id);
        String oldName = c.getFullName();
        apply(c, req);
        if (!c.getFullName().equalsIgnoreCase(oldName)) {
            ScreeningMatchDto match = screening.bestHit(c.getFullName());
            if (match != null) {
                alertService.createScreeningAlert(c, match);
            }
        }
        risk.recalculate(c);
        return DtoMapper.toDto(c);
    }

    @Transactional
    public CustomerDto recalculateRisk(Long id) {
        Customer c = load(id);
        risk.recalculate(c);
        return DtoMapper.toDto(c);
    }

    @Transactional
    public AccountDto openAccount(Long customerId, AccountRequest req) {
        Customer c = load(customerId);
        Account a = new Account();
        String number;
        do {
            number = Ids.digits("AC", 12);
        } while (accounts.existsByAccountNumber(number));
        a.setAccountNumber(number);
        a.setCustomer(c);
        a.setAccountType(req.accountType().toUpperCase(Locale.ROOT));
        a.setCurrency(req.currency());
        return DtoMapper.toDto(accounts.save(a));
    }

    @Transactional
    public AccountDto setAccountStatus(Long accountId, AccountStatusRequest req) {
        Account a = accounts.findWithCustomer(accountId).orElseThrow(() -> new NotFoundException("Account", accountId));
        a.setStatus(req.status());
        return DtoMapper.toDto(a);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> allAccounts() {
        return accounts.findAllWithCustomer().stream().map(DtoMapper::toDto).toList();
    }

    private Customer load(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    private static void apply(Customer c, CustomerRequest req) {
        c.setFullName(req.fullName().trim());
        c.setCustomerType(req.customerType());
        c.setDateOfBirth(req.dateOfBirth());
        c.setNationality(upper(req.nationality()));
        c.setCountryOfResidence(upper(req.countryOfResidence()));
        c.setOccupation(req.occupation());
        c.setEmail(req.email());
        c.setPep(req.pep());
        c.setKycStatus(req.kycStatus() == null ? KycStatus.PENDING : req.kycStatus());
    }

    private static String upper(String s) {
        return s == null || s.isBlank() ? null : s.toUpperCase(Locale.ROOT);
    }
}
