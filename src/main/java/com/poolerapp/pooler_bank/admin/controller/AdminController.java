package com.poolerapp.pooler_bank.admin.controller;

import com.poolerapp.pooler_bank.loan.dto.LoanDtos.*;
import com.poolerapp.pooler_bank.loan.model.Loan;
import com.poolerapp.pooler_bank.loan.repository.LoanRepository;
import com.poolerapp.pooler_bank.loan.service.LoanService;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only operations — loan approval, user management")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final LoanService loanService;
    private final LoanRepository loanRepository;
    private final AccountCustomerRepository customerRepository;

    // ── Loan management ───────────────────────────────────────────────────────

    @PostMapping("/loans/{loanId}/approve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Approve a submitted loan — calls Fineract approve API")
    public ResponseEntity<LoanResponse> approveLoan(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.approveLoan(loanId));
    }

    @PostMapping("/loans/{loanId}/disburse")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Disburse an approved loan — calls Fineract disburse API")
    public ResponseEntity<LoanResponse> disburseLoan(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.disburseLoan(loanId));
    }

    @GetMapping("/loans")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "List all loans (all statuses)")
    public ResponseEntity<List<Loan>> getAllLoans() {
        return ResponseEntity.ok(loanRepository.findAll());
    }

    @GetMapping("/loans/status/{status}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Filter loans by status (SUBMITTED, APPROVED, DISBURSED …)")
    public ResponseEntity<List<Loan>> getLoansByStatus(@PathVariable String status) {
        Loan.LoanStatus loanStatus = Loan.LoanStatus.valueOf(status.toUpperCase());
        List<Loan> loans = loanRepository.findAll().stream()
                .filter(l -> l.getStatus() == loanStatus)
                .toList();
        return ResponseEntity.ok(loans);
    }

    // ── User management ───────────────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "List all registered customers")
    public ResponseEntity<List<AccountCustomer>> getAllUsers() {
        return ResponseEntity.ok(customerRepository.findAll());
    }

    @GetMapping("/users/{accountNumber}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Get a specific customer by account number")
    public ResponseEntity<AccountCustomer> getUser(@PathVariable String accountNumber) {
        AccountCustomer customer = customerRepository.findByAccountNumber(accountNumber);
        if (customer == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/users/{accountNumber}/kyc")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Update KYC status for a customer (PENDING|VERIFIED|REJECTED)")
    public ResponseEntity<Map<String, Object>> updateKyc(
            @PathVariable String accountNumber,
            @RequestParam String status) {
        AccountCustomer customer = customerRepository.findByAccountNumber(accountNumber);
        if (customer == null) return ResponseEntity.notFound().build();
        customer.setKycStatus(status.toUpperCase());
        customerRepository.save(customer);
        return ResponseEntity.ok(Map.of(
                "accountNumber", accountNumber,
                "kycStatus", status.toUpperCase(),
                "message", "KYC status updated"
        ));
    }

    @PutMapping("/users/{accountNumber}/default-flag")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Flag or unflag a customer as having defaulted on a loan")
    public ResponseEntity<Map<String, Object>> setDefaultFlag(
            @PathVariable String accountNumber,
            @RequestParam boolean hasDefaulted) {
        AccountCustomer customer = customerRepository.findByAccountNumber(accountNumber);
        if (customer == null) return ResponseEntity.notFound().build();
        customer.setHasDefaultedLoan(hasDefaulted);
        customerRepository.save(customer);
        return ResponseEntity.ok(Map.of(
                "accountNumber", accountNumber,
                "hasDefaultedLoan", hasDefaulted,
                "message", "Default flag updated"
        ));
    }

    // ── System health ─────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "System dashboard stats")
    public ResponseEntity<Map<String, Object>> dashboard() {
        long totalUsers = customerRepository.count();
        long totalLoans = loanRepository.count();
        long activeLoans = loanRepository.findAll().stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.DISBURSED)
                .count();
        long pendingLoans = loanRepository.findAll().stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.SUBMITTED)
                .count();

        return ResponseEntity.ok(Map.of(
                "totalCustomers", totalUsers,
                "totalLoans", totalLoans,
                "activeLoans", activeLoans,
                "pendingApproval", pendingLoans
        ));
    }
}
