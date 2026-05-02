package com.poolerapp.pooler_bank.payment.service;

import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.payment.dto.PaymentDtos.*;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction.PaymentStatus;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction.PaymentType;
import com.poolerapp.pooler_bank.payment.repository.PaymentTransactionRepository;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository paymentRepository;
    private final AccountCustomerRepository customerRepository;
    private final FineractClientService fineractClient;
    @Value("${payment.simulation.enabled:true}")
    private boolean simulationEnabled;
    @Transactional
    public PaymentResponse simulateDeposit(PaymentRequest req) {
        return processPayment(req, PaymentType.DEPOSIT);
    }
    @Transactional
    public PaymentResponse simulateWithdrawal(PaymentRequest req) {
        return processPayment(req, PaymentType.WITHDRAWAL);
    }

    public List<PaymentResponse> getHistory(String accountNumber) {
        return paymentRepository
                .findByAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    private PaymentResponse processPayment(PaymentRequest req, PaymentType type) {
        Optional<PaymentTransaction> existing =
                paymentRepository.findByIdempotencyKey(req.getIdempotencyKey());

        if (existing.isPresent()) {
            log.info("[IDEMPOTENT REPLAY] key={} type={}", req.getIdempotencyKey(), type);
            PaymentResponse response = toResponse(existing.get());
            response.setIdempotentReplay(true);
            return response;
        }
        AccountCustomer customer = customerRepository.findByAccountNumber(req.getAccountNumber());
        if (customer == null) {
            throw new IllegalArgumentException("Account not found: " + req.getAccountNumber());
        }

        if (type == PaymentType.WITHDRAWAL) {
            if (customer.getAccountBalance().compareTo(req.getAmount()) < 0) {
                throw new IllegalStateException("Insufficient balance for withdrawal");
            }
        }
        PaymentTransaction record;
        try {
            record = PaymentTransaction.builder()
                    .idempotencyKey(req.getIdempotencyKey())
                    .accountNumber(req.getAccountNumber())
                    .type(type)
                    .amount(req.getAmount())
                    .narration(req.getNarration() != null ? req.getNarration() : defaultNarration(type))
                    .status(PaymentStatus.PENDING)
                    .build();
            record = paymentRepository.saveAndFlush(record); // flush forces DB constraint check NOW
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread just wrote the same key
            log.warn("[IDEMPOTENT RACE] key={} — returning stored result", req.getIdempotencyKey());
            PaymentResponse response = toResponse(
                    paymentRepository.findByIdempotencyKey(req.getIdempotencyKey()).orElseThrow());
            response.setIdempotentReplay(true);
            return response;
        }
        try {
            Long fineractTxId;

            if (simulationEnabled) {
                fineractTxId = executeSim(customer, req.getAmount(), type);
                log.info("[SIMULATED] {} of {} on account {}", type, req.getAmount(), req.getAccountNumber());
            } else {
                fineractTxId = executeFineract(customer, req.getAmount(), req.getNarration(), type);
                log.info("[FINERACT] {} of {} on account {} → txId={}",
                        type, req.getAmount(), req.getAccountNumber(), fineractTxId);
            }

            record.setFineractTransactionId(fineractTxId);
            record.setStatus(PaymentStatus.SUCCESS);
            record.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(record);

            return toResponse(record);

        } catch (Exception e) {
            record.setStatus(PaymentStatus.FAILED);
            record.setFailureReason(e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(record);
            log.error("[PAYMENT FAILED] key={} type={} reason={}", req.getIdempotencyKey(), type, e.getMessage());
            throw e;
        }
    }


    private Long executeSim(AccountCustomer customer, BigDecimal amount, PaymentType type) {
        if (type == PaymentType.DEPOSIT) {
            customer.setAccountBalance(customer.getAccountBalance().add(amount));
            customer.setDepositCount(customer.getDepositCount() + 1);
        } else {
            customer.setAccountBalance(customer.getAccountBalance().subtract(amount));
        }
        customerRepository.save(customer);
        return System.currentTimeMillis();
    }

    private Long executeFineract(AccountCustomer customer, BigDecimal amount,
                                  String narration, PaymentType type) {
        if (customer.getFineractSavingsAccountId() == null) {
            throw new IllegalStateException("No Fineract savings account linked to this customer");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionDate", LocalDate.now().toString());
        payload.put("transactionAmount", amount);
        payload.put("paymentTypeId", 1);
        payload.put("note", narration != null ? narration : defaultNarration(type));
        payload.put("locale", "en");
        payload.put("dateFormat", "yyyy-MM-dd");

        Map<String, Object> result;
        if (type == PaymentType.DEPOSIT) {
            result = fineractClient.depositToSavings(customer.getFineractSavingsAccountId(), payload);
            // Mirror balance update
            customer.setAccountBalance(customer.getAccountBalance().add(amount));
            customer.setDepositCount(customer.getDepositCount() + 1);
        } else {
            result = fineractClient.withdrawFromSavings(customer.getFineractSavingsAccountId(), payload);
            customer.setAccountBalance(customer.getAccountBalance().subtract(amount));
        }
        customerRepository.save(customer);

        Object resourceId = result.get("resourceId");
        return resourceId != null ? Long.parseLong(resourceId.toString()) : null;
    }
    private PaymentResponse toResponse(PaymentTransaction tx) {
        PaymentResponse r = new PaymentResponse();
        r.setPaymentId(tx.getId());
        r.setFineractTransactionId(tx.getFineractTransactionId());
        r.setAccountNumber(tx.getAccountNumber());
        r.setAmount(tx.getAmount());
        r.setType(tx.getType().name());
        r.setStatus(tx.getStatus().name());
        r.setNarration(tx.getNarration());
        r.setProcessedAt(tx.getCompletedAt() != null ? tx.getCompletedAt() : tx.getCreatedAt());
        r.setIdempotentReplay(false);
        return r;
    }

    private String defaultNarration(PaymentType type) {
        return type == PaymentType.DEPOSIT ? "Customer deposit" : "Customer withdrawal";
    }
}
