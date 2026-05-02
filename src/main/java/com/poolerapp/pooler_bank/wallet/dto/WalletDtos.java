package com.poolerapp.pooler_bank.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

public class WalletDtos {

    @Data
    public static class DepositRequest {
        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull
        @DecimalMin(value = "1.0", message = "Deposit amount must be at least 1")
        private BigDecimal amount;

        private String narration = "Customer deposit";
    }

    @Data
    public static class WithdrawalRequest {
        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull
        @DecimalMin(value = "1.0", message = "Withdrawal amount must be at least 1")
        private BigDecimal amount;

        private String narration = "Customer withdrawal";
    }

    @Data
    public static class WalletResponse {
        private String accountNumber;
        private String accountName;
        private BigDecimal balance;
        private String currency;
        private String status;
        private Long fineractSavingsId;
    }

@Data
    public static class FundWalletInitResponse {
        private String reference;
        private String authorizationUrl;
        private java.math.BigDecimal amount;
        private String accountNumber;
        private String message;
    }

    @Data
    public static class RemittanceResponse {
        private String reference;
        private String transferCode;
        private java.math.BigDecimal amount;
        private String senderAccountNumber;
        private String beneficiaryName;
        private String beneficiaryAccountNumber;
        private String paystackStatus;
        private String message;
    }

    @Data
    public static class TransactionResponse {
        private Long transactionId;
        private String type;
        private BigDecimal amount;
        private String date;
        private String status;
        private String narration;
    }
}
