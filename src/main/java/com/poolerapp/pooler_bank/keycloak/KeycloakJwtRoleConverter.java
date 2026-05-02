package com.poolerapp.pooler_bank.keycloak;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts Keycloak JWT claims → Spring Security GrantedAuthority list.
 *
 * Keycloak encodes realm roles inside the JWT like this:
 * {
 *   "realm_access": {
 *     "roles": ["ADMIN", "USER", "offline_access", ...]
 *   }
 * }
 *
 * Spring's default converter only reads "scope" claims, so without this
 * converter all Keycloak tokens would appear to have no roles and every
 * @PreAuthorize("hasRole('ADMIN')") check would fail with 403.
 *
 * This converter:
 *  1. Reads realm_access.roles from the Keycloak JWT
 *  2. Skips Keycloak-internal roles (offline_access, uma_authorization)
 *  3. Prefixes each role with "ROLE_" → "ADMIN" becomes "ROLE_ADMIN"
 *
 * Result: all existing @PreAuthorize annotations work without any change.
 */
public class KeycloakJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM        = "roles";
    private static final String ROLE_PREFIX        = "ROLE_";


    private static final List<String> IGNORED_ROLES = List.of(
            "offline_access", "uma_authorization"
    );

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);

        if (realmAccess == null || !realmAccess.containsKey(ROLES_CLAIM)) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get(ROLES_CLAIM);

        return roles.stream()
                .filter(role -> !IGNORED_ROLES.contains(role))
                .filter(role -> !role.startsWith("default-roles-"))
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.toList());
    }
}
