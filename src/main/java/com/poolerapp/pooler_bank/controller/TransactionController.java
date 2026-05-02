package com.poolerapp.pooler_bank.controller;

import com.poolerapp.pooler_bank.model.Transactions;
import com.poolerapp.pooler_bank.repository.TransactionRepository;
import com.poolerapp.pooler_bank.service.AccountCustomerTransactionStatement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/aacountTransactionsStatement")
public class TransactionController {

    private AccountCustomerTransactionStatement accountCustomerTransactionStatement;
    @GetMapping
    public List<Transactions> generateBankStatement(@RequestParam String accountNumber,
                                                    @RequestParam String startDate,
                                                    @RequestParam String endDate)
    {
        return accountCustomerTransactionStatement.generateStatement(accountNumber, startDate, endDate);

    }

}
