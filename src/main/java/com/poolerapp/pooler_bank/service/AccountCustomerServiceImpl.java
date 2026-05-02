package com.poolerapp.pooler_bank.service;

import com.poolerapp.pooler_bank.config.JwtTokenGenerator;
import com.poolerapp.pooler_bank.dto.*;
import com.poolerapp.pooler_bank.enums.CustomerAccountStatus;
import com.poolerapp.pooler_bank.enums.CustomerAccountType;
import com.poolerapp.pooler_bank.enums.Role;
import com.poolerapp.pooler_bank.fineract.FineractClientService;
import com.poolerapp.pooler_bank.fineract.FineractProperties;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import com.poolerapp.pooler_bank.utils.AccountUtils;
import com.poolerapp.pooler_bank.keycloak.KeycloakAdminService;
import com.poolerapp.pooler_bank.wallet.service.WalletService;
import org.springframework.transaction.annotation.Transactional;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Slf4j
@Service
@Builder
public class AccountCustomerServiceImpl implements AccountCustomerService {

    private static final Logger logger = Logger.getLogger(TransactionServiceImpl.class.getName());

    @Autowired
    AccountCustomerRepository accountCustomerRepository;

    @Autowired
    TransactionService transactionService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtTokenGenerator jwtTokenGenerator;

    @Autowired
    FineractClientService fineractClientService;

    @Autowired
    FineractProperties fineractProperties;

    @Autowired
    @Lazy
    WalletService walletService;

    @Autowired
    KeycloakAdminService keycloakAdminService;

    // ── Registration: save user → register Fineract client → provision wallet ─

