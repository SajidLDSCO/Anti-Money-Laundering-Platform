package com.example.aml.repository;

import com.example.aml.domain.Customer;
import com.example.aml.domain.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCustomerNumber(String customerNumber);

    @Query("""
            select c from Customer c
            where (lower(c.fullName) like lower(concat('%', :q, '%'))
                   or lower(c.customerNumber) like lower(concat('%', :q, '%')))
              and (:level is null or c.riskLevel = :level)
            """)
    Page<Customer> search(@Param("q") String q, @Param("level") RiskLevel level, Pageable pageable);

    long countByRiskLevel(RiskLevel level);

    @Query("select c.riskLevel, count(c) from Customer c group by c.riskLevel")
    List<Object[]> countGroupedByRiskLevel();
}
