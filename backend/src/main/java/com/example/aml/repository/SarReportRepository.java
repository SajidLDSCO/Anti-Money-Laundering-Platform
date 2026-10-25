package com.example.aml.repository;

import com.example.aml.domain.SarReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SarReportRepository extends JpaRepository<SarReport, Long> {

    @Query("select s from SarReport s join fetch s.alert join fetch s.customer order by s.filedAt desc")
    List<SarReport> findAllDetailed();

    Optional<SarReport> findByAlertId(Long alertId);

    long countByCustomerId(Long customerId);
}
