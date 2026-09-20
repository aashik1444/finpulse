package com.finpulse.ledger.web;

import com.finpulse.ledger.service.AccountNotFoundException;
import com.finpulse.ledger.service.CurrencyMismatchException;
import com.finpulse.ledger.service.InsufficientFundsException;
import com.finpulse.ledger.service.TransactionNotFoundException;
import com.finpulse.ledger.service.TransferRetriesExhaustedException;
import com.finpulse.ledger.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * One place where every exception becomes an HTTP response, so no controller
 * needs a try/catch and every error body has the same shape regardless of
 * which endpoint or failure mode produced it.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    // 422, not 400. The request was well formed and the accounts exist; it simply
    // cannot be fulfilled given the current state of the system. 400 is reserved
    // for requests that are wrong on their face.
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", ex.getMessage(), request);
    }

    // 409 Conflict: the request was valid and may well succeed if sent again. This is
    // the honest answer when contention on one account was heavy enough that the
    // bounded retry gave up. 503 would wrongly imply the service is down; 500 would
    // wrongly imply a bug. 409 tells the caller it is safe to retry, and because the
    // request carries an idempotency key, retrying is in fact safe.
    @ExceptionHandler(TransferRetriesExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleRetriesExhausted(
            TransferRetriesExhaustedException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", ex.getMessage(), request);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(
            CurrencyMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    // Without this, omitting the Idempotency-Key header surfaces as a generic 500,
    // which tells the caller nothing about what they got wrong.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header is missing: " + ex.getHeaderName(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code, message, Instant.now(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
