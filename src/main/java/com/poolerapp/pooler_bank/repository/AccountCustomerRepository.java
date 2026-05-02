package com.poolerapp.pooler_bank.repository;

import com.poolerapp.pooler_bank.model.AccountCustomer;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountCustomerRepository extends JpaRepository<AccountCustomer, Long> {
    Boolean existsByAccountNumber(String accountNumber);
    Optional<AccountCustomer> findByEmail(String email);
    Boolean existsByEmail(String email);
    AccountCustomer findByAccountNumber(String accountNumber);

    //List<AccountCustomer> findAllByCustomerAccountStatusActive();

    AccountCustomer deleteAccountCustomerByAccountNumber(String accountNumber);
}
