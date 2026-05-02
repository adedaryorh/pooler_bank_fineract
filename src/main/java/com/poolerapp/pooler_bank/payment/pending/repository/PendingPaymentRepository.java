package com.poolerapp.pooler_bank.payment.pending.repository;

import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PendingPaymentRepository extends JpaRepository<PendingPayment, Long> {

    Optional<PendingPayment> findByPaystackReference(String paystackReference);

    boolean existsByPaystackReference(String paystackReference);
}
