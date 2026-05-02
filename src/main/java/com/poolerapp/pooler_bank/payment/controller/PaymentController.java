package com.poolerapp.pooler_bank.payment.controller;

import com.poolerapp.pooler_bank.payment.dto.PaymentDtos.*;
import com.poolerapp.pooler_bank.payment.service.PaymentService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Simulated deposit and withdrawal — Module 7")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Simulate a deposit",
        description = "Deposits funds via PaymentService → Fineract savings account. " +
                      "Supply a unique idempotencyKey per request — duplicate keys replay " +
                      "the original result without charging twice."
    )
    public ResponseEntity<PaymentResponse> deposit(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.simulateDeposit(request));
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Simulate a withdrawal",
        description = "Withdraws funds via PaymentService → Fineract savings account. " +
                      "Idempotency key prevents duplicate debits on retries."
    )
    public ResponseEntity<PaymentResponse> withdraw(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.simulateWithdrawal(request));
    }

    @GetMapping("/history/{accountNumber}")
    @Operation(
        summary = "Payment history for an account",
        description = "Returns all payment records (deposits + withdrawals) ordered newest first."
    )
    public ResponseEntity<List<PaymentResponse>> history(@PathVariable String accountNumber) {
        return ResponseEntity.ok(paymentService.getHistory(accountNumber));
    }
}
