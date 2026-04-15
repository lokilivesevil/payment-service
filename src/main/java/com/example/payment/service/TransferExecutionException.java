package com.example.payment.service;

public class TransferExecutionException extends RuntimeException {
    public TransferExecutionException(String message) {
        super(message);
    }
}
