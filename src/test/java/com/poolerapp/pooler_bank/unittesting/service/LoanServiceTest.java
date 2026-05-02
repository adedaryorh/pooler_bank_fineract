package com.poolerapp.pooler_bank.unittesting.service;

import com.poolerapp.pooler_bank.credit.CreditScoringService;
import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.fineract.FineractProperties;
import com.poolerapp.pooler_bank.loan.dto.LoanDtos.*;
import com.poolerapp.pooler_bank.loan.model.Loan;
import com.poolerapp.pooler_bank.loan.repository.LoanRepository;
import com.poolerapp.pooler_bank.loan.service.LoanService;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock FineractClientService fineractClientService;
    @Mock FineractProperties fineractProperties;
    @Mock LoanRepository loanRepository;
    @Mock AccountCustomerRepository accountCustomerRepository;
    @Mock CreditScoringService creditScoringService;

    @InjectMocks
    LoanService loanService;

    private AccountCustomer customer;

    @BeforeEach
    void setup() {
        customer = new AccountCustomer();
        customer.setId(1L);
        customer.setAccountNumber("1234567890");
        customer.setFirstName("Chidi");
        customer.setLastName("Nwosu");
        customer.setFineractClientId(42L);
        customer.setDepositCount(5);
        customer.setHasDefaultedLoan(false);
        customer.setAccountBalance(new BigDecimal("1000000"));
    }

    @Test
    void apply_for_loan_happy_path() {
        // Arrange
        when(accountCustomerRepository.findByAccountNumber("1234567890")).thenReturn(customer);
        doNothing().when(creditScoringService).assertEligible(any(), any());
        when(fineractProperties.getLoanProductId()).thenReturn(1L);

        Loan savedLoan = new Loan();
        savedLoan.setId(10L);
        savedLoan.setCustomer(customer);
        savedLoan.setAmount(new BigDecimal("100000"));
        savedLoan.setTermMonths(6);
        savedLoan.setInterestRate(new BigDecimal("24"));
        savedLoan.setStatus(Loan.LoanStatus.SUBMITTED);
        savedLoan.setFineractLoanId(99L);

        when(loanRepository.save(any())).thenReturn(savedLoan);
        when(fineractClientService.createLoan(anyMap())).thenReturn(Map.of("loanId", 99));

        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setAccountNumber("1234567890");
        req.setAmount(new BigDecimal("100000"));
        req.setTermMonths(6);
        req.setPurpose("Business expansion");

        // Act
        LoanResponse response = loanService.applyForLoan(req);

        // Assert
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        assertThat(response.getFineractLoanId()).isEqualTo(99L);
        verify(fineractClientService).createLoan(anyMap());
        verify(creditScoringService).assertEligible(eq(customer), eq(new BigDecimal("100000")));
    }

    @Test
    void approve_loan_calls_fineract() {
        Loan loan = new Loan();
        loan.setId(1L);
        loan.setFineractLoanId(99L);
        loan.setCustomer(customer);
        loan.setAmount(new BigDecimal("100000"));
        loan.setTermMonths(6);
        loan.setInterestRate(new BigDecimal("24"));
        loan.setStatus(Loan.LoanStatus.SUBMITTED);

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(any())).thenReturn(loan);
        when(fineractClientService.approveLoan(eq(99L), anyMap())).thenReturn(Map.of("loanId", 99));

        LoanResponse response = loanService.approveLoan(1L);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        verify(fineractClientService).approveLoan(eq(99L), anyMap());
    }
}
