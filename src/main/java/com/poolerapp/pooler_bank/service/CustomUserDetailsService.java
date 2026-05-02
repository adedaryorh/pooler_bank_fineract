package com.poolerapp.pooler_bank.service;

import com.poolerapp.pooler_bank.repository.AccountCustomerRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private AccountCustomerRepository accountCustomerRepository;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return accountCustomerRepository.findByEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                username +" "+ "not found")
                );
    }
}
