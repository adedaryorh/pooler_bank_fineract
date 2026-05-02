package com.poolerapp.pooler_bank.payment.pending.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "pending_payments",
       indexes = {
           @Index(name = "idx_pp_reference", columnList = "paystack_reference", unique = true),
           @Index(name = "idx_pp_account",   columnList = "account_number")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PendingPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "paystack_reference", nullable = false, unique = true, length = 100)
    private String paystackReference;
    @Column(name = "account_number", nullable = false)
    private String accountNumber;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentPurpose purpose;

    @Column(name = "loan_id")
    private Long loanId;
    @Column(name = "authorization_url", length = 500)
    private String authorizationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingPaymentStatus status;
    @Column(name = "paystack_transaction_id")
    private Long paystackTransactionId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PaymentPurpose {
        WALLET_FUNDING,
        LOAN_REPAYMENT
    }

    public enum PendingPaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        EXPIRED
    }
}
