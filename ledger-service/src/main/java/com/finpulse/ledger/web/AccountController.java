package com.finpulse.ledger.web;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.service.AccountService;
import com.finpulse.ledger.web.dto.AccountResponse;
import com.finpulse.ledger.web.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(
                request.ownerName(), request.currency(), request.openingBalanceMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(LedgerMapper.toResponse(account));
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable UUID id) {
        Account account = accountService.getAccount(id);
        return LedgerMapper.toResponse(account);
    }
}
