package com.poolerapp.pooler_bank.service;

import com.poolerapp.pooler_bank.dto.*;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AccountCustomerService {

    BankResponse login(LoginDto loginDto);

    BankResponse creatAnAnccount(AccountCustomerRequest accountCustomerRequest);

    BankResponse balanceEnquiry(EnquiryRequest enquiryRequest);

    String nameEnquiry(EnquiryRequest enquiryRequest);

    BankResponse creditingAnAccount(CreditDebitRequest creditDebitRequest);
    BankResponse debitingAnAccount(CreditDebitRequest creditDebitRequest);

    BankResponse transfer(TransferRequest transferRequest);

    AccountTypeResponse updateAccountType(AccountTypeRequest accountTypeRequest);

    AccountStatusResponse updateAccountStatus(AccountStatusRequest accountStatusRequest);
    AccountCustomer getAccountCustomerByAccountNumber(String accountNumber);

    //List<AccountCustomer> getAllActiveAccounts();
    void deleteAccountCustomerByAccountNumber(String accountNumber);


}
