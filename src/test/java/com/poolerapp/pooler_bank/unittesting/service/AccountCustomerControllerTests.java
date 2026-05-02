package com.poolerapp.pooler_bank.unittesting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poolerapp.pooler_bank.service.AccountCustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
public class AccountCustomerControllerTests {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccountCustomerService accountCustomerService;

    @Autowired
    private ObjectMapper objectMapper;
    @Test
    public void givenAccountCustomerObject_whenCreatAccountCustomer_thenReturnBankResponse(){

    }
}
