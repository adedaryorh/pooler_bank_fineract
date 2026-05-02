package com.poolerapp.pooler_bank.loan.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LoanDtos {

    @Data
    public static class LoanApplicationRequest {
        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull
        @DecimalMin(value = "1000.0", message = "Minimum loan amount is 1,000")
        private BigDecimal amount;

        @NotNull
        @Min(value = 1) @Max(value = 60)
        private Integer termMonths;

        private String purpose;


        @NotBlank(message = "Idempotency key is required to prevent duplicate loan submissions")
        private String idempotencyKey;

        @NotBlank(message = "Disbursement account number is required")
        private String disbursementAccountNumber;

        @NotBlank(message = "Disbursement bank code is required")
        private String disbursementBankCode;
    }

    @Data
    public static class LoanRepaymentRequest {
        @NotNull
        @DecimalMin(value = "1.0")
        private BigDecimal amount;

        private String accountNumber;
    }

    @Data
    public static class InitiateLoanRepaymentRequest {
        @NotBlank(message = "Payer email is required")
        private String payerEmail;

        @NotNull
        @DecimalMin(value = "1.0")
        private BigDecimal amount;
    }

    @Data
    public static class LoanResponse {
        private Long loanId;
        private Long fineractLoanId;
        private String accountNumber;
        private String customerName;
        private BigDecimal amount;
        private Integer termMonths;
        private BigDecimal interestRate;
        private String status;
        private String purpose;
        private LocalDate disbursedOn;
        private LocalDateTime createdAt;
        /** True when this response is replayed from a previous identical idempotency key */
        private boolean idempotentReplay;
        private String paystackTransferCode;
        private String paystackDisbursementStatus;
    }

    @Data
    public static class LoanApprovalRequest {
        @NotNull
        private Long loanId;
        private String note;
    }

    @Data
    public static class LoanRepaymentInitResponse {
        private Long loanId;
        private String reference;
        private String authorizationUrl;
        private java.math.BigDecimal amount;
        private String message;
    }

    @Data
    public static class CreditScoreResponse {
        private String accountNumber;
        private String customerName;
        private int score;
        private boolean eligible;
        private String explanation;
    }
}
