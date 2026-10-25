package com.example.aml.repository;

import com.example.aml.domain.Transaction;
import com.example.aml.domain.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** A customer's activity across all of their accounts, used as rule-engine history. */
    @Query("""
            select t from Transaction t
            where t.account.customer.id = :customerId
              and t.timestamp >= :from and t.timestamp <= :to
            order by t.timestamp asc
            """)
    List<Transaction> findCustomerHistory(@Param("customerId") Long customerId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    @Query(value = """
            select t from Transaction t join fetch t.account a join fetch a.customer c
            where (:status is null or t.status = :status)
              and (:customerId is null or c.id = :customerId)
              and (lower(t.reference) like lower(concat('%', :q, '%'))
                   or lower(coalesce(t.counterpartyName, '')) like lower(concat('%', :q, '%'))
                   or lower(c.fullName) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(t) from Transaction t join t.account a join a.customer c
            where (:status is null or t.status = :status)
              and (:customerId is null or c.id = :customerId)
              and (lower(t.reference) like lower(concat('%', :q, '%'))
                   or lower(coalesce(t.counterpartyName, '')) like lower(concat('%', :q, '%'))
                   or lower(c.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<Transaction> search(@Param("status") TransactionStatus status,
                             @Param("customerId") Long customerId,
                             @Param("q") String q,
                             Pageable pageable);

    @Query("select t from Transaction t join fetch t.account a join fetch a.customer where t.id = :id")
    Optional<Transaction> findDetailed(@Param("id") Long id);

    long countByStatus(TransactionStatus status);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.timestamp >= :from")
    BigDecimal sumAmountSince(@Param("from") Instant from);

    /** Lightweight projection for the dashboard's daily chart: [timestamp, status]. */
    @Query("select t.timestamp, t.status from Transaction t where t.timestamp >= :from")
    List<Object[]> timelineSince(@Param("from") Instant from);
}
