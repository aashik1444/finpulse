package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.domain.LedgerTransaction;

import java.util.List;

public record TransferResult(LedgerTransaction transaction, List<LedgerEntry> entries, boolean alreadyProcessed) {
}
