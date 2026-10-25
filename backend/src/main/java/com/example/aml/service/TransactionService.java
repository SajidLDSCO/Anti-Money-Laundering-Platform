package com.example.aml.service;

import com.example.aml.api.DtoMapper;
import com.example.aml.api.dto.EvaluationResultDto;
import com.example.aml.api.dto.PageDto;
import com.example.aml.api.dto.TransactionDto;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.common.BusinessRuleException;
import com.example.aml.common.Ids;
import com.example.aml.common.NotFoundException;
import com.example.aml.config.AmlProperties;
import com.example.aml.domain.Account;
import com.example.aml.domain.AccountStatus;
import com.example.aml.domain.Alert;
import com.example.aml.domain.Customer;
import com.example.aml.domain.Transaction;
import com.example.aml.domain.TransactionStatus;
import com.example.aml.repository.AccountRepository;
import com.example.aml.repository.TransactionRepository;
import com.example.aml.rules.RuleContext;
import com.example.aml.rules.RuleEngine;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Entry point for transaction monitoring: every transaction is screened by the rule
 * engine before it is posted. Blocked transactions are stored but not posted to the
 * balance until an analyst releases them.
 */
@Service
public class TransactionService {

    /** Longest look-back any rule needs. */
    private static final Duration HISTORY_WINDOW = Duration.ofDays(90);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final RuleEngine engine;
    private final AlertService alertService;
    private final CustomerRiskService risk;
    private final AmlProperties props;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts, RuleEngine engine,
                              AlertService alertService, CustomerRiskService risk, AmlProperties props) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.engine = engine;
        this.alertService = alertService;
        this.risk = risk;
        this.props = props;
    }

    @Transactional
    public EvaluationResultDto submit(TransactionRequest req) {
        Account account = accounts.findWithCustomer(req.accountId())
                .orElseThrow(() -> new NotFoundException("Account", req.accountId()));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Account " + account.getAccountNumber() + " is "
                    + account.getStatus() + "; transactions are not accepted.");
        }
        Customer customer = account.getCustomer();

        Transaction tx = new Transaction();
        tx.setReference(Ids.next("TX"));
        tx.setAccount(account);
        tx.setType(req.type());
        tx.setAmount(req.amount());
        tx.setCurrency(req.currency() == null ? account.getCurrency() : req.currency());
        tx.setCounterpartyName(blankToNull(req.counterpartyName()));
        tx.setCounterpartyAccount(blankToNull(req.counterpartyAccount()));
        tx.setCounterpartyCountry(req.counterpartyCountry() == null || req.counterpartyCountry().isBlank()
                ? null : req.counterpartyCountry().toUpperCase(Locale.ROOT));
        tx.setChannel(req.channel() == null ? "BRANCH" : req.channel());
        tx.setDescription(blankToNull(req.description()));
        tx.setTimestamp(req.timestamp() == null ? Instant.now() : req.timestamp());

        List<Transaction> history = transactions.findCustomerHistory(customer.getId(),
                tx.getTimestamp().minus(HISTORY_WINDOW), tx.getTimestamp());
        RuleEngine.Result result = engine.evaluate(new RuleContext(tx, customer, history));

        tx.setRiskScore(result.score());
        tx.setStatus(result.decision());
        if (result.decision() != TransactionStatus.BLOCKED) {
            account.setBalance(tx.getType().isInbound()
                    ? account.getBalance().add(tx.getAmount())
                    : account.getBalance().subtract(tx.getAmount()));
        }
        transactions.save(tx);

        Alert alert = null;
        if (result.alertRequired(props.scoring().flagThreshold()) || result.decision() == TransactionStatus.BLOCKED) {
            alert = alertService.createTransactionAlert(tx, result.hits(), result.score());
            risk.recalculate(customer);
        }

        return new EvaluationResultDto(DtoMapper.toDto(tx), result.score(), result.decision().name(),
                result.hits().stream().map(DtoMapper::toDto).toList(),
                alert == null ? null : alert.getId(),
                alert == null ? null : alert.getAlertNumber());
    }

    @Transactional(readOnly = true)
    public PageDto<TransactionDto> search(TransactionStatus status, Long customerId, String q, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Order.desc("timestamp")));
        return PageDto.of(transactions.search(status, customerId, q == null ? "" : q.trim(), pr), DtoMapper::toDto);
    }

    @Transactional(readOnly = true)
    public TransactionDto get(Long id) {
        return transactions.findDetailed(id).map(DtoMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Transaction", id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
