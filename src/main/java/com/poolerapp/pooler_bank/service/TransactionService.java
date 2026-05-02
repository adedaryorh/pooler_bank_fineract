package com.poolerapp.pooler_bank.service;


import com.poolerapp.pooler_bank.dto.TransactionDTO;
import com.poolerapp.pooler_bank.model.Transactions;
import org.springframework.stereotype.Service;

@Service
public interface TransactionService {

    void saveTransaction(TransactionDTO transactionDTO);


}
