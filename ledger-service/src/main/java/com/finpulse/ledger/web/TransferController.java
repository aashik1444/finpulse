package com.finpulse.ledger.web;

import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.repository.LedgerEntryRepository;
import com.finpulse.ledger.service.TransferExecutor;
import com.finpulse.ledger.service.TransferResult;
import com.finpulse.ledger.web.dto.LedgerEntryResponse;
import com.finpulse.ledger.web.dto.TransferRequest;
import com.finpulse.ledger.web.dto.TransferResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransferController {

    // Depends on TransferExecutor, not TransferService directly, so that every real
    // request gets the optimistic-lock retry, not just the concurrency test.
    private final TransferExecutor transferExecutor;
    private final LedgerEntryRepository entryRepository;

    // The idempotency key travels as a header, not a body field, following the
    // convention Stripe and most payment APIs use. It describes retry semantics,
    // which belong to the transport, not to what the transfer actually is.
    @PostMapping("/api/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransferResult result = transferExecutor.executeWithRetry(
                request.fromAccountId(), request.toAccountId(), request.amountMinor(), idempotencyKey);

        TransferResponse body = new TransferResponse(
                result.transaction().getId(),
                result.transaction().getStatus(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amountMinor(),
                result.transaction().getCreatedAt());

        // 201 when this call created the transfer, 200 when it merely replayed one
        // that already existed. The client can tell the difference, which matters
        // when reconciling what a retry actually did.
        HttpStatus status = result.alreadyProcessed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/api/accounts/{id}/entries")
    public Page<LedgerEntryResponse> getEntries(@PathVariable UUID id, Pageable pageable) {
        Page<LedgerEntry> entries = entryRepository.findByAccountId(id, pageable);
        return entries.map(LedgerMapper::toResponse);
    }
}
