package com.poolerapp.pooler_bank.keycloak;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    private String authServerUrl;

    private String realm;

    private String clientId;

    private String clientSecret;

    private String adminClientId = "admin-cli";
    private String adminUsername;

    private String adminPassword;
}
