package com.poolerapp.pooler_bank.payment.paystack.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poolerapp.pooler_bank.loan.service.LoanService;
import com.poolerapp.pooler_bank.payment.paystack.service.PaystackService;
import com.poolerapp.pooler_bank.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@Slf4j
@RestController
@RequestMapping("/api/paystack/webhook")
@RequiredArgsConstructor
@Tag(name = "Paystack Webhooks", description = "Receives and processes Paystack payment events")
public class PaystackWebhookController {

    private final PaystackService paystackService;
    private final WalletService walletService;
    private final LoanService loanService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Paystack webhook receiver — do not call directly")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("x-paystack-signature") String signature,
            @org.springframework.web.bind.annotation.RequestBody byte[] rawBody) {


        if (!paystackService.verifyWebhookSignature(rawBody, signature)) {
            log.error("Paystack webhook REJECTED — invalid signature");
            return ResponseEntity.status(401).body("Invalid signature");
        }


        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse Paystack webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON");
        }

        String event = root.path("event").asText();
        JsonNode data = root.path("data");

        log.info("Paystack webhook received: event={}", event);


        try {
            switch (event) {

                case "charge.success" -> handleChargeSuccess(data);

                case "transfer.success" -> {
                    String transferCode = data.path("transfer_code").asText();
                    String ref = data.path("reference").asText();
                    log.info("Paystack transfer SUCCESS: transferCode={} ref={}", transferCode, ref);

                }

                case "transfer.failed" -> {
                    String transferCode = data.path("transfer_code").asText();
                    String ref = data.path("reference").asText();
                    String reason = data.path("gateway_response").asText("No reason given");
                    log.error("Paystack transfer FAILED: transferCode={} ref={} reason={} — MANUAL INTERVENTION REQUIRED",
                            transferCode, ref, reason);

                }

                case "transfer.reversed" -> {
                    String transferCode = data.path("transfer_code").asText();
                    log.warn("Paystack transfer REVERSED: transferCode={} — MANUAL INTERVENTION REQUIRED", transferCode);
                }

                default -> log.info("Unhandled Paystack event: {} — ignoring", event);
            }
        } catch (Exception e) {

            log.error("Error processing Paystack webhook event={}: {}", event, e.getMessage(), e);
        }

        return ResponseEntity.ok("OK");
    }
    private void handleChargeSuccess(JsonNode data) {
        String reference = data.path("reference").asText();
        long amountKobo = data.path("amount").asLong();
        BigDecimal amountNaira = paystackService.toNaira(amountKobo);
        String status = data.path("status").asText();

        if (!"success".equalsIgnoreCase(status)) {
            log.warn("charge.success event but status={} for ref={} — skipping", status, reference);
            return;
        }

        log.info("charge.success: ref={} amount=₦{}", reference, amountNaira);

        // ── Step: Always re-verify with Paystack (never trust webhook amount alone) ──
        var verifyResp = paystackService.verifyTransaction(reference);
        if (verifyResp == null || verifyResp.getData() == null || !verifyResp.getData().isSuccessful()) {
            log.error("Paystack verification FAILED for ref={} — not crediting wallet", reference);
            return;
        }

        BigDecimal verifiedAmount = verifyResp.getData().getAmountNaira();

        if (reference.startsWith("WF-")) {
            log.info("Routing ref={} → wallet funding handler", reference);
            walletService.handleWalletFundingWebhook(reference, verifiedAmount);

        } else if (reference.startsWith("LR-")) {
            log.info("Routing ref={} → loan repayment handler", reference);
            loanService.handleLoanRepaymentWebhook(reference, verifiedAmount);

        } else {
            log.warn("Unknown reference prefix for ref={} — cannot route webhook", reference);
        }
    }
}
