package com.example.aml.api;

import com.example.aml.api.dto.EvaluationResultDto;
import com.example.aml.api.dto.PageDto;
import com.example.aml.api.dto.TransactionDto;
import com.example.aml.api.dto.TransactionRequest;
import com.example.aml.domain.TransactionStatus;
import com.example.aml.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public PageDto<TransactionDto> search(@RequestParam(required = false) TransactionStatus status,
                                          @RequestParam(required = false) Long customerId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.search(status, customerId, q, page, size);
    }

    @GetMapping("/{id}")
    public TransactionDto get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Submit a transaction for real-time monitoring. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationResultDto submit(@Valid @RequestBody TransactionRequest request) {
        return service.submit(request);
    }
}
