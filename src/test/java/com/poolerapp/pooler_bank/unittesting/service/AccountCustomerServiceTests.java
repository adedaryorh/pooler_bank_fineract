package com.poolerapp.pooler_bank.unittesting.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.poolerapp.pooler_bank.dto.AccountCustomerRequest;
import com.poolerapp.pooler_bank.dto.BankResponse;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import com.poolerapp.pooler_bank.service.AccountCustomerServiceImpl;
import com.poolerapp.pooler_bank.utils.AccountUtils;
import com.poolerapp.pooler_bank.enums.CustomerAccountStatus;
import com.poolerapp.pooler_bank.enums.CustomerAccountType;
import com.poolerapp.pooler_bank.enums.Role;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
public class AccountCustomerServiceTests {
    @Mock
    private AccountCustomerRepository accountCustomerRepository;
    @InjectMocks
    private AccountCustomerServiceImpl accountCustomerService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountUtils accountUtils;

    @BeforeEach
    public void setUp() {
        //accountCustomerRepository = Mockito.mock(AccountCustomerRepository.class);
        MockitoAnnotations.initMocks(this);
        //accountCustomerService = new AccountCustomerServiceImpl(accountCustomerRepository);
    }
    @DisplayName("Junit test for creating account")
    @Test
    public void givenAccountCustomerRequest_whenCreatAnAccount_thenReturnsBankResponse() {
        // givem - precondition or setup
        AccountCustomerRequest accountCustomerRequest = AccountCustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("idris@gmail.com")
                .dateOfBirth("1990-01-01")
                .address("123 Main St")
                .gender("Male")
                .stateOfOrigin("Lagos")
                .accountNumber("1234567890")
                .accountBalance(BigDecimal.ZERO)
                .phonenumber("555-123-4567")
                .ninNumber("5534-123-4567")
                .bvnNumber("22496786601")
                .password("1234")
                .role(Role.ROLE_USER)
                .customerAccountStatus(CustomerAccountStatus.ACTIVE)
                .customerAccountType(CustomerAccountType.BusinessAccount)
                .build();

        BDDMockito.given(accountCustomerRepository.findByEmail(accountCustomerRequest.getEmail())).willReturn(Optional.empty());
        //BDDMockito.given(accountCustomerRepository.save(accountCustomerRequest)).willReturn(accountCustomerRequest);
        System.out.println(accountCustomerRepository);
        System.out.println(accountCustomerService);
        //when
        BankResponse bankResponse = accountCustomerService.creatAnAnccount(accountCustomerRequest);
        System.out.println(bankResponse);

        // Then
        assertThat(bankResponse).isNotNull();
        assertThat(bankResponse.getResponseCode()).isEqualTo(AccountUtils.ACCOUNT_CREDITED_SUCCESS_CODE);
        assertThat(bankResponse.getResponseMessage()).isEqualTo(AccountUtils.ACCOUNT_CREDITED_SUCCESS_MSG);
    }
}

