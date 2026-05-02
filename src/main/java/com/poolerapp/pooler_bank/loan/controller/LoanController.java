package com.poolerapp.pooler_bank.loan.controller;

import com.poolerapp.pooler_bank.credit.CreditScoringService;
import com.poolerapp.pooler_bank.loan.dto.LoanDtos.*;
import org.springframework.http.HttpStatus;

import com.poolerapp.pooler_bank.loan.service.LoanService;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan lifecycle endpoints (backed by Apache Fineract)")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;
    private final CreditScoringService creditScoringService;
    private final AccountCustomerRepository customerRepository;

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for a loan (includes credit scoring check)")
    public ResponseEntity<LoanResponse> apply(@Valid @RequestBody LoanApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.applyForLoan(request));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan details")
    public ResponseEntity<LoanResponse> getLoan(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getLoan(loanId));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get all loans for an account")
    public ResponseEntity<List<LoanResponse>> getLoansByAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(loanService.getLoansByAccount(accountNumber));
    }

    @PostMapping("/{loanId}/repay")
    @Operation(summary = "Make a loan repayment via Fineract")
    public ResponseEntity<LoanResponse> repay(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.ok(loanService.repayLoan(loanId, request));
    }

    @PostMapping("/{loanId}/repay/initiate")
    @Operation(summary = "Initiate loan repayment via Paystack — returns payment URL")
    public ResponseEntity<LoanRepaymentInitResponse> initiateRepayment(
            @PathVariable Long loanId,
            @Valid @RequestBody InitiateLoanRepaymentRequest request) {
        return ResponseEntity.ok(loanService.initiateLoanRepayment(loanId, request));
    }

    @GetMapping("/credit-score/{accountNumber}")
    @Operation(summary = "Get credit score for an account holder")
    public ResponseEntity<CreditScoreResponse> getCreditScore(@PathVariable String accountNumber) {
        AccountCustomer customer = customerRepository.findByAccountNumber(accountNumber);
        if (customer == null) return ResponseEntity.notFound().build();

        int score = creditScoringService.calculateScore(customer);
        CreditScoreResponse response = new CreditScoreResponse();
        response.setAccountNumber(accountNumber);
        response.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        response.setScore(score);
        response.setEligible(score >= 30);
        response.setExplanation(buildExplanation(customer, score));

        return ResponseEntity.ok(response);
    }

    private String buildExplanation(AccountCustomer customer, int score) {
        return String.format(
                "Score: %d/100. Deposits: %d (need ≥3). Defaulted: %s. Balance: ₦%s",
                score,
                customer.getDepositCount(),
                customer.isHasDefaultedLoan() ? "YES" : "No",
                customer.getAccountBalance().toPlainString()
        );
    }
}
