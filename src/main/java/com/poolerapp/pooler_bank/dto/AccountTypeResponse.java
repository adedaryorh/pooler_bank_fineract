package com.poolerapp.pooler_bank.dto;

import com.poolerapp.pooler_bank.enums.CustomerAccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountTypeResponse {
    private String responseCode;
    private String responseMessage;
}
