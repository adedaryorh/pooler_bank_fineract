package com.poolerapp.pooler_bank.service;

import com.poolerapp.pooler_bank.dto.TransactionDTO;
import com.poolerapp.pooler_bank.model.Transactions;
import com.poolerapp.pooler_bank.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.logging.Logger;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService{

    private static final Logger logger = Logger.getLogger(TransactionServiceImpl.class.getName());

    @Autowired
    TransactionRepository transactionRepository;

    @Override
    public void saveTransaction(TransactionDTO transactionDTO) {
        Transactions transactions = Transactions.builder()
                .transactionType(transactionDTO.getTransactionType())
                .accountNumber(transactionDTO.getAccountNumber())
                .amount(transactionDTO.getAmount())
                .status("SUCCESS")
                .build();
                transactionRepository.save(transactions);
                logger.info("Transaction saved Successfully");
    }



}
