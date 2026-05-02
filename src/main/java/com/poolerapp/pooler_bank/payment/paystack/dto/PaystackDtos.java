package com.poolerapp.pooler_bank.payment.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * All Paystack-facing DTOs.
 *
 * Paystack amounts are in KOBO (₦1 = 100 kobo).
 * Conversion: BigDecimal naira → multiply by 100 → send as long.
 * Response amounts come back in kobo → divide by 100 → BigDecimal naira.
 */
public class PaystackDtos {


    @Data
    public static class InitializeRequest {
        /** Customer email required by Paystack */
        private String email;
        /** Amount in KOBO */
        private long amount;
        /** Your unique reference */
        private String reference;
        /** URL Paystack redirects to after card payment */
        @JsonProperty("callback_url")
        private String callbackUrl;
        /** Arbitrary metadata attached to this transaction */
        private Object metadata;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitializeResponse {
        private boolean status;
        private String message;
        private InitializeData data;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class InitializeData {
            @JsonProperty("authorization_url")
            private String authorizationUrl;
            @JsonProperty("access_code")
            private String accessCode;
            private String reference;
        }
    }

    // ── Verify Transaction ─────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VerifyResponse {
        private boolean status;
        private String message;
        private VerifyData data;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class VerifyData {
            private String status;          // "success" | "failed" | "abandoned"
            private String reference;
            private long amount;            // in kobo
            private String currency;
            @JsonProperty("paid_at")
            private String paidAt;
            @JsonProperty("customer")
            private CustomerData customer;

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class CustomerData {
                private String email;
                @JsonProperty("customer_code")
                private String customerCode;
            }

            public BigDecimal getAmountNaira() {
                return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100));
            }

            public boolean isSuccessful() {
                return "success".equalsIgnoreCase(status);
            }
        }
    }

    // ── Transfer Recipient ─────────────────────────────────────────────────

    @Data
    public static class RecipientRequest {
        /** Always "nuban" for Nigerian bank accounts */
        private String type = "nuban";
        private String name;
        @JsonProperty("account_number")
        private String accountNumber;
        @JsonProperty("bank_code")
        private String bankCode;
        private String currency = "NGN";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecipientResponse {
        private boolean status;
        private String message;
        private RecipientData data;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RecipientData {
            @JsonProperty("recipient_code")
            private String recipientCode;
            private String name;
            @JsonProperty("account_number")
            private String accountNumber;
            @JsonProperty("bank_code")
            private String bankCode;
            private boolean active;
        }
    }

    // ── Transfer (Payout) ──────────────────────────────────────────────────

    @Data
    public static class TransferRequest {
        private String source = "balance";
        /** Amount in KOBO */
        private long amount;
        private String reference;
        private String recipient;   // recipient_code from RecipientResponse
        private String reason;
        private String currency = "NGN";
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransferResponse {
        private boolean status;
        private String message;
        private TransferData data;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TransferData {
            @JsonProperty("transfer_code")
            private String transferCode;
            private String reference;
            private String status;    // "pending" | "success" | "failed" | "reversed"
            private long amount;      // in kobo
            private String currency;
            @JsonProperty("created_at")
            private String createdAt;

            public boolean isPending() {
                return "pending".equalsIgnoreCase(status);
            }

            public boolean isSuccess() {
                return "success".equalsIgnoreCase(status);
            }

            public BigDecimal getAmountNaira() {
                return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100));
            }
        }
    }

    // ── Webhook Event ──────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookEvent {
        private String event;   // "charge.success" | "transfer.success" | "transfer.failed"
        private Object data;    // raw — parsed by handler based on event type
    }

    // ── App-level request DTOs (from clients to our API) ──────────────────

    @Data
    public static class FundWalletRequest {
        @NotBlank
        private String accountNumber;
        @NotNull
        @DecimalMin("1.0")
        private BigDecimal amount;
        private String narration;
    }

    @Data
    public static class RemittanceRequest {
        @NotBlank
        private String senderAccountNumber;
        @NotNull
        @DecimalMin("1.0")
        private BigDecimal amount;
        @NotBlank
        private String beneficiaryName;
        @NotBlank
        private String beneficiaryAccountNumber;
        @NotBlank
        private String beneficiaryBankCode;
        private String narration;
    }

    @Data
    public static class InitiateLoanRepaymentRequest {
        @NotNull
        private Long loanId;
        @NotNull
        @DecimalMin("1.0")
        private BigDecimal amount;
        @NotBlank
        private String payerEmail;
    }
}
