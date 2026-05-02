package com.poolerapp.pooler_bank.payment.paystack.service;

import com.poolerapp.pooler_bank.payment.paystack.dto.PaystackDtos.*;
import com.poolerapp.pooler_bank.payment.paystack.properties.PaystackProperties;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaystackService {

    private final PaystackProperties props;


    private WebClient client() {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getSecretKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Retry(name = "paystack")
    public InitializeResponse initializeTransaction(String email, BigDecimal amount,
                                                     String reference, String callbackUrl) {
        InitializeRequest req = new InitializeRequest();
        req.setEmail(email);
        req.setAmount(toKobo(amount));
        req.setReference(reference);
        req.setCallbackUrl(callbackUrl != null ? callbackUrl : props.getCallbackUrl());

        log.info("Paystack → POST /transaction/initialize ref={} amount=₦{}", reference, amount);

        return client().post()
                .uri("/transaction/initialize")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new PaystackException(
                                "initializeTransaction failed: " + body, res.statusCode().value()))))
                .bodyToMono(InitializeResponse.class)
                .block();
    }

    @Retry(name = "paystack")
    public VerifyResponse verifyTransaction(String reference) {
        log.info("Paystack → GET /transaction/verify/{}", reference);

        return client().get()
                .uri("/transaction/verify/" + reference)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new PaystackException(
                                "verifyTransaction failed for ref=" + reference + ": " + body,
                                res.statusCode().value()))))
                .bodyToMono(VerifyResponse.class)
                .block();
    }

    @Retry(name = "paystack")
    public String createTransferRecipient(String name, String accountNumber, String bankCode) {
        RecipientRequest req = new RecipientRequest();
        req.setName(name);
        req.setAccountNumber(accountNumber);
        req.setBankCode(bankCode);

        log.info("Paystack → POST /transferrecipient acct={} bank={}", accountNumber, bankCode);

        RecipientResponse response = client().post()
                .uri("/transferrecipient")
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new PaystackException(
                                "createTransferRecipient failed: " + body, res.statusCode().value()))))
                .bodyToMono(RecipientResponse.class)
                .block();

        if (response == null || response.getData() == null) {
            throw new PaystackException("Null response creating transfer recipient for " + accountNumber);
        }

        log.info("Paystack recipient created: code={}", response.getData().getRecipientCode());
        return response.getData().getRecipientCode();
    }

    @Retry(name = "paystack")
    public TransferResponse initiateTransfer(String recipientCode, BigDecimal amount,
                                              String reference, String reason) {
        TransferRequest req = new TransferRequest();
        req.setRecipient(recipientCode);
        req.setAmount(toKobo(amount));
        req.setReference(reference);
        req.setReason(reason != null ? reason : "Pooler Bank Transfer");

        log.info("Paystack → POST /transfer recipient={} amount=₦{} ref={}", recipientCode, amount, reference);

        try {
            TransferResponse response = client().post()
                    .uri("/transfer")
                    .bodyValue(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new PaystackException(
                                    "initiateTransfer failed: " + body, res.statusCode().value()))))
                    .bodyToMono(TransferResponse.class)
                    .block();

            if (response == null || response.getData() == null) {
                throw new PaystackException("Null response initiating transfer ref=" + reference);
            }

            log.info("Paystack transfer initiated: transferCode={} status={}",
                    response.getData().getTransferCode(), response.getData().getStatus());
            return response;

        } catch (WebClientResponseException ex) {
            log.error("Paystack transfer failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaystackException("Paystack transfer failed: " + ex.getResponseBodyAsString(),
                    ex.getStatusCode().value());
        }
    }

    public boolean verifyWebhookSignature(byte[] payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    props.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(payload);
            String computed = HexFormat.of().formatHex(hash);
            boolean valid = computed.equalsIgnoreCase(signature);
            if (!valid) {
                log.warn("Paystack webhook signature mismatch — possible spoofed request");
            }
            return valid;
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }


    /** Naira → Kobo (₦1 = 100 kobo). Paystack expects integer kobo. */
    public long toKobo(BigDecimal naira) {
        return naira.multiply(BigDecimal.valueOf(100)).longValue();
    }
    public BigDecimal toNaira(long kobo) {
        return BigDecimal.valueOf(kobo).divide(BigDecimal.valueOf(100));
    }
}
