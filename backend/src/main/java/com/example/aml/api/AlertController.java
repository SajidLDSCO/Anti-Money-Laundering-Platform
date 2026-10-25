package com.example.aml.api;

import com.example.aml.api.dto.AlertDetailDto;
import com.example.aml.api.dto.AlertSummaryDto;
import com.example.aml.api.dto.AssignRequest;
import com.example.aml.api.dto.NoteDto;
import com.example.aml.api.dto.NoteRequest;
import com.example.aml.api.dto.PageDto;
import com.example.aml.api.dto.SarDto;
import com.example.aml.api.dto.SarRequest;
import com.example.aml.api.dto.StatusChangeRequest;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.Severity;
import com.example.aml.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping("/alerts")
    public PageDto<AlertSummaryDto> search(@RequestParam(required = false) AlertStatus status,
                                           @RequestParam(defaultValue = "false") boolean openOnly,
                                           @RequestParam(required = false) Severity severity,
                                           @RequestParam(required = false) Long customerId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.search(status, openOnly, severity, customerId, page, size);
    }

    @GetMapping("/alerts/{id}")
    public AlertDetailDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    // The analyst on every workflow action is the signed-in user, never a name sent by the client.

    @PostMapping("/alerts/{id}/assign")
    public AlertDetailDto assign(@PathVariable Long id, Authentication auth) {
        return service.assign(id, new AssignRequest(auth.getName()));
    }

    @PostMapping("/alerts/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request, Authentication auth) {
        return service.addNote(id, new NoteRequest(auth.getName(), request.text()));
    }

    @PostMapping("/alerts/{id}/status")
    public AlertDetailDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                       Authentication auth) {
        return service.changeStatus(id, new StatusChangeRequest(request.status(), auth.getName(), request.comment()));
    }

    @PostMapping("/alerts/{id}/sar")
    public AlertDetailDto fileSar(@PathVariable Long id, @Valid @RequestBody SarRequest request, Authentication auth) {
        return service.fileSar(id, new SarRequest(auth.getName(), request.narrative()));
    }

    @GetMapping("/sars")
    public List<SarDto> sars() {
        return service.listSars();
    }
}
