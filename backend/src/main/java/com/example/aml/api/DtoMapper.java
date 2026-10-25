package com.example.aml.api;

import com.example.aml.api.dto.AccountDto;
import com.example.aml.api.dto.AlertSummaryDto;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.NoteDto;
import com.example.aml.api.dto.RuleHitDto;
import com.example.aml.api.dto.SarDto;
import com.example.aml.api.dto.TransactionDto;
import com.example.aml.domain.Account;
import com.example.aml.domain.Alert;
import com.example.aml.domain.AlertNote;
import com.example.aml.domain.Customer;
import com.example.aml.domain.RuleHit;
import com.example.aml.domain.SarReport;
import com.example.aml.domain.Transaction;

/** Entity → DTO conversion. Call inside a transaction: lazy associations are touched. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getCustomerNumber(), c.getFullName(), c.getCustomerType(),
                c.getDateOfBirth(), c.getNationality(), c.getCountryOfResidence(), c.getOccupation(),
                c.getEmail(), c.isPep(), c.getKycStatus(), c.getRiskScore(), c.getRiskLevel(),
                c.getRiskFactors(), c.getCreatedAt());
    }

    public static AccountDto toDto(Account a) {
        return new AccountDto(a.getId(), a.getAccountNumber(), a.getCustomer().getId(),
                a.getCustomer().getFullName(), a.getAccountType(), a.getCurrency(), a.getBalance(),
                a.getStatus(), a.getOpenedAt());
    }

    public static TransactionDto toDto(Transaction t) {
        Account a = t.getAccount();
        return new TransactionDto(t.getId(), t.getReference(), a.getId(), a.getAccountNumber(),
                a.getCustomer().getId(), a.getCustomer().getFullName(), t.getType(), t.getAmount(),
                t.getCurrency(), t.getCounterpartyName(), t.getCounterpartyAccount(),
                t.getCounterpartyCountry(), t.getChannel(), t.getDescription(), t.getTimestamp(),
                t.getRiskScore(), t.getStatus());
    }

    public static RuleHitDto toDto(RuleHit h) {
        return new RuleHitDto(h.getRuleCode(), h.getRuleName(), h.getScore(), h.getReason());
    }

    public static AlertSummaryDto toSummary(Alert a) {
        Transaction t = a.getTransaction();
        return new AlertSummaryDto(a.getId(), a.getAlertNumber(), a.getType(), a.getCustomer().getId(),
                a.getCustomer().getFullName(),
                t == null ? null : t.getId(),
                t == null ? null : t.getReference(),
                t == null ? null : t.getAmount(),
                t == null ? null : t.getCurrency(),
                a.getScore(), a.getSeverity(), a.getStatus(), a.getAssignedTo(), a.getSummary(),
                a.getHits().stream().map(RuleHit::getRuleCode).toList(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    public static NoteDto toDto(AlertNote n) {
        return new NoteDto(n.getId(), n.getAuthor(), n.getText(), n.isSystemEntry(), n.getCreatedAt());
    }

    public static SarDto toDto(SarReport s) {
        return new SarDto(s.getId(), s.getSarNumber(), s.getAlert().getId(), s.getAlert().getAlertNumber(),
                s.getCustomer().getId(), s.getCustomer().getFullName(), s.getNarrative(), s.getFiledBy(),
                s.getFiledAt());
    }
}
