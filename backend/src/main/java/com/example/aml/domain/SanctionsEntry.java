package com.example.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A designated person or entity from a sanctions / watch list (OFAC SDN, UN, EU, local list...). */
@Entity
@Table(name = "sanctions_entries")
public class SanctionsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Pipe-separated alternative spellings / aliases. */
    @Column(length = 2000)
    private String aliases;

    @Column(length = 16)
    private String entityType;

    @Column(length = 2)
    private String country;

    @Column(length = 32)
    private String listSource;

    @Column(length = 64)
    private String program;

    public List<String> allNames() {
        List<String> names = new ArrayList<>();
        names.add(name);
        if (aliases != null && !aliases.isBlank()) {
            Arrays.stream(aliases.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(names::add);
        }
        return names;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getListSource() { return listSource; }
    public void setListSource(String listSource) { this.listSource = listSource; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
}
