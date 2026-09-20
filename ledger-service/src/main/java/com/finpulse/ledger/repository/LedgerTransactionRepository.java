package com.finpulse.ledger.repository;

import com.finpulse.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);
}
