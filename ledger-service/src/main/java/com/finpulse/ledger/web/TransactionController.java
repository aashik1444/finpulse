package com.finpulse.ledger.web;

import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.domain.LedgerTransaction;
import com.finpulse.ledger.repository.LedgerEntryRepository;
import com.finpulse.ledger.repository.LedgerTransactionRepository;
import com.finpulse.ledger.service.TransactionNotFoundException;
import com.finpulse.ledger.web.dto.TransactionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @GetMapping("/api/transactions/{id}")
    public TransactionDetailResponse getTransaction(@PathVariable UUID id) {
        LedgerTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        List<LedgerEntry> entries = entryRepository.findByTransactionId(id);
        return LedgerMapper.toDetailResponse(transaction, entries);
    }
}
