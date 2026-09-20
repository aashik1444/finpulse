package com.finpulse.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balanceMinor;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Account(String ownerName, String currency, long openingBalanceMinor) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.balanceMinor = openingBalanceMinor;
        this.createdAt = Instant.now();
    }

    public void debit(long amountMinor) {
        this.balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        this.balanceMinor += amountMinor;
    }
}
