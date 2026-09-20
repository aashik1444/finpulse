package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.domain.EntryDirection;
import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.domain.LedgerTransaction;
import com.finpulse.ledger.domain.TransactionStatus;
import com.finpulse.ledger.repository.AccountRepository;
import com.finpulse.ledger.repository.LedgerEntryRepository;
import com.finpulse.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    /**
     * Moves money between two accounts, producing exactly one DEBIT and one CREDIT
     * entry of equal amount. Every step runs inside one transaction: either all of
     * it happens or none of it does. A half-applied transfer, a debit with no
     * matching credit, is the one outcome a ledger must never produce.
     */
    @Transactional
    public TransferResult transfer(UUID fromId, UUID toId, long amountMinor, String idempotencyKey) {

        // Idempotency is checked before validation, not after. If a client is retrying
        // because it never saw the first response, the correct answer is the original
        // result, handed back without touching balances again. Validating first would
        // mean a retry could fail on, say, insufficient funds because the balance moved
        // for an unrelated reason since, which is a wrong answer to a request that
        // already succeeded.
        Optional<LedgerTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            LedgerTransaction tx = existing.get();
            List<LedgerEntry> entries = entryRepository.findByTransactionId(tx.getId());
            return new TransferResult(tx, entries, true);
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must differ");
        }

        Account from = accountRepository.findById(fromId)
                .orElseThrow(() -> new AccountNotFoundException(fromId));
        Account to = accountRepository.findById(toId)
                .orElseThrow(() -> new AccountNotFoundException(toId));

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Cannot transfer between accounts of different currencies: "
                            + from.getCurrency() + " and " + to.getCurrency());
        }

        if (from.getBalanceMinor() < amountMinor) {
            throw new InsufficientFundsException(
                    "Account " + fromId + " has insufficient funds for transfer of " + amountMinor);
        }

        LedgerTransaction transaction = new LedgerTransaction(
                idempotencyKey, "Transfer " + amountMinor + " from " + fromId + " to " + toId,
                TransactionStatus.POSTED);
        transactionRepository.save(transaction);

        // The two entries are the double entry invariant made concrete: same amount,
        // opposite directions, same parent transaction. Their sum is zero by construction.
        LedgerEntry debitEntry = new LedgerEntry(transaction, from, EntryDirection.DEBIT, amountMinor);
        LedgerEntry creditEntry = new LedgerEntry(transaction, to, EntryDirection.CREDIT, amountMinor);
        entryRepository.save(debitEntry);
        entryRepository.save(creditEntry);

        from.debit(amountMinor);
        to.credit(amountMinor);
        accountRepository.save(from);
        accountRepository.save(to);

        return new TransferResult(transaction, List.of(debitEntry, creditEntry), false);
    }

    /**
     * The pessimistic alternative to {@link #transfer}, kept so the two strategies can
     * be measured against the same concurrency test rather than compared in the abstract.
     *
     * <p>Identical to transfer() except that both accounts are loaded with
     * {@code SELECT ... FOR UPDATE}. A competing transaction blocks at that SELECT until
     * this one finishes, so there is no optimistic failure and no retry wrapper: conflicts
     * become waits instead of exceptions.
     *
     * <p>The tradeoff is real in both directions. Under low contention this is strictly
     * worse, because every transfer pays for a lock nobody was competing for. Under heavy
     * contention on one account it is strictly better, because optimistic retry degenerates
     * into wasted work: with N threads on one row only one can win per round, so the rest
     * burn attempts and give up. Measured numbers for both are in the learning journal.
     */
    @Transactional
    public TransferResult transferPessimistic(UUID fromId, UUID toId, long amountMinor,
                                              String idempotencyKey) {

        Optional<LedgerTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            LedgerTransaction tx = existing.get();
            List<LedgerEntry> entries = entryRepository.findByTransactionId(tx.getId());
            return new TransferResult(tx, entries, true);
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must differ");
        }

        // Lock in a globally consistent order, smaller UUID first, regardless of transfer
        // direction. Without this, a transfer A->B locking A then waiting for B, racing a
        // transfer B->A locking B then waiting for A, deadlocks: each holds what the other
        // needs. Postgres detects it and kills one, but the right fix is to make the cycle
        // impossible rather than to handle it after the fact. Every transaction touching
        // both rows asks for them in the same order, so no cycle can form.
        boolean fromIsFirst = fromId.compareTo(toId) < 0;
        UUID firstLock = fromIsFirst ? fromId : toId;
        UUID secondLock = fromIsFirst ? toId : fromId;

        Account first = accountRepository.findByIdForUpdate(firstLock)
                .orElseThrow(() -> new AccountNotFoundException(firstLock));
        Account second = accountRepository.findByIdForUpdate(secondLock)
                .orElseThrow(() -> new AccountNotFoundException(secondLock));

        Account from = fromIsFirst ? first : second;
        Account to = fromIsFirst ? second : first;

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Cannot transfer between accounts of different currencies: "
                            + from.getCurrency() + " and " + to.getCurrency());
        }

        if (from.getBalanceMinor() < amountMinor) {
            throw new InsufficientFundsException(
                    "Account " + fromId + " has insufficient funds for transfer of " + amountMinor);
        }

        LedgerTransaction transaction = new LedgerTransaction(
                idempotencyKey, "Transfer " + amountMinor + " from " + fromId + " to " + toId,
                TransactionStatus.POSTED);
        transactionRepository.save(transaction);

        LedgerEntry debitEntry = new LedgerEntry(transaction, from, EntryDirection.DEBIT, amountMinor);
        LedgerEntry creditEntry = new LedgerEntry(transaction, to, EntryDirection.CREDIT, amountMinor);
        entryRepository.save(debitEntry);
        entryRepository.save(creditEntry);

        from.debit(amountMinor);
        to.credit(amountMinor);
        accountRepository.save(from);
        accountRepository.save(to);

        return new TransferResult(transaction, List.of(debitEntry, creditEntry), false);
    }
}
