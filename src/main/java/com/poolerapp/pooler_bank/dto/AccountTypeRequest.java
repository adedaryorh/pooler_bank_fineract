package com.poolerapp.pooler_bank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountTypeRequest {
    private String accountNumber;
    private String accountType;
    private String email;
    private String bvnNumber;
}
