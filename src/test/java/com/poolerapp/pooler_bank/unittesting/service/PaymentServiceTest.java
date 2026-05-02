package com.poolerapp.pooler_bank.unittesting.service;

import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.payment.dto.PaymentDtos.*;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction.PaymentStatus;
import com.poolerapp.pooler_bank.payment.model.PaymentTransaction.PaymentType;
import com.poolerapp.pooler_bank.payment.repository.PaymentTransactionRepository;
import com.poolerapp.pooler_bank.payment.service.PaymentService;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentTransactionRepository paymentRepository;
    @Mock AccountCustomerRepository customerRepository;
    @Mock FineractClientService fineractClientService;

    @InjectMocks
    PaymentService paymentService;

    private AccountCustomer customer;

    @BeforeEach
    void setup() {
        // Enable simulation mode so no real Fineract calls are made
        ReflectionTestUtils.setField(paymentService, "simulationEnabled", true);

        customer = new AccountCustomer();
        customer.setId(1L);
        customer.setAccountNumber("1234567890");
        customer.setAccountBalance(new BigDecimal("500000"));
        customer.setDepositCount(2);
        customer.setFineractSavingsAccountId(10L);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Test
    void deposit_simulation_succeeds_and_writes_success_record() {
        when(customerRepository.findByAccountNumber("1234567890")).thenReturn(customer);
        when(paymentRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());

        PaymentTransaction saved = buildTx(PaymentType.DEPOSIT, PaymentStatus.SUCCESS);
        when(paymentRepository.saveAndFlush(any())).thenReturn(saved);
        when(paymentRepository.save(any())).thenReturn(saved);

        PaymentRequest req = buildRequest("1234567890", new BigDecimal("10000"), "key-001");
        PaymentResponse response = paymentService.simulateDeposit(req);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getType()).isEqualTo("DEPOSIT");
        assertThat(response.isIdempotentReplay()).isFalse();
        verify(fineractClientService, never()).depositToSavings(any(), any()); // simulation mode
    }

    @Test
    void deposit_increments_deposit_count_for_credit_scoring() {
        when(customerRepository.findByAccountNumber("1234567890")).thenReturn(customer);
        when(paymentRepository.findByIdempotencyKey("key-002")).thenReturn(Optional.empty());

        PaymentTransaction saved = buildTx(PaymentType.DEPOSIT, PaymentStatus.SUCCESS);
        when(paymentRepository.saveAndFlush(any())).thenReturn(saved);
        when(paymentRepository.save(any())).thenReturn(saved);

        paymentService.simulateDeposit(buildRequest("1234567890", new BigDecimal("5000"), "key-002"));

        assertThat(customer.getDepositCount()).isEqualTo(3); // was 2, now 3
    }

    // ── Withdrawal ────────────────────────────────────────────────────────────

    @Test
    void withdrawal_rejects_insufficient_balance() {
        when(customerRepository.findByAccountNumber("1234567890")).thenReturn(customer);
        when(paymentRepository.findByIdempotencyKey("key-003")).thenReturn(Optional.empty());

        PaymentRequest req = buildRequest("1234567890", new BigDecimal("999999"), "key-003");
        assertThatThrownBy(() -> paymentService.simulateWithdrawal(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void duplicate_deposit_key_returns_stored_result_without_processing() {
        PaymentTransaction existingTx = buildTx(PaymentType.DEPOSIT, PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existingTx));

        PaymentRequest req = buildRequest("1234567890", new BigDecimal("10000"), "key-dup");
        PaymentResponse response = paymentService.simulateDeposit(req);

        assertThat(response.isIdempotentReplay()).isTrue();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");

        // Customer repo and Fineract must NOT be called — it's a replay
        verify(customerRepository, never()).findByAccountNumber(any());
        verify(fineractClientService, never()).depositToSavings(any(), any());
    }

    @Test
    void duplicate_withdrawal_key_returns_stored_result() {
        PaymentTransaction existingTx = buildTx(PaymentType.WITHDRAWAL, PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey("key-dup-w")).thenReturn(Optional.of(existingTx));

        PaymentRequest req = buildRequest("1234567890", new BigDecimal("5000"), "key-dup-w");
        PaymentResponse response = paymentService.simulateWithdrawal(req);

        assertThat(response.isIdempotentReplay()).isTrue();
        verify(customerRepository, never()).findByAccountNumber(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentRequest buildRequest(String account, BigDecimal amount, String key) {
        PaymentRequest r = new PaymentRequest();
        r.setAccountNumber(account);
        r.setAmount(amount);
        r.setIdempotencyKey(key);
        r.setNarration("Test payment");
        return r;
    }

    private PaymentTransaction buildTx(PaymentType type, PaymentStatus status) {
        return PaymentTransaction.builder()
                .id(99L)
                .idempotencyKey("key-existing")
                .accountNumber("1234567890")
                .type(type)
                .amount(new BigDecimal("10000"))
                .status(status)
                .build();
    }
}
