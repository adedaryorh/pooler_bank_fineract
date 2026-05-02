package com.poolerapp.pooler_bank.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakProperties props;
    private String getAdminToken() {
        WebClient client = WebClient.create(props.getAuthServerUrl());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id",  props.getAdminClientId());
        form.add("username",   props.getAdminUsername());
        form.add("password",   props.getAdminPassword());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = client.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                                new KeycloakException("Failed to get admin token: " + body))))
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("access_token")) {
            throw new KeycloakException("Admin token response was null or missing access_token");
        }

        return response.get("access_token").toString();
    }

    public String createUser(String email, String firstName, String lastName,
                             String password, String role) {
        String adminToken = getAdminToken();
        WebClient client  = adminWebClient(adminToken);

        Map<String, Object> userRepresentation = Map.of(
                "username",      email,
                "email",         email,
                "firstName",     firstName,
                "lastName",      lastName,
                "enabled",       true,
                "emailVerified", false,
                "credentials",   List.of(Map.of(
                        "type",      "password",
                        "value",     password,
                        "temporary", false
                ))
        );
        var responseEntity = client.post()
                .uri("/admin/realms/" + props.getRealm() + "/users")
                .bodyValue(userRepresentation)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                                new KeycloakException("Failed to create Keycloak user: " + body))))
                .toBodilessEntity()
                .block();

        if (responseEntity == null) {
            throw new KeycloakException("Null response when creating Keycloak user for: " + email);
        }

        var location = responseEntity.getHeaders().getLocation();
        if (location == null) {
            throw new KeycloakException("Keycloak did not return Location header after user creation");
        }

        String path           = location.getPath();
        String keycloakUserId = path.substring(path.lastIndexOf('/') + 1);
        log.info("Keycloak user created — id={}, email={}, realm={}", keycloakUserId, email, props.getRealm());

        assignRealmRole(adminToken, keycloakUserId, role);

        return keycloakUserId;
    }
    public void assignRealmRole(String adminToken, String keycloakUserId, String roleName) {
        WebClient client = adminWebClient(adminToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> roleRepresentation = client.get()
                .uri("/admin/realms/" + props.getRealm() + "/roles/" + roleName)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new KeycloakException(
                                "Role '" + roleName + "' not found in realm '" + props.getRealm() + "': " + body))))
                .bodyToMono(Map.class)
                .block();

        if (roleRepresentation == null) {
            throw new KeycloakException("Null role representation returned for role: " + roleName);
        }

        client.post()
                .uri("/admin/realms/" + props.getRealm()
                        + "/users/" + keycloakUserId + "/role-mappings/realm")
                .bodyValue(List.of(roleRepresentation))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                                new KeycloakException("Failed to assign role '" + roleName + "': " + body))))
                .toBodilessEntity()
                .block();

        log.info("Assigned role '{}' to Keycloak user {}", roleName, keycloakUserId);
    }

    public void deleteUser(String keycloakUserId) {
        String adminToken = getAdminToken();

        adminWebClient(adminToken).delete()
                .uri("/admin/realms/" + props.getRealm() + "/users/" + keycloakUserId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                                new KeycloakException("Failed to delete Keycloak user " + keycloakUserId + ": " + body))))
                .toBodilessEntity()
                .block();

        log.info("Keycloak user {} deleted from realm {}", keycloakUserId, props.getRealm());
    }

    private WebClient adminWebClient(String adminToken) {
        return WebClient.builder()
                .baseUrl(props.getAuthServerUrl())
                .defaultHeader("Authorization", "Bearer " + adminToken)
                .defaultHeader("Content-Type",  "application/json")
                .build();
    }
}
