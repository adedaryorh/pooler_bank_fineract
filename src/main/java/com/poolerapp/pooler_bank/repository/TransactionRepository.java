package com.poolerapp.pooler_bank.repository;

import com.poolerapp.pooler_bank.model.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transactions, String> {
}
