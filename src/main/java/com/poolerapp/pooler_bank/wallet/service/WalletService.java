package com.poolerapp.pooler_bank.wallet.service;

import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.fineract.FineractProperties;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.*;
import com.poolerapp.pooler_bank.payment.paystack.properties.PaystackProperties;
import com.poolerapp.pooler_bank.payment.paystack.service.PaystackService;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment.PaymentPurpose;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment.PendingPaymentStatus;
import com.poolerapp.pooler_bank.payment.pending.repository.PendingPaymentRepository;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import com.poolerapp.pooler_bank.wallet.dto.WalletDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet service — orchestrates Fineract (ledger) + Paystack (real money movement).
 *
 * Design rules:
 *  1. Balance is NEVER stored locally — always fetched live from Fineract.
 *  2. deposit() is ONLY called after a verified Paystack webhook — never directly by clients.
 *  3. Fineract is debited BEFORE Paystack payout on withdrawal.
 *     If Paystack fails → Fineract debit is reversed (re-credit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final FineractClientService fineractClient;
    private final FineractProperties fineractProps;
    private final AccountCustomerRepository customerRepository;
    private final PaystackService paystackService;
    private final PaystackProperties paystackProperties;
    private final PendingPaymentRepository pendingPaymentRepository;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public Long provisionSavingsAccount(AccountCustomer customer) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", customer.getFineractClientId());
        payload.put("productId", fineractProps.getSavingsProductId());
        payload.put("submittedOnDate", today());
        payload.put("locale", "en");
        payload.put("dateFormat", "yyyy-MM-dd");

        Map<String, Object> created = fineractClient.createSavingsAccount(payload);
        Long savingsId = toLong(created.get("savingsId"));

        fineractClient.approveSavingsAccount(savingsId);
        fineractClient.activateSavingsAccount(savingsId);

        customer.setFineractSavingsAccountId(savingsId);
        customerRepository.save(customer);

        log.info("Provisioned Fineract savings account {} for customer {}", savingsId, customer.getId());
        return savingsId;
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    public WalletResponse getWallet(String accountNumber) {
        AccountCustomer customer = findCustomer(accountNumber);
        Map<String, Object> fineractData = fineractClient.getSavingsAccount(customer.getFineractSavingsAccountId());
        return toWalletResponse(customer, fineractData);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROMPT 2 — Wallet Funding via Paystack
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * STEP 1: Customer wants to add money to their wallet.
     *
     * We do NOT move any money here. We:
     *   1. Generate a unique Paystack reference
     *   2. Call Paystack initializeTransaction → get a payment URL
     *   3. Save a PendingPayment record (PENDING)
     *   4. Return the URL for the client to redirect the user to
     *
     * Money only moves when Paystack webhook confirms payment (handleWalletFundingWebhook).
     */
    @Transactional
    public FundWalletInitResponse initiateFundWallet(FundWalletRequest req) {
        AccountCustomer customer = findCustomer(req.getAccountNumber());

        String reference = "WF-" + req.getAccountNumber() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        InitializeResponse paystackResp = paystackService.initializeTransaction(
                customer.getEmail(),
                req.getAmount(),
                reference,
                paystackProperties.getCallbackUrl()
        );

        if (paystackResp == null || paystackResp.getData() == null) {
            throw new IllegalStateException("Paystack did not return a payment URL");
        }

        // Save pending record — this is how we reconcile the webhook
        PendingPayment pending = PendingPayment.builder()
                .paystackReference(reference)
                .accountNumber(req.getAccountNumber())
                .expectedAmount(req.getAmount())
                .purpose(PaymentPurpose.WALLET_FUNDING)
                .authorizationUrl(paystackResp.getData().getAuthorizationUrl())
                .status(PendingPaymentStatus.PENDING)
                .build();
        pendingPaymentRepository.save(pending);

        log.info("Wallet funding initiated: ref={} amount=₦{} account={}", reference, req.getAmount(), req.getAccountNumber());

        FundWalletInitResponse response = new FundWalletInitResponse();
        response.setReference(reference);
        response.setAuthorizationUrl(paystackResp.getData().getAuthorizationUrl());
        response.setAmount(req.getAmount());
        response.setAccountNumber(req.getAccountNumber());
        response.setMessage("Redirect the user to the authorizationUrl to complete payment");
        return response;
    }

    /**
     * STEP 2: Paystack webhook confirms payment.
     * Called ONLY by PaystackWebhookController after signature verification + verifyTransaction.
     *
     * Flow:
     *   Webhook arrives → controller verifies signature → verifyTransaction →
     *   this method → credit Fineract → mark PendingPayment COMPLETED
     */
    @Transactional
    public void handleWalletFundingWebhook(String reference, BigDecimal verifiedAmount) {
        PendingPayment pending = pendingPaymentRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("No pending payment found for ref: " + reference));

        if (pending.getStatus() == PendingPaymentStatus.COMPLETED) {
            log.warn("Duplicate webhook for already completed payment ref={}", reference);
            return; // idempotent — do nothing
        }

        if (pending.getPurpose() != PaymentPurpose.WALLET_FUNDING) {
            throw new IllegalStateException("Reference " + reference + " is not a WALLET_FUNDING payment");
        }

        // Amount integrity check
        if (verifiedAmount.compareTo(pending.getExpectedAmount()) != 0) {
            log.error("Amount mismatch for ref={}: expected=₦{} received=₦{}", reference, pending.getExpectedAmount(), verifiedAmount);
            pending.setStatus(PendingPaymentStatus.FAILED);
            pending.setFailureReason("Amount mismatch: expected ₦" + pending.getExpectedAmount() + " got ₦" + verifiedAmount);
            pendingPaymentRepository.save(pending);
            return;
        }

        // Credit Fineract
        deposit(pending.getAccountNumber(), verifiedAmount, "Paystack wallet funding ref=" + reference);

        pending.setStatus(PendingPaymentStatus.COMPLETED);
        pendingPaymentRepository.save(pending);

        log.info("Wallet funded successfully: ref={} amount=₦{} account={}", reference, verifiedAmount, pending.getAccountNumber());
    }

    /**
     * Internal deposit — credits Fineract savings account and updates local counters.
     * NOT exposed as a public API endpoint. Only called after verified Paystack payment.
     */
    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String narration) {
        AccountCustomer customer = findCustomer(accountNumber);

        Map<String, Object> payload = buildTransactionPayload(amount, narration);
        Map<String, Object> result = fineractClient.depositToSavings(
                customer.getFineractSavingsAccountId(), payload);

        customer.setDepositCount(customer.getDepositCount() + 1);
        customer.setAccountBalance(customer.getAccountBalance().add(amount));
        customerRepository.save(customer);

        log.info("Fineract deposit ₦{} → account {}", amount, accountNumber);
        return toTransactionResponse(result, "DEPOSIT", amount);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROMPT 3 — Wallet Withdrawal / Remittance Payout via Paystack
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Remittance payout: debit sender's Fineract wallet → pay beneficiary via Paystack Transfer.
     *
     * Ordering matters for safety:
     *   1. Debit Fineract FIRST (internal ledger)
     *   2. Create Paystack recipient (idempotent — same account → same code)
     *   3. Initiate Paystack transfer (payout to beneficiary's bank)
     *   4. If Paystack fails → REVERSE Fineract debit (re-credit)
     *
     * Paystack transfer is async — status starts "pending".
     * A transfer.success or transfer.failed webhook arrives later.
     */
    @Transactional
    public RemittanceResponse sendRemittance(RemittanceRequest req) {
        AccountCustomer sender = findCustomer(req.getSenderAccountNumber());

        // ── Step 1: Debit Fineract (internal ledger) ──────────────────────────
        Map<String, Object> withdrawPayload = buildTransactionPayload(req.getAmount(),
                "Remittance to " + req.getBeneficiaryName());
        Map<String, Object> fineractResult = fineractClient.withdrawFromSavings(
                sender.getFineractSavingsAccountId(), withdrawPayload);

        // Update local balance mirror
        sender.setAccountBalance(sender.getAccountBalance().subtract(req.getAmount()));
        customerRepository.save(sender);

        log.info("Fineract debit ₦{} from account {} for remittance", req.getAmount(), req.getSenderAccountNumber());

        // ── Step 2 & 3: Create recipient + initiate Paystack transfer ─────────
        String paystackReference = "RM-" + req.getSenderAccountNumber() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String transferCode = null;
        String transferStatus = "FAILED";

        try {
            String recipientCode = paystackService.createTransferRecipient(
                    req.getBeneficiaryName(),
                    req.getBeneficiaryAccountNumber(),
                    req.getBeneficiaryBankCode()
            );

            TransferResponse transferResp = paystackService.initiateTransfer(
                    recipientCode,
                    req.getAmount(),
                    paystackReference,
                    req.getNarration() != null ? req.getNarration() : "Pooler Bank Remittance"
            );

            transferCode = transferResp.getData().getTransferCode();
            transferStatus = transferResp.getData().getStatus().toUpperCase(); // PENDING initially

            log.info("Paystack transfer initiated: code={} status={}", transferCode, transferStatus);

        } catch (Exception e) {
            // ── ROLLBACK: Re-credit Fineract if Paystack fails ────────────────
            log.error("Paystack transfer failed for ref={} — reversing Fineract debit: {}", paystackReference, e.getMessage());

            Map<String, Object> reversePayload = buildTransactionPayload(req.getAmount(),
                    "REVERSAL: Paystack payout failed for " + paystackReference);
            fineractClient.depositToSavings(sender.getFineractSavingsAccountId(), reversePayload);

            sender.setAccountBalance(sender.getAccountBalance().add(req.getAmount()));
            customerRepository.save(sender);

            log.info("Fineract debit reversed for account {} after Paystack failure", req.getSenderAccountNumber());
            throw new IllegalStateException("Remittance failed — funds have been returned to wallet: " + e.getMessage());
        }

        RemittanceResponse response = new RemittanceResponse();
        response.setReference(paystackReference);
        response.setTransferCode(transferCode);
        response.setAmount(req.getAmount());
        response.setSenderAccountNumber(req.getSenderAccountNumber());
        response.setBeneficiaryName(req.getBeneficiaryName());
        response.setBeneficiaryAccountNumber(req.getBeneficiaryAccountNumber());
        response.setPaystackStatus(transferStatus);
        response.setMessage("Transfer initiated. Status will update via Paystack webhook.");
        return response;
    }

    // ── Legacy withdraw (internal transfers, simulation mode) ─────────────────

    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest req) {
        AccountCustomer customer = findCustomer(req.getAccountNumber());

        Map<String, Object> payload = buildTransactionPayload(req.getAmount(), req.getNarration());
        Map<String, Object> result = fineractClient.withdrawFromSavings(
                customer.getFineractSavingsAccountId(), payload);

        if (customer.getAccountBalance().compareTo(req.getAmount()) >= 0) {
            customer.setAccountBalance(customer.getAccountBalance().subtract(req.getAmount()));
            customerRepository.save(customer);
        }

        log.info("Withdrawal ₦{} from account {}", req.getAmount(), req.getAccountNumber());
        return toTransactionResponse(result, "WITHDRAWAL", req.getAmount());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccountCustomer findCustomer(String accountNumber) {
        AccountCustomer c = customerRepository.findByAccountNumber(accountNumber);
        if (c == null) throw new IllegalArgumentException("Account not found: " + accountNumber);
        if (c.getFineractSavingsAccountId() == null)
            throw new IllegalStateException("No Fineract wallet linked to: " + accountNumber);
        return c;
    }

    private Map<String, Object> buildTransactionPayload(BigDecimal amount, String narration) {
        Map<String, Object> p = new HashMap<>();
        p.put("transactionDate", today());
        p.put("transactionAmount", amount);
        p.put("paymentTypeId", 1);
        p.put("note", narration);
        p.put("locale", "en");
        p.put("dateFormat", "yyyy-MM-dd");
        return p;
    }

    @SuppressWarnings("unchecked")
    private WalletResponse toWalletResponse(AccountCustomer customer, Map<String, Object> data) {
        WalletResponse r = new WalletResponse();
        r.setAccountNumber(customer.getAccountNumber());
        r.setAccountName(customer.getFirstName() + " " + customer.getLastName());
        r.setFineractSavingsId(customer.getFineractSavingsAccountId());

        Map<String, Object> summary = (Map<String, Object>) data.getOrDefault("summary", new HashMap<>());
        Object bal = summary.get("availableBalance");
        r.setBalance(bal != null ? new BigDecimal(bal.toString()) : BigDecimal.ZERO);

        Map<String, Object> statusMap = (Map<String, Object>) data.getOrDefault("status", new HashMap<>());
        r.setStatus(statusMap.getOrDefault("value", "UNKNOWN").toString());

        Map<String, Object> currency = (Map<String, Object>) data.getOrDefault("currency", new HashMap<>());
        r.setCurrency(currency.getOrDefault("code", "NGN").toString());
        return r;
    }

    private TransactionResponse toTransactionResponse(Map<String, Object> data, String type, BigDecimal amount) {
        TransactionResponse r = new TransactionResponse();
        r.setTransactionId(toLong(data.get("resourceId")));
        r.setType(type);
        r.setAmount(amount);
        r.setDate(today());
        r.setStatus("SUCCESS");
        return r;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }

    private String today() { return LocalDate.now().toString(); }
}
