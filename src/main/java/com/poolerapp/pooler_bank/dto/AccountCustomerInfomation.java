package com.poolerapp.pooler_bank.dto;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountCustomerInfomation {
    private String accountName;
    private String accountNumber;
    private BigDecimal accountBalance;

}
