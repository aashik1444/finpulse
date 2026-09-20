package com.finpulse.audit.web;

import com.finpulse.audit.domain.AuditRecord;
import com.finpulse.audit.service.AuditRecordService;
import com.finpulse.audit.web.dto.AuditRecordResponse;
import com.finpulse.audit.web.dto.IncomingAuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditRecordService auditRecordService;

    /**
     * Ingest endpoint for the outbox poller.
     *
     * <p>202 Accepted rather than 200 or 201. The event is durably recorded, but
     * nothing was created that the caller can now go and fetch, which is what 201
     * implies, and the caller is not asking for a representation back, which is what
     * 200 suggests. 202 says: received, stored, we are done here.
     *
     * <p>It returns 202 for a duplicate too. From the producer's side the meaningful
     * question is only whether this event is safely recorded, and it is. Returning an
     * error would make the poller retry an event that is already stored, forever.
     */
    @PostMapping("/api/audit/events")
    public ResponseEntity<Void> receiveEvent(@RequestBody IncomingAuditEvent event) {
        auditRecordService.recordEvent(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/api/audit")
    public Page<AuditRecordResponse> getAudit(@RequestParam UUID accountId, Pageable pageable) {
        Page<AuditRecord> records = auditRecordService.findByAccount(accountId, pageable);
        return records.map(r -> new AuditRecordResponse(
                r.getId(), r.getEventId(), r.getAggregateId(), r.getEventType(),
                r.getPayload(), r.getSourceCreatedAt(), r.getReceivedAt()));
    }
}
