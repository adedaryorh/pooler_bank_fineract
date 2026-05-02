package com.poolerapp.pooler_bank.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Local record of every payment event (deposit / withdrawal).
 *
 * The {@code idempotencyKey} column (UNIQUE) is the primary defence against
 * duplicate submissions. If the same key arrives twice, we return the
 * original result instead of calling Fineract again.
 */
@Entity
@Table(name = "payment_transactions",
       indexes = @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client-supplied dedup key — must be globally unique per operation. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;  // DEPOSIT | WITHDRAWAL

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String narration;

    /** Fineract transaction ID returned after successful call */
    @Column(name = "fineract_transaction_id")
    private Long fineractTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;  // PENDING | SUCCESS | FAILED

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public enum PaymentType { DEPOSIT, WITHDRAWAL }
    public enum PaymentStatus { PENDING, SUCCESS, FAILED }
}
