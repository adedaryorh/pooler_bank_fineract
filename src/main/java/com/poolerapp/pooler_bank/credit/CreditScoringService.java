package com.poolerapp.pooler_bank.credit;

import com.poolerapp.pooler_bank.exception.LoanEligibilityException;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditScoringService {

    private static final int MIN_DEPOSITS_REQUIRED = 3;
    private static final double INCOME_MULTIPLIER = 2.0;

    private final AccountCustomerRepository accountCustomerRepository;

    public void assertEligible(AccountCustomer customer, BigDecimal requestedAmount) {
        List<String> rejectionReasons = new ArrayList<>();

        // Rule 1 – deposit history
        if (customer.getDepositCount() < MIN_DEPOSITS_REQUIRED) {
            rejectionReasons.add("Insufficient deposit history: need at least "
                    + MIN_DEPOSITS_REQUIRED + " deposits, found " + customer.getDepositCount());
        }

        // Rule 2 – no active defaults
        if (customer.isHasDefaultedLoan()) {
            rejectionReasons.add("Customer has a previously defaulted loan");
        }

        // Rule 3 – income threshold proxy (2× wallet balance)
        BigDecimal threshold = customer.getAccountBalance()
                .multiply(BigDecimal.valueOf(INCOME_MULTIPLIER));
        if (requestedAmount.compareTo(threshold) > 0) {
            rejectionReasons.add("Requested amount (" + requestedAmount
                    + ") exceeds income threshold (" + threshold + ")");
        }

        if (!rejectionReasons.isEmpty()) {
            String reasons = String.join("; ", rejectionReasons);
            log.warn("Loan eligibility REJECTED for customer {}: {}", customer.getId(), reasons);
            throw new LoanEligibilityException("Loan application rejected: " + reasons);
        }

        log.info("Loan eligibility APPROVED for customer {}", customer.getId());
    }

    public int calculateScore(AccountCustomer customer) {
        int score = 0;
        score += Math.min(customer.getDepositCount() * 10, 40);

        if (!customer.isHasDefaultedLoan()) score += 30;

        if (customer.getAccountBalance().compareTo(BigDecimal.ZERO) > 0) {
            score += Math.min(30, customer.getAccountBalance().intValue() / 1000);
        }

        return Math.min(score, 100);
    }
}
