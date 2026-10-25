package com.example.aml.api;

import com.example.aml.api.dto.ImportResultDto;
import com.example.aml.api.dto.SanctionsEntryDto;
import com.example.aml.api.dto.ScreeningResultDto;
import com.example.aml.screening.SanctionsScreeningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ScreeningController {

    private final SanctionsScreeningService service;

    public ScreeningController(SanctionsScreeningService service) {
        this.service = service;
    }

    /** Ad-hoc name screening, e.g. before onboarding a customer or paying a new beneficiary. */
    @GetMapping("/screening")
    public ScreeningResultDto screen(@RequestParam String name) {
        return service.screenForDisplay(name);
    }

    @GetMapping("/sanctions")
    public List<SanctionsEntryDto> list() {
        return service.list();
    }

    @PostMapping("/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public SanctionsEntryDto create(@Valid @RequestBody SanctionsEntryDto entry) {
        return service.create(entry);
    }

    @DeleteMapping("/sanctions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping(value = "/sanctions/import", consumes = "multipart/form-data")
    public ImportResultDto importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        return service.importCsv(file.getInputStream());
    }
}
