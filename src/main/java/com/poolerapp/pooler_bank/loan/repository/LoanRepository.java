package com.poolerapp.pooler_bank.loan.repository;

import com.poolerapp.pooler_bank.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByCustomerId(Long customerId);

    Optional<Loan> findByFineractLoanId(Long fineractLoanId);

    boolean existsByCustomerIdAndStatusIn(Long customerId, List<Loan.LoanStatus> statuses);

    Optional<Loan> findByIdempotencyKey(String idempotencyKey);
}
