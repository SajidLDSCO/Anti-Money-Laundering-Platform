package com.example.aml.repository;

import com.example.aml.domain.SanctionsEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionsEntryRepository extends JpaRepository<SanctionsEntry, Long> {
}
