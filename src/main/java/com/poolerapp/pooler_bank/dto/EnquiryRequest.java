package com.poolerapp.pooler_bank.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryRequest {
    private String accountNumber;
}
