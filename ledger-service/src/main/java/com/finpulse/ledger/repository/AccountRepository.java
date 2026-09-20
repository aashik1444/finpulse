package com.finpulse.ledger.repository;

import com.finpulse.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Loads an account and takes a row level write lock on it, emitting
     * {@code SELECT ... FOR UPDATE}.
     *
     * <p>A second transaction calling this for the same account blocks at the SELECT
     * until the first commits or rolls back, rather than racing ahead and losing at
     * commit time. Conflicts become waits instead of failures, so no retry is needed.
     *
     * <p>This is the alternative to the default optimistic path, kept alongside it so
     * the two can be measured against the same load. See {@code TransferService
     * .transferPessimistic}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
