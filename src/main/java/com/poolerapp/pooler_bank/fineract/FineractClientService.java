package com.poolerapp.pooler_bank.fineract;

import com.poolerapp.pooler_bank.exception.FineractException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineractClientService {

    @Qualifier("fineractWebClient")
    private final WebClient webClient;

    @Retry(name = "fineract")
    public Map<String, Object> createClient(Map<String, Object> payload) {
        log.info("Fineract → POST /clients");
        return post("/clients", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> getClient(Long clientId) {
        log.info("Fineract → GET /clients/{}", clientId);
        return get("/clients/" + clientId);
    }

    @Retry(name = "fineract")
    public Map<String, Object> createSavingsAccount(Map<String, Object> payload) {
        log.info("Fineract → POST /savingsaccounts");
        return post("/savingsaccounts", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> approveSavingsAccount(Long accountId) {
        log.info("Fineract → POST /savingsaccounts/{}/approve", accountId);
        return post("/savingsaccounts/" + accountId + "?command=approve",
                Map.of("approvedOnDate", today()));
    }

    @Retry(name = "fineract")
    public Map<String, Object> activateSavingsAccount(Long accountId) {
        log.info("Fineract → POST /savingsaccounts/{}/activate", accountId);
        return post("/savingsaccounts/" + accountId + "?command=activate",
                Map.of("activatedOnDate", today()));
    }

    @Retry(name = "fineract")
    public Map<String, Object> getSavingsAccount(Long accountId) {
        log.info("Fineract → GET /savingsaccounts/{}", accountId);
        return get("/savingsaccounts/" + accountId);
    }

    @Retry(name = "fineract")
    public Map<String, Object> depositToSavings(Long accountId, Map<String, Object> payload) {
        log.info("Fineract → POST /savingsaccounts/{}/transactions?command=deposit", accountId);
        return post("/savingsaccounts/" + accountId + "/transactions?command=deposit", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> withdrawFromSavings(Long accountId, Map<String, Object> payload) {
        log.info("Fineract → POST /savingsaccounts/{}/transactions?command=withdrawal", accountId);
        return post("/savingsaccounts/" + accountId + "/transactions?command=withdrawal", payload);
    }
    @Retry(name = "fineract")
    public Map<String, Object> createLoan(Map<String, Object> payload) {
        log.info("Fineract → POST /loans");
        return post("/loans", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> approveLoan(Long loanId, Map<String, Object> payload) {
        log.info("Fineract → POST /loans/{}/approve", loanId);
        return post("/loans/" + loanId + "?command=approve", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> disburseLoan(Long loanId, Map<String, Object> payload) {
        log.info("Fineract → POST /loans/{}/disburse", loanId);
        return post("/loans/" + loanId + "?command=disburse", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> repayLoan(Long loanId, Map<String, Object> payload) {
        log.info("Fineract → POST /loans/{}/repayment", loanId);
        return post("/loans/" + loanId + "/transactions?command=repayment", payload);
    }

    @Retry(name = "fineract")
    public Map<String, Object> getLoan(Long loanId) {
        log.info("Fineract → GET /loans/{}", loanId);
        return get("/loans/" + loanId);
    }

    @Retry(name = "fineract")
    public Map<String, Object> getLoansByClientId(Long clientId) {
        log.info("Fineract → GET /loans?clientId={}", clientId);
        return get("/loans?clientId=" + clientId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return webClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(
                                            new FineractException("Fineract error: " + errorBody,
                                                    response.statusCode().value()))))
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fineract POST {} → {} {}", path, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new FineractException(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(
                                            new FineractException("Fineract error: " + errorBody,
                                                    response.statusCode().value()))))
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Fineract GET {} → {} {}", path, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new FineractException(ex.getResponseBodyAsString(), ex.getStatusCode().value());
        }
    }

    private String today() {
        return java.time.LocalDate.now().toString(); // yyyy-MM-dd
    }
}