    @Override
    @Transactional
    public BankResponse creatAnAnccount(AccountCustomerRequest req) {
        if (accountCustomerRepository.existsByAccountNumber(req.getAccountNumber()) ||
                accountCustomerRepository.existsByEmail(req.getEmail())) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_EXISTS_MSG)
                    .build();
        }

        // Step 1 – persist locally
        AccountCustomer customer = AccountCustomer.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .dateOfBirth(req.getDateOfBirth())
                .address(req.getAddress())
                .gender(req.getGender())
                .stateOfOrigin(req.getStateOfOrigin())
                .accountNumber(AccountUtils.generateAccountnumber())
                .accountBalance(BigDecimal.ZERO)
                .phonenumber(req.getPhonenumber())
                .ninNumber(req.getNinNumber())
                .bvnNumber(req.getBvnNumber())
                .password(passwordEncoder.encode(req.getPassword()))
                .customerAccountType(req.getCustomerAccountType())
                .customerAccountStatus(req.getCustomerAccountStatus())
                .role(Role.ROLE_USER)
                .kycStatus("PENDING")
                .depositCount(0)
                .hasDefaultedLoan(false)
                .build();
        customer = accountCustomerRepository.save(customer);

        // Step 2 – create user in Keycloak and store the returned UUID
        try {
            // Strip ROLE_ prefix → Keycloak role name is "USER" or "ADMIN"
            String keycloakRoleName = customer.getRole().name().replace("ROLE_", "");
            String keycloakUserId = keycloakAdminService.createUser(
                    customer.getEmail(),
                    customer.getFirstName(),
                    customer.getLastName(),
                    req.getPassword(),   // plaintext — Keycloak hashes server-side
                    keycloakRoleName
            );
            customer.setKeycloakUserId(keycloakUserId);
            customer = accountCustomerRepository.save(customer);
            log.info("Keycloak user {} created for customer {}", keycloakUserId, customer.getId());
        } catch (Exception e) {
            log.error("Keycloak provisioning failed for customer {}: {}. Will retry later.",
                    customer.getId(), e.getMessage());
        }

        // Step 3 – register client in Fineract
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("firstname", customer.getFirstName());
            payload.put("lastname", customer.getLastName());
            payload.put("externalId", customer.getAccountNumber());
            payload.put("mobileNo", customer.getPhonenumber());
            payload.put("emailAddress", customer.getEmail());
            payload.put("officeId", 1);
            payload.put("active", true);
            payload.put("activationDate", LocalDate.now().toString());
            payload.put("submittedOnDate", LocalDate.now().toString());
            payload.put("locale", "en");
            payload.put("dateFormat", "yyyy-MM-dd");

            Map<String, Object> fineractResp = fineractClientService.createClient(payload);
            Long fineractClientId = Long.parseLong(fineractResp.get("clientId").toString());
            customer.setFineractClientId(fineractClientId);
            customer = accountCustomerRepository.save(customer);

            // Step 3 – provision savings/wallet account in Fineract
            walletService.provisionSavingsAccount(customer);
            log.info("Fineract client {} and savings account provisioned for customer {}",
                    fineractClientId, customer.getId());
        } catch (Exception e) {
            log.error("Fineract provisioning failed for customer {}: {}. Will retry later.",
                    customer.getId(), e.getMessage());
        }

        return BankResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_CREATION_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_CREATION_SUCCESS_MSG)
                .accountCustomerInfomation(AccountCustomerInfomation.builder()
                        .accountBalance(customer.getAccountBalance())
                        .accountNumber(customer.getAccountNumber())
                        .accountName(customer.getFirstName() + " " + customer.getLastName())
                        .build())
                .build();
    }

    public BankResponse login(LoginDto loginDto) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword()));
        return BankResponse.builder()
                .responseCode("Login Success")
                .responseMessage(jwtTokenGenerator.generateToken(auth))
                .build();
    }

    @Override
    public BankResponse balanceEnquiry(EnquiryRequest enquiryRequest) {
        boolean exists = accountCustomerRepository.existsByAccountNumber(enquiryRequest.getAccountNumber());
        if (!exists) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(enquiryRequest.getAccountNumber());
        return BankResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_FOUND_CODE)
                .responseMessage(AccountUtils.ACCOUNT_FOUND_MSG)
                .accountCustomerInfomation(AccountCustomerInfomation.builder()
                        .accountBalance(customer.getAccountBalance())
                        .accountNumber(customer.getAccountNumber())
                        .accountName(customer.getFirstName() + " " + customer.getLastName())
                        .build())
                .build();
    }

    @Override
    public String nameEnquiry(EnquiryRequest enquiryRequest) {
        boolean exists = accountCustomerRepository.existsByAccountNumber(enquiryRequest.getAccountNumber());
        if (!exists) return AccountUtils.ACCOUNT_NOT_EXISTS_MSG;
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(enquiryRequest.getAccountNumber());
        return customer.getFirstName() + " " + customer.getLastName();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankResponse creditingAnAccount(CreditDebitRequest req) {
        boolean exists = accountCustomerRepository.existsByAccountNumber(req.getAccountNumber());
        if (!exists) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(req.getAccountNumber());
        customer.setAccountBalance(customer.getAccountBalance().add(req.getAmount()));
        accountCustomerRepository.save(customer);

        TransactionDTO tx = TransactionDTO.builder()
                .accountNumber(customer.getAccountNumber())
                .transactionType("CREDIT")
                .amount(req.getAmount())
                .build();
        transactionService.saveTransaction(tx);

        return BankResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_CREDITED_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_CREDITED_SUCCESS_MSG)
                .accountCustomerInfomation(AccountCustomerInfomation.builder()
                        .accountName(customer.getFirstName() + " " + customer.getLastName())
                        .accountBalance(customer.getAccountBalance())
                        .accountNumber(req.getAccountNumber())
                        .build())
                .build();
    }

    public BankResponse debitingAnAccount(CreditDebitRequest req) {
        try {
            return debitingAnAccountTransactional(req);
        } catch (DataAccessException ex) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.DATABASE_ERROR_CODE)
                    .responseMessage(AccountUtils.DATABASE_ERROR_MSG)
                    .build();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BankResponse debitingAnAccountTransactional(CreditDebitRequest req) {
        boolean exists = accountCustomerRepository.existsByAccountNumber(req.getAccountNumber());
        if (!exists) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(req.getAccountNumber());
        BigInteger available = customer.getAccountBalance().toBigInteger();
        BigInteger debit = req.getAmount().toBigInteger();

        if (available.compareTo(debit) < 0) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_INSUFFICIENT_ERROR_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_INSUFFICIENT_ERROR_MSG)
                    .build();
        }
        customer.setAccountBalance(customer.getAccountBalance().subtract(req.getAmount()));
        accountCustomerRepository.save(customer);

        TransactionDTO tx = TransactionDTO.builder()
                .accountNumber(customer.getAccountNumber())
                .transactionType("DEBIT")
                .amount(req.getAmount())
                .build();
        transactionService.saveTransaction(tx);

        return BankResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_DEBITED_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_DEBITED_SUCCESS_MSG)
                .accountCustomerInfomation(AccountCustomerInfomation.builder()
                        .accountName(customer.getFirstName() + " " + customer.getLastName())
                        .accountNumber(customer.getAccountNumber())
                        .accountBalance(customer.getAccountBalance())
                        .build())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankResponse transfer(TransferRequest req) {
        boolean destinationExists = accountCustomerRepository.existsByAccountNumber(req.getDestinationAccountNumber());
        if (!destinationExists) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer source = accountCustomerRepository.findByAccountNumber(req.getSourceAccountNumber());
        if (req.getAmount().compareTo(source.getAccountBalance()) > 0) {
            return BankResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_INSUFFICIENT_ERROR_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_INSUFFICIENT_ERROR_MSG)
                    .build();
        }
        source.setAccountBalance(source.getAccountBalance().subtract(req.getAmount()));
        accountCustomerRepository.save(source);

        AccountCustomer dest = accountCustomerRepository.findByAccountNumber(req.getDestinationAccountNumber());
        dest.setAccountBalance(dest.getAccountBalance().add(req.getAmount()));
        accountCustomerRepository.save(dest);

        TransactionDTO tx = TransactionDTO.builder()
                .accountNumber(dest.getAccountNumber())
                .transactionType("CREDIT")
                .amount(req.getAmount())
                .build();
        transactionService.saveTransaction(tx);

        return BankResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_TRANSFER_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_TRANSFER_SUCCESS_MSG)
                .accountCustomerInfomation(AccountCustomerInfomation.builder()
                        .accountNumber(req.getDestinationAccountNumber())
                        .build())
                .build();
    }

    @Override
    public AccountTypeResponse updateAccountType(AccountTypeRequest req) {
        if (!accountCustomerRepository.existsByAccountNumber(req.getAccountNumber())) {
            return AccountTypeResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(req.getAccountNumber());
        customer.setCustomerAccountType(CustomerAccountType.valueOf(req.getAccountType()));
        accountCustomerRepository.save(customer);
        return AccountTypeResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_UPDATED_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_UPDATED_SUCCESS_MSG)
                .build();
    }

    @Override
    public AccountStatusResponse updateAccountStatus(AccountStatusRequest req) {
        if (!accountCustomerRepository.existsByAccountNumber(req.getAccountNumber())) {
            return AccountStatusResponse.builder()
                    .responseCode(AccountUtils.ACCOUNT_NOT_EXISTS_CODE)
                    .responseMessage(AccountUtils.ACCOUNT_NOT_EXISTS_MSG)
                    .build();
        }
        AccountCustomer customer = accountCustomerRepository.findByAccountNumber(req.getAccountNumber());
        customer.setCustomerAccountStatus(CustomerAccountStatus.valueOf(req.getAccountStatus()));
        accountCustomerRepository.save(customer);
        return AccountStatusResponse.builder()
                .responseCode(AccountUtils.ACCOUNT_UPDATED_SUCCESS_CODE)
                .responseMessage(AccountUtils.ACCOUNT_UPDATED_SUCCESS_MSG)
                .build();
    }

    @Override
    public AccountCustomer getAccountCustomerByAccountNumber(String accountNumber) {
        return accountCustomerRepository.findByAccountNumber(accountNumber);
    }

    @Override
    public void deleteAccountCustomerByAccountNumber(String accountNumber) {
        log.info("Closing account: {}", accountNumber);
        accountCustomerRepository.deleteAccountCustomerByAccountNumber(accountNumber);
    }
}
