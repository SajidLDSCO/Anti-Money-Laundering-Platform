package com.example.aml.api;

import com.example.aml.api.dto.RuleDto;
import com.example.aml.api.dto.RuleToggleRequest;
import com.example.aml.rules.RuleEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleEngine engine;

    public RuleController(RuleEngine engine) {
        this.engine = engine;
    }

    @GetMapping
    public List<RuleDto> list() {
        return engine.describe();
    }

    @PatchMapping("/{code}")
    public RuleDto toggle(@PathVariable String code, @RequestBody RuleToggleRequest request) {
        return engine.setEnabled(code, request.enabled());
    }
}
