package com.example.aml.repository;

import com.example.aml.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerIdOrderByIdAsc(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    @Query("select a from Account a join fetch a.customer where a.id = :id")
    Optional<Account> findWithCustomer(@Param("id") Long id);

    @Query("select a from Account a join fetch a.customer order by a.customer.fullName, a.accountNumber")
    List<Account> findAllWithCustomer();
}
