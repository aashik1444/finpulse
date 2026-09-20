package com.finpulse.ledger.service;

public class TransferRetriesExhaustedException extends RuntimeException {
    public TransferRetriesExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
