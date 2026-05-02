package com.poolerapp.pooler_bank.loan.service;

import com.poolerapp.pooler_bank.credit.CreditScoringService;
import com.poolerapp.pooler_bank.payment.paystack.service.PaystackService;
import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.InitializeResponse;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment.PaymentPurpose;
import com.poolerapp.pooler_bank.payment.pending.model.PendingPayment.PendingPaymentStatus;
import com.poolerapp.pooler_bank.payment.pending.repository.PendingPaymentRepository;
import com.poolerapp.pooler_bank.payment.paystack.properties.PaystackProperties;

import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.TransferResponse;

import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.fineract.FineractProperties;
import com.poolerapp.pooler_bank.loan.dto.LoanDtos.*;
import com.poolerapp.pooler_bank.loan.model.Loan;
import com.poolerapp.pooler_bank.loan.model.Loan.LoanStatus;
import com.poolerapp.pooler_bank.loan.repository.LoanRepository;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final FineractClientService fineractClient;
    private final FineractProperties fineractProps;
    private final LoanRepository loanRepository;
    private final AccountCustomerRepository customerRepository;
    private final CreditScoringService creditScoringService;
    private final PaystackService paystackService;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final PaystackProperties paystackProperties;

    @Transactional
    public LoanResponse applyForLoan(LoanApplicationRequest req) {

        Optional<Loan> existing = loanRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("[IDEMPOTENT REPLAY] Loan application key={} already processed, returning stored loan {}",
                    req.getIdempotencyKey(), existing.get().getId());
            LoanResponse response = toResponse(existing.get(), existing.get().getCustomer());
            response.setIdempotentReplay(true);
            return response;
        }

        AccountCustomer customer = findCustomer(req.getAccountNumber());

        creditScoringService.assertEligible(customer, req.getAmount());

        Loan loan;
        try {
            loan = Loan.builder()
                    .customer(customer)
                    .amount(req.getAmount())
                    .termMonths(req.getTermMonths())
                    .interestRate(BigDecimal.valueOf(24.0))
                    .purpose(req.getPurpose())
                    .status(LoanStatus.PENDING_ELIGIBILITY)
                    .idempotencyKey(req.getIdempotencyKey())
                    .build();
            loan = loanRepository.saveAndFlush(loan); // flush forces UNIQUE constraint check NOW
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race: another thread wrote the same idempotency key just now
            log.warn("[IDEMPOTENT RACE] key={} — reading stored result", req.getIdempotencyKey());
            Loan stored = loanRepository.findByIdempotencyKey(req.getIdempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Idempotency race but no stored loan found"));
            LoanResponse response = toResponse(stored, stored.getCustomer());
            response.setIdempotentReplay(true);
            return response;
        }

        // ── Call Fineract ─────────────────────────────────────────────────────
        Map<String, Object> payload = buildLoanPayload(customer, req);
        Map<String, Object> fineractResponse = fineractClient.createLoan(payload);
        Long fineractLoanId = toLong(fineractResponse.get("loanId"));

        // ── Update with Fineract ID ───────────────────────────────────────────
        loan.setFineractLoanId(fineractLoanId);
        loan.setStatus(LoanStatus.SUBMITTED);
        loanRepository.save(loan);

        log.info("Loan {} submitted to Fineract as loan ID {}", loan.getId(), fineractLoanId);
        return toResponse(loan, customer);
    }


    @Transactional
    public LoanResponse approveLoan(Long loanId) {
        Loan loan = getLoanOrThrow(loanId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("approvedOnDate", today());
        payload.put("expectedDisbursementDate", today());
        payload.put("locale", "en");
        payload.put("dateFormat", "yyyy-MM-dd");

        fineractClient.approveLoan(loan.getFineractLoanId(), payload);

        loan.setStatus(LoanStatus.APPROVED);
        loanRepository.save(loan);

        log.info("Loan {} (Fineract {}) approved", loanId, loan.getFineractLoanId());
        return toResponse(loan, loan.getCustomer());
    }


    @Transactional
    public LoanResponse disburseLoan(Long loanId) {
        Loan loan = getLoanOrThrow(loanId);

        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new IllegalStateException("Loan must be APPROVED before disbursement, current: " + loan.getStatus());
        }


        Map<String, Object> payload = new HashMap<>();
        payload.put("actualDisbursementDate", today());
        payload.put("locale", "en");
        payload.put("dateFormat", "yyyy-MM-dd");
        fineractClient.disburseLoan(loan.getFineractLoanId(), payload);

        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedOn(LocalDate.now());

        AccountCustomer borrower = loan.getCustomer();
        String disbAcct = loan.getDisbursementAccountNumber();
        String disbBank = loan.getDisbursementBankCode();

        if (disbAcct != null && disbBank != null) {
            try {
                // Create or reuse recipient
                String recipientCode = loan.getPaystackRecipientCode();
                if (recipientCode == null) {
                    recipientCode = paystackService.createTransferRecipient(
                            borrower.getFirstName() + " " + borrower.getLastName(),
                            disbAcct,
                            disbBank
                    );
                    loan.setPaystackRecipientCode(recipientCode);
                }

                String disburseRef = "LD-" + loanId + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                TransferResponse transferResp = paystackService.initiateTransfer(
                        recipientCode,
                        loan.getAmount(),
                        disburseRef,
                        "Pooler Bank Loan Disbursement — Loan #" + loanId
                );

                loan.setPaystackTransferCode(transferResp.getData().getTransferCode());
                log.info("Paystack disbursement transfer initiated: transferCode={} status={}",
                        transferResp.getData().getTransferCode(), transferResp.getData().getStatus());

            } catch (Exception e) {

                log.error("Paystack payout failed for loan {} — MANUAL INTERVENTION REQUIRED: {}",
                        loanId, e.getMessage());

            }
        } else {
            log.warn("Loan {} has no disbursement bank details — Paystack payout skipped", loanId);
        }

        loanRepository.save(loan);
        log.info("Loan {} (Fineract {}) disbursed", loanId, loan.getFineractLoanId());
        return toResponse(loan, loan.getCustomer());
    }


    @Transactional
    public LoanResponse repayLoan(Long loanId, LoanRepaymentRequest req) {
        Loan loan = getLoanOrThrow(loanId);

        if (loan.getStatus() != LoanStatus.DISBURSED) {
            throw new IllegalStateException("Only disbursed loans can be repaid");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionDate", today());
        payload.put("transactionAmount", req.getAmount());
        payload.put("paymentTypeId", 1);
        payload.put("locale", "en");
        payload.put("dateFormat", "yyyy-MM-dd");

        fineractClient.repayLoan(loan.getFineractLoanId(), payload);

        Map<String, Object> fineractLoan = fineractClient.getLoan(loan.getFineractLoanId());
        Map<?, ?> statusMap = (Map<?, ?>) fineractLoan.getOrDefault("status", new HashMap<>());
        String fineractStatus = statusMap.getOrDefault("value", "").toString();

        if ("Closed (obligations met)".equalsIgnoreCase(fineractStatus) ||
                "closedObligationsMet".equalsIgnoreCase(fineractStatus)) {
            loan.setStatus(LoanStatus.REPAID);
            loan.setRepaidOn(LocalDate.now());
            loan.getCustomer().setHasDefaultedLoan(false);
        }

        loanRepository.save(loan);
        log.info("Repayment of {} on loan {}", req.getAmount(), loanId);
        return toResponse(loan, loan.getCustomer());
    }

    @Transactional
    public LoanRepaymentInitResponse initiateLoanRepayment(
            Long loanId, com.poolerapp.pooler_bank.loan.dto.LoanDtos.InitiateLoanRepaymentRequest req) {
        Loan loan = getLoanOrThrow(loanId);

        if (loan.getStatus() != LoanStatus.DISBURSED) {
            throw new IllegalStateException("Only DISBURSED loans can be repaid. Current: " + loan.getStatus());
        }

        String reference = "LR-" + loanId + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        InitializeResponse paystackResp = paystackService.initializeTransaction(
                req.getPayerEmail(), req.getAmount(), reference, paystackProperties.getCallbackUrl());

        if (paystackResp == null || paystackResp.getData() == null) {
            throw new IllegalStateException("Paystack did not return a repayment URL");
        }

        PendingPayment pending = PendingPayment.builder()
                .paystackReference(reference)
                .accountNumber(loan.getCustomer().getAccountNumber())
                .expectedAmount(req.getAmount())
                .purpose(PaymentPurpose.LOAN_REPAYMENT)
                .loanId(loanId)
                .authorizationUrl(paystackResp.getData().getAuthorizationUrl())
                .status(PendingPaymentStatus.PENDING)
                .build();
        pendingPaymentRepository.save(pending);

        log.info("Loan repayment initiated: loanId={} ref={} amount=₦{}", loanId, reference, req.getAmount());

        LoanRepaymentInitResponse response = new LoanRepaymentInitResponse();
        response.setLoanId(loanId);
        response.setReference(reference);
        response.setAuthorizationUrl(paystackResp.getData().getAuthorizationUrl());
        response.setAmount(req.getAmount());
        response.setMessage("Redirect the borrower to authorizationUrl to complete repayment");
        return response;
    }


    @Transactional
    public void handleLoanRepaymentWebhook(String reference, java.math.BigDecimal verifiedAmount) {
        PendingPayment pending = pendingPaymentRepository.findByPaystackReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("No pending repayment for ref: " + reference));

        if (pending.getStatus() == PendingPaymentStatus.COMPLETED) {
            log.warn("Duplicate repayment webhook for ref={}", reference);
            return;
        }

        if (pending.getPurpose() != PaymentPurpose.LOAN_REPAYMENT) {
            throw new IllegalStateException("Reference " + reference + " is not a LOAN_REPAYMENT");
        }

        // Record repayment in Fineract
        com.poolerapp.pooler_bank.loan.dto.LoanDtos.LoanRepaymentRequest repayReq =
                new com.poolerapp.pooler_bank.loan.dto.LoanDtos.LoanRepaymentRequest();
        repayReq.setAmount(verifiedAmount);
        repayLoan(pending.getLoanId(), repayReq);

        pending.setStatus(PendingPaymentStatus.COMPLETED);
        pendingPaymentRepository.save(pending);

        log.info("Loan repayment completed via webhook: loanId={} ref={} amount=₦{}",
                pending.getLoanId(), reference, verifiedAmount);
    }

    public List<LoanResponse> getLoansByAccount(String accountNumber) {
        AccountCustomer customer = findCustomer(accountNumber);
        return loanRepository.findByCustomerId(customer.getId())
                .stream()
                .map(l -> toResponse(l, customer))
                .collect(Collectors.toList());
    }

    public LoanResponse getLoan(Long loanId) {
        Loan loan = getLoanOrThrow(loanId);
        return toResponse(loan, loan.getCustomer());
    }


    private Map<String, Object> buildLoanPayload(AccountCustomer customer, LoanApplicationRequest req) {
        Map<String, Object> p = new HashMap<>();
        p.put("clientId", customer.getFineractClientId());
        p.put("productId", fineractProps.getLoanProductId());
        p.put("principal", req.getAmount());
        p.put("loanTermFrequency", req.getTermMonths());
        p.put("loanTermFrequencyType", 2); // months
        p.put("numberOfRepayments", req.getTermMonths());
        p.put("repaymentEvery", 1);
        p.put("repaymentFrequencyType", 2); // monthly
        p.put("interestRatePerPeriod", 2.0); // 2% per month = 24% p.a.
        p.put("amortizationType", 1); // equal installments
        p.put("interestType", 0);     // declining balance
        p.put("interestCalculationPeriodType", 1);
        p.put("transactionProcessingStrategyCode", "mifos-standard-strategy");
        p.put("submittedOnDate", today());
        p.put("expectedDisbursementDate", today());
        p.put("locale", "en");
        p.put("dateFormat", "yyyy-MM-dd");
        return p;
    }

    private LoanResponse toResponse(Loan loan, AccountCustomer customer) {
        LoanResponse r = new LoanResponse();
        r.setLoanId(loan.getId());
        r.setFineractLoanId(loan.getFineractLoanId());
        r.setAccountNumber(customer.getAccountNumber());
        r.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        r.setAmount(loan.getAmount());
        r.setTermMonths(loan.getTermMonths());
        r.setInterestRate(loan.getInterestRate());
        r.setStatus(loan.getStatus().name());
        r.setPurpose(loan.getPurpose());
        r.setDisbursedOn(loan.getDisbursedOn());
        r.setCreatedAt(loan.getCreatedAt());
        r.setPaystackTransferCode(loan.getPaystackTransferCode());
        r.setPaystackDisbursementStatus(loan.getPaystackTransferCode() != null ? "PENDING" : null);
        return r;
    }

    private Loan getLoanOrThrow(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    private AccountCustomer findCustomer(String accountNumber) {
        AccountCustomer c = customerRepository.findByAccountNumber(accountNumber);
        if (c == null) throw new IllegalArgumentException("Account not found: " + accountNumber);
        return c;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }

    private String today() {
        return LocalDate.now().toString();
    }
}
