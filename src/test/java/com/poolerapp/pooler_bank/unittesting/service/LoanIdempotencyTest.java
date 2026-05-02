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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanIdempotencyTest {

    @Mock FineractClientService fineractClientService;
    @Mock FineractProperties fineractProperties;
    @Mock LoanRepository loanRepository;
    @Mock AccountCustomerRepository accountCustomerRepository;
    @Mock CreditScoringService creditScoringService;

    @InjectMocks LoanService loanService;

    private AccountCustomer customer;

    @BeforeEach
    void setup() {
        customer = new AccountCustomer();
        customer.setId(1L);
        customer.setAccountNumber("9876543210");
        customer.setFirstName("Ngozi");
        customer.setLastName("Obi");
        customer.setFineractClientId(10L);
        customer.setDepositCount(5);
        customer.setAccountBalance(new BigDecimal("2000000"));
        customer.setHasDefaultedLoan(false);
    }

    @Test
    void duplicate_loan_application_returns_stored_result_without_calling_fineract() {
        // Arrange: simulate a previously submitted loan with same idempotency key
        Loan existingLoan = new Loan();
        existingLoan.setId(55L);
        existingLoan.setCustomer(customer);
        existingLoan.setFineractLoanId(200L);
        existingLoan.setAmount(new BigDecimal("500000"));
        existingLoan.setTermMonths(12);
        existingLoan.setInterestRate(new BigDecimal("24"));
        existingLoan.setStatus(Loan.LoanStatus.SUBMITTED);
        existingLoan.setIdempotencyKey("acc-9876-uuid-1111");

        when(loanRepository.findByIdempotencyKey("acc-9876-uuid-1111"))
                .thenReturn(Optional.of(existingLoan));

        LoanApplicationRequest req = buildRequest("9876543210", "500000", 12, "acc-9876-uuid-1111");

        // Act
        LoanResponse response = loanService.applyForLoan(req);

        // Assert
        assertThat(response.getLoanId()).isEqualTo(55L);
        assertThat(response.getFineractLoanId()).isEqualTo(200L);
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        assertThat(response.isIdempotentReplay()).isTrue();

        // Critical: Fineract must NOT be called again
        verify(fineractClientService, never()).createLoan(any());
        // Credit scoring must NOT run again
        verify(creditScoringService, never()).assertEligible(any(), any());
    }

    @Test
    void unique_loan_application_calls_fineract_and_stores_record() {
        when(loanRepository.findByIdempotencyKey("acc-9876-uuid-2222")).thenReturn(Optional.empty());
        when(accountCustomerRepository.findByAccountNumber("9876543210")).thenReturn(customer);
        doNothing().when(creditScoringService).assertEligible(any(), any());
        when(fineractProperties.getLoanProductId()).thenReturn(1L);

        Loan savedLoan = new Loan();
        savedLoan.setId(56L);
        savedLoan.setCustomer(customer);
        savedLoan.setFineractLoanId(201L);
        savedLoan.setAmount(new BigDecimal("500000"));
        savedLoan.setTermMonths(12);
        savedLoan.setInterestRate(new BigDecimal("24"));
        savedLoan.setStatus(Loan.LoanStatus.SUBMITTED);

        when(loanRepository.saveAndFlush(any())).thenReturn(savedLoan);
        when(loanRepository.save(any())).thenReturn(savedLoan);
        when(fineractClientService.createLoan(anyMap()))
                .thenReturn(java.util.Map.of("loanId", 201));

        LoanApplicationRequest req = buildRequest("9876543210", "500000", 12, "acc-9876-uuid-2222");
        LoanResponse response = loanService.applyForLoan(req);

        assertThat(response.isIdempotentReplay()).isFalse();
        verify(fineractClientService).createLoan(anyMap());
    }

    private LoanApplicationRequest buildRequest(String account, String amount, int months, String idempotencyKey) {
        LoanApplicationRequest r = new LoanApplicationRequest();
        r.setAccountNumber(account);
        r.setAmount(new BigDecimal(amount));
        r.setTermMonths(months);
        r.setPurpose("Business");
        r.setIdempotencyKey(idempotencyKey);
        return r;
    }
}
