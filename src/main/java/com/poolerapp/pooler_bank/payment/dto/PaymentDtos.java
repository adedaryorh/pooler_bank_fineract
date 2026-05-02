package com.poolerapp.pooler_bank.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDtos {

    @Data
    public static class PaymentRequest {
        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull
        @DecimalMin(value = "1.0", message = "Amount must be at least 1")
        private BigDecimal amount;

        private String narration;

        /**
         * Client-generated unique key. If the same key is sent twice,
         * the second call returns the original result — no double-charge.
         * Format: UUID or "{accountNumber}-{timestamp}-{random}"
         */
        @NotBlank(message = "Idempotency key is required")
        private String idempotencyKey;
    }

    @Data
    public static class PaymentResponse {
        private Long paymentId;
        private Long fineractTransactionId;
        private String accountNumber;
        private BigDecimal amount;
        private String type;
        private String status;
        private String narration;
        private LocalDateTime processedAt;

        /** True when this response is replayed from a previous identical request */
        private boolean idempotentReplay;
    }
}
