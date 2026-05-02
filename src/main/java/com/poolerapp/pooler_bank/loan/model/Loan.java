package com.poolerapp.pooler_bank.loan.model;

import com.poolerapp.pooler_bank.model.AccountCustomer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Local reference record for a loan managed by Fineract.
 * We do NOT duplicate the full loan ledger here — only what we need for
 * orchestration, reporting, and credit scoring.
 */
@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private AccountCustomer customer;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer termMonths;

    @Column(nullable = false)
    private BigDecimal interestRate;

    /** Primary reference to Fineract */
    @Column(name = "fineract_loan_id", unique = true)
    private Long fineractLoanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    private String purpose;

    @Column(name = "disbursed_on")
    private LocalDate disbursedOn;

    @Column(name = "repaid_on")
    private LocalDate repaidOn;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Paystack disbursement fields ─────────────────────────────────────────────
    /** Paystack transfer_code for the disbursement payout — used to reconcile webhook */
    @Column(name = "paystack_transfer_code", length = 100)
    private String paystackTransferCode;

    /** Paystack recipient_code for the borrower — persisted to avoid recreating */
    @Column(name = "paystack_recipient_code", length = 100)
    private String paystackRecipientCode;

    /** Borrower's external bank account number (for Paystack payout) */
    @Column(name = "disbursement_account_number", length = 20)
    private String disbursementAccountNumber;

    /** Borrower's external bank code (CBN code, e.g. '011' for First Bank) */
    @Column(name = "disbursement_bank_code", length = 10)
    private String disbursementBankCode;

    /** Idempotency guard — prevents double loan submission on retries/double-clicks */
    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Version
    private Long version; // optimistic lock — prevents double disburse

    public enum LoanStatus {
        PENDING_ELIGIBILITY,
        SUBMITTED,
        APPROVED,
        DISBURSED,
        REPAID,
        DEFAULTED,
        REJECTED
    }
}
