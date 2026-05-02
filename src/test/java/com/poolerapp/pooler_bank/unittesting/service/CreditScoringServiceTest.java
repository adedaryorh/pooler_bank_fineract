package com.poolerapp.pooler_bank.unittesting.service;

import com.poolerapp.pooler_bank.credit.CreditScoringService;
import com.poolerapp.pooler_bank.exception.LoanEligibilityException;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CreditScoringServiceTest {

    @Mock
    AccountCustomerRepository accountCustomerRepository;

    @InjectMocks
    CreditScoringService creditScoringService;

    private AccountCustomer customer;

    @BeforeEach
    void setup() {
        customer = new AccountCustomer();
        customer.setId(1L);
        customer.setFirstName("Ade");
        customer.setLastName("Okon");
        customer.setDepositCount(5);
        customer.setHasDefaultedLoan(false);
        customer.setAccountBalance(new BigDecimal("500000"));
    }

    @Test
    void eligible_customer_passes_scoring() {
        assertThatNoException()
                .isThrownBy(() -> creditScoringService.assertEligible(customer, new BigDecimal("200000")));
    }

    @Test
    void rejects_insufficient_deposit_history() {
        customer.setDepositCount(1);
        assertThatThrownBy(() -> creditScoringService.assertEligible(customer, new BigDecimal("10000")))
                .isInstanceOf(LoanEligibilityException.class)
                .hasMessageContaining("Insufficient deposit history");
    }

    @Test
    void rejects_defaulted_customer() {
        customer.setHasDefaultedLoan(true);
        assertThatThrownBy(() -> creditScoringService.assertEligible(customer, new BigDecimal("10000")))
                .isInstanceOf(LoanEligibilityException.class)
                .hasMessageContaining("defaulted loan");
    }

    @Test
    void rejects_amount_exceeding_income_threshold() {
        // balance=500k, threshold=1M, request=1.5M → should fail
        assertThatThrownBy(() -> creditScoringService.assertEligible(customer, new BigDecimal("1500000")))
                .isInstanceOf(LoanEligibilityException.class)
                .hasMessageContaining("income threshold");
    }

    @Test
    void score_is_between_0_and_100() {
        int score = creditScoringService.calculateScore(customer);
        assertThat(score).isBetween(0, 100);
    }

    @Test
    void defaulted_customer_gets_lower_score() {
        int goodScore = creditScoringService.calculateScore(customer);
        customer.setHasDefaultedLoan(true);
        int badScore = creditScoringService.calculateScore(customer);
        assertThat(goodScore).isGreaterThan(badScore);
    }
}
