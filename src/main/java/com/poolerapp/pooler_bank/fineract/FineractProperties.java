package com.poolerapp.pooler_bank.fineract;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {
    private String baseUrl;
    private String username;
    private String password;
    private String tenantId;
    private Long savingsProductId;
    private Long loanProductId;
    private int connectTimeout = 5;
    private int readTimeout = 30;
}
