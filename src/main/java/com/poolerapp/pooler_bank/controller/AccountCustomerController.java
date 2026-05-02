package com.poolerapp.pooler_bank.controller;

import com.poolerapp.pooler_bank.dto.*;
import com.poolerapp.pooler_bank.model.AccountCustomer;
import com.poolerapp.pooler_bank.service.AccountCustomerService;
import jakarta.validation.Valid;
import lombok.Value;
import org.antlr.v4.runtime.misc.EqualityComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/poolerapp/v1")
public class AccountCustomerController {
    @Autowired
    AccountCustomerService accountCustomerService;


    @PostMapping("/login")
    public BankResponse login(@RequestBody LoginDto loginDto)
    {
        return accountCustomerService.login(loginDto);
    }

    @GetMapping("/accountCustomerByAccountNumber")
    public AccountCustomer getAccountCustomerByAccountNumber(@RequestBody EnquiryRequest enquiryRequest)
    {
        return accountCustomerService.getAccountCustomerByAccountNumber(enquiryRequest.getAccountNumber());
    }

     @DeleteMapping(path = "deleteAccountCustomer/{accountNumber}")
     void deleteAccount(@PathVariable("accountNumber") String accountNumber)
     {
        accountCustomerService.deleteAccountCustomerByAccountNumber(accountNumber);
     }


    @PostMapping("/createAccount")
    @ResponseStatus(HttpStatus.CREATED)
    public BankResponse createCustomerAccount(@Valid @RequestBody AccountCustomerRequest accountCustomerRequest)
    {
        return accountCustomerService.creatAnAnccount(accountCustomerRequest);
    }
    @GetMapping("/balanceEnquiry")
    public BankResponse balanceEnquiry(@RequestBody EnquiryRequest enquiryRequest){
        return accountCustomerService.balanceEnquiry(enquiryRequest);
    }
    @GetMapping("/nameEnquiry")
    public String nameEnquiry(@RequestBody EnquiryRequest enquiryRequest){
        return accountCustomerService.nameEnquiry(enquiryRequest);
    }

    @PostMapping("/credit")
    public BankResponse creditCustomerAccount(@RequestBody CreditDebitRequest creditDebitRequest){
        return accountCustomerService.creditingAnAccount(creditDebitRequest);
    }
    @PostMapping("/debit")
    public BankResponse debitCustomerAccount(@RequestBody CreditDebitRequest creditDebitRequest){
        return accountCustomerService.debitingAnAccount(creditDebitRequest);
    }
    @PostMapping("/transfer")
    public BankResponse transfer(@RequestBody TransferRequest transferRequest){
        return accountCustomerService.transfer(transferRequest);
    }

    @PutMapping("/update_account_type")
    public AccountTypeResponse updateAccountType(@RequestBody AccountTypeRequest accountTypeRequest){
        return accountCustomerService.updateAccountType(accountTypeRequest);
    }

    @PostMapping("/update_account_status")
    public AccountStatusResponse updateAccountStatus(@RequestBody AccountStatusRequest accountStatusRequest){
        return accountCustomerService.updateAccountStatus(accountStatusRequest);
    }

    /*
    @GetMapping("/accountCustomers")
    public List<AccountCustomer> getAllAccountsOnActive(){
        return accountCustomerService.getAllActiveAccounts();

    }
     */

}
