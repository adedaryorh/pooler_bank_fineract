package com.poolerapp.pooler_bank.wallet.controller;

import com.poolerapp.pooler_bank.wallet.dto.WalletDtos.*;
import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.FundWalletRequest;
import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.RemittanceRequest;

import com.poolerapp.pooler_bank.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Savings account operations (backed by Apache Fineract)")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get live wallet balance from Fineract")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String accountNumber) {
        return ResponseEntity.ok(walletService.getWallet(accountNumber));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into wallet via Fineract savings account")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(walletService.deposit(request));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds from wallet via Fineract savings account")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(walletService.withdraw(request));
    }

    @PostMapping("/fund")
    @Operation(summary = "Initiate wallet funding via Paystack — returns payment URL")
    public ResponseEntity<FundWalletInitResponse> fundWallet(@Valid @RequestBody FundWalletRequest request) {
        return ResponseEntity.ok(walletService.initiateFundWallet(request));
    }

    @PostMapping("/remit")
    @Operation(summary = "Send remittance payout to external bank via Paystack Transfer")
    public ResponseEntity<RemittanceResponse> remit(@Valid @RequestBody RemittanceRequest request) {
        return ResponseEntity.ok(walletService.sendRemittance(request));
    }
}
