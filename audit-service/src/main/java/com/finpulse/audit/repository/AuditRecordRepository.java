package com.finpulse.audit.repository;

import com.finpulse.audit.domain.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<AuditRecord> findByAggregateId(UUID aggregateId, Pageable pageable);
}
