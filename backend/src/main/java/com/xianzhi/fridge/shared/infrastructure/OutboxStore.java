package com.xianzhi.fridge.shared.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public OutboxStore(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
    }
    public List<ClaimedEvent> claim(int limit, Instant now) {
        return transactions.execute(status -> {
            List<ClaimedEvent> claimed = jdbc.query("""
                    SELECT BIN_TO_UUID(id) id, event_type, payload_json, attempts
                    FROM outbox_event
                    WHERE (status='PENDING' AND available_at<=?)
                       OR (status='PROCESSING' AND locked_at<?)
                    ORDER BY available_at, created_at
                    LIMIT ? FOR UPDATE SKIP LOCKED
                    """, (rs, row) -> new ClaimedEvent(UUID.fromString(rs.getString("id")), rs.getString("event_type"), rs.getString("payload_json"), rs.getInt("attempts") + 1),
                    Timestamp.from(now), Timestamp.from(now.minus(java.time.Duration.ofMinutes(5))), limit);
            for (ClaimedEvent event : claimed) jdbc.update("UPDATE outbox_event SET status='PROCESSING', attempts=?, locked_at=? WHERE id=UUID_TO_BIN(?)", event.attempt(), Timestamp.from(now), event.id().toString());
            return claimed;
        });
    }
    public void complete(UUID id, Instant now) { jdbc.update("UPDATE outbox_event SET status='COMPLETED', processed_at=?, locked_at=NULL, last_error=NULL WHERE id=UUID_TO_BIN(?)", Timestamp.from(now), id.toString()); }
    public void fail(ClaimedEvent event, Instant now, Throwable failure) {
        boolean terminal = event.attempt() >= 5; long backoff = Math.min(3600, 30L * (1L << Math.min(event.attempt() - 1, 10)));
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (message.length() > 1000) message = message.substring(0, 1000);
        jdbc.update("UPDATE outbox_event SET status=?, available_at=?, locked_at=NULL, last_error=? WHERE id=UUID_TO_BIN(?)",
                terminal ? "FAILED" : "PENDING", Timestamp.from(now.plusSeconds(backoff)), message, event.id().toString());
    }
    public record ClaimedEvent(UUID id, String eventType, String payload, int attempt) { }
}
