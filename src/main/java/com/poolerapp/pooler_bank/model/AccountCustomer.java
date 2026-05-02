package com.poolerapp.pooler_bank.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poolerapp.pooler_bank.enums.CustomerAccountType;
import com.poolerapp.pooler_bank.enums.CustomerAccountStatus;
import com.poolerapp.pooler_bank.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class AccountCustomer implements UserDetails {
    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "customer_sequence"
    )
    @SequenceGenerator(
            name = "author_sequence",
            sequenceName = "customer_sequence",
            allocationSize = 1
    )
    private Long id;
    @NotBlank(message = "First name must not be empty")
    @Column(name = "firstName", nullable = false)
    private String firstName;
    @NotBlank(message = "Last name must not be empty")
    @Column(name = "lastName", nullable = false)
    private String lastName;
    @Column(
            unique = true,
            nullable = false
    )
    @NotBlank(message = "Email must not be empty")
    @Email(regexp = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,3}",
            flags = Pattern.Flag.CASE_INSENSITIVE)
    private String email;
    @NotBlank(message = "Date of Birth must not be empty")
    private String dateOfBirth;
    private String address;
    @NotBlank(message = "Gender must not be empty")
    private String gender;
    @NotBlank(message = "State of Origin must not be empty")
    private String stateOfOrigin;
    @NotBlank(message = "Account number must not be empty")
    private String accountNumber;
    private BigDecimal accountBalance;
    @NotBlank(message = "Phone number must not be empty")
    private String phonenumber;
    private String ninNumber;
    private String bvnNumber;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "password must not be empty")
    private String password;
    private Role role;
    private CustomerAccountStatus customerAccountStatus;
    private CustomerAccountType customerAccountType;

    // ── Keycloak integration field ────────────────────────────────────────────
    /**
     * UUID returned by Keycloak after user creation via Admin API.
     * Stored here so password resets, role changes and account deletion
     * can be synced back to Keycloak without an extra search call.
     */
    @Column(name = "keycloak_user_id", unique = true)
    private String keycloakUserId;

    // ── Fineract integration fields ──────────────────────────────────────────
    @Column(name = "fineract_client_id")
    private Long fineractClientId;

    @Column(name = "fineract_savings_account_id")
    private Long fineractSavingsAccountId;

    @Column(name = "kyc_status")
    private String kycStatus = "PENDING"; // PENDING | VERIFIED | REJECTED

    @Column(name = "deposit_count")
    private int depositCount = 0;

    @Column(name = "has_defaulted_loan")
    private boolean hasDefaultedLoan = false;
    @Version
    private Long version;
    @CreationTimestamp
    @Column(
            updatable = false,
            nullable = false
    )
    private LocalDate createdAt;
    @UpdateTimestamp
    @Column(
            updatable = false
    )
    private LocalDate modifiedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}
