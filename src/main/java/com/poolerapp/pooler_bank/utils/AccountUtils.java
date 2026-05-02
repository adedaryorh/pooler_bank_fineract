package com.poolerapp.pooler_bank.utils;

import java.time.Year;

public class AccountUtils {

    public static final String ACCOUNT_EXISTS_CODE = "001";
    public static final String DESTINATION_ACCOUNT_NOT_EXISTS_CODE = "008";
    public static final String DESTINATION_ACCOUNT_NOT_EXISTS_MSG = "This account is ";
    public static final String ACCOUNT_CREATION_SUCCESS_CODE = "002";
    public static final String ACCOUNT_UPDATED_SUCCESS_CODE = "009";
    public static final String ACCOUNT_UPDATED_SUCCESS_MSG = "This user type/status has been updated ";
    public static final String ACCOUNT_NOT_EXISTS_CODE = "003";
    public static final String ACCOUNT_FOUND_CODE = "004";
    public static final String ACCOUNT_CREDITED_SUCCESS_CODE = "005";
    public static final String ACCOUNT_INSUFFICIENT_ERROR_CODE = "006";
    public static final String ACCOUNT_DEBITED_SUCCESS_CODE = "007";
    public static final String ACCOUNT_TRANSFER_SUCCESS_CODE = "008";
    public static final String DATABASE_ERROR_CODE = "300";
    public static final String DATABASE_ERROR_MSG = "There's an exception on the db level";
    public static final String ACCOUNT_INSUFFICIENT_ERROR_MSG = "The initial acct has an insufficient balance";
    public static final String ACCOUNT_EXISTS_MSG = "This user already has an account";
    public static final String ACCOUNT_TRANSFER_SUCCESS_MSG = "This transfer is a success";
    public static final String ACCOUNT_DEBITED_SUCCESS_MSG = "A debit occurs succcessfully";
    public static final String ACCOUNT_FOUND_MSG = "This account is found ";
    public static final String ACCOUNT_CREDITED_SUCCESS_MSG = "This account has been credited the value ";
    public static final String ACCOUNT_NOT_EXISTS_MSG = "The user with account number not exist";
    public static final String ACCOUNT_CREATION_SUCCESS_MSG = "This new customer has been created successfully ";

    public static String generateAccountnumber(){
        Year currentYear = Year.now();
        int min = 100000;
        int max = 999999;
        int randNumber = (int) (Math.floor(Math.random()* (max - min + 1) + min));
        String newCurrentYear = String.valueOf(currentYear);
        String randomNumber = String.valueOf(randNumber);
        StringBuilder accountNUmber = new StringBuilder();
        return accountNUmber.append(newCurrentYear).append(randomNumber).toString();
    }


    //https://tools.keycdn.com/sha256-online-generator

}
