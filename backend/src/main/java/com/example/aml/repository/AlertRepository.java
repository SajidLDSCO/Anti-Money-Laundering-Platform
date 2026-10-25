package com.example.aml.repository;

import com.example.aml.domain.Alert;
import com.example.aml.domain.AlertStatus;
import com.example.aml.domain.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query(value = """
            select a from Alert a join fetch a.customer c left join fetch a.transaction t
            where (:status is null or a.status = :status)
              and (:openOnly = false or a.status in :openStatuses)
              and (:severity is null or a.severity = :severity)
              and (:customerId is null or c.id = :customerId)
            """,
            countQuery = """
            select count(a) from Alert a
            where (:status is null or a.status = :status)
              and (:openOnly = false or a.status in :openStatuses)
              and (:severity is null or a.severity = :severity)
              and (:customerId is null or a.customer.id = :customerId)
            """)
    Page<Alert> search(@Param("status") AlertStatus status,
                       @Param("openOnly") boolean openOnly,
                       @Param("openStatuses") Collection<AlertStatus> openStatuses,
                       @Param("severity") Severity severity,
                       @Param("customerId") Long customerId,
                       Pageable pageable);

    @Query("select a from Alert a join fetch a.customer left join fetch a.transaction where a.id = :id")
    Optional<Alert> findDetailed(@Param("id") Long id);

    long countByStatusIn(Collection<AlertStatus> statuses);

    long countByStatusInAndSeverity(Collection<AlertStatus> statuses, Severity severity);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<AlertStatus> statuses);

    @Query("select a.severity, count(a) from Alert a where a.status in :statuses group by a.severity")
    List<Object[]> countBySeverity(@Param("statuses") Collection<AlertStatus> statuses);

    @Query("select h.ruleCode, count(a) from Alert a join a.hits h group by h.ruleCode")
    List<Object[]> countByRule();

    @Query("select a from Alert a join fetch a.customer left join fetch a.transaction order by a.createdAt desc limit 8")
    List<Alert> findRecent();
}
