package com.xianzhi.fridge.shared.infrastructure;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class OutboxStoreTest {
    @Test
    void fourthFailureUsesExponentialBackoff() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OutboxStore store = new OutboxStore(jdbc, mock(PlatformTransactionManager.class));
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-18T06:00:00Z");

        store.fail(new OutboxStore.ClaimedEvent(id, "TestEvent", "{}", 4), now,
                new IllegalStateException("temporary failure"));

        verify(jdbc).update(anyString(), eq("PENDING"), eq(Timestamp.from(now.plusSeconds(240))),
                eq("temporary failure"), eq(id.toString()));
    }

    @Test
    void fifthFailureMovesEventToFailedState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OutboxStore store = new OutboxStore(jdbc, mock(PlatformTransactionManager.class));
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-18T06:00:00Z");

        store.fail(new OutboxStore.ClaimedEvent(id, "TestEvent", "{}", 5), now,
                new IllegalStateException("terminal failure"));

        verify(jdbc).update(anyString(), eq("FAILED"), eq(Timestamp.from(now.plusSeconds(480))),
                eq("terminal failure"), eq(id.toString()));
    }
}
