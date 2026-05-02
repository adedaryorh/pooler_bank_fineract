package com.poolerapp.pooler_bank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountStatusRequest {
    private String accountNumber;
    private String accountStatus;
    private String accountType;
    private String email;
}
