package com.poolerapp.pooler_bank.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poolerapp.pooler_bank.enums.CustomerAccountStatus;
import com.poolerapp.pooler_bank.enums.CustomerAccountType;
import com.poolerapp.pooler_bank.enums.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountCustomerRequest {
    private String firstName;
    private String lastName;
    private String email;
    @JsonFormat(pattern = "dd-MMM-yyyy")
    private String dateOfBirth;
    private String address;
    private String gender;
    private String stateOfOrigin;
    private String accountNumber;
    private BigDecimal accountBalance;
    private String phonenumber;
    private String ninNumber;
    private String bvnNumber;
    private String password;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private CustomerAccountStatus customerAccountStatus;
    @Enumerated(EnumType.STRING)
    private CustomerAccountType customerAccountType;

}
