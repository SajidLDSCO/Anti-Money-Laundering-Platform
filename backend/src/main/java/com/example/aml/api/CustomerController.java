package com.example.aml.api;

import com.example.aml.api.dto.AccountDto;
import com.example.aml.api.dto.AccountRequest;
import com.example.aml.api.dto.AccountStatusRequest;
import com.example.aml.api.dto.CustomerDetailDto;
import com.example.aml.api.dto.CustomerDto;
import com.example.aml.api.dto.CustomerRequest;
import com.example.aml.api.dto.PageDto;
import com.example.aml.domain.RiskLevel;
import com.example.aml.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    public PageDto<CustomerDto> search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) RiskLevel riskLevel,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.search(q, riskLevel, page, size);
    }

    @GetMapping("/customers/{id}")
    public CustomerDetailDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto create(@Valid @RequestBody CustomerRequest request) {
        return service.create(request);
    }

    @PutMapping("/customers/{id}")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/customers/{id}/recalculate-risk")
    public CustomerDto recalculate(@PathVariable Long id) {
        return service.recalculateRisk(id);
    }

    @PostMapping("/customers/{id}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto openAccount(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return service.openAccount(id, request);
    }

    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        return service.allAccounts();
    }

    @PatchMapping("/accounts/{id}/status")
    public AccountDto setAccountStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest request) {
        return service.setAccountStatus(id, request);
    }
}
