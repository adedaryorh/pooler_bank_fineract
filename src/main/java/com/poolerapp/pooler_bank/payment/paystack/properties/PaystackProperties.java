package com.poolerapp.pooler_bank.payment.paystack.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "paystack")
public class PaystackProperties {

    private String baseUrl = "https://api.paystack.co";

    private String secretKey;

    private String webhookHeader = "x-paystack-signature";
    private String callbackUrl = "http://localhost:8081/api/v1/payments/paystack/callback";
}
