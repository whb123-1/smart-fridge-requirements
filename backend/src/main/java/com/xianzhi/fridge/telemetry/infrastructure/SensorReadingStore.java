package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import com.xianzhi.fridge.telemetry.domain.ReadingSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SensorReadingStore {
    private final JdbcTemplate jdbc;
    public SensorReadingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID id, UUID userId, UUID fridgeId, UUID zoneId, UUID deviceId, UUID sensorId,
                       UUID messageId, SensorMetric metric, BigDecimal value, String unit,
                       ReadingQuality quality, ReadingSource source, Instant observedAt, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO sensor_reading
                  (id,user_id,fridge_id,zone_id,device_id,sensor_id,telemetry_message_id,metric,value,unit,quality,source,observed_at,received_at)
                VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,?,?,?)
                """, id.toString(), userId.toString(), fridgeId.toString(), zoneId.toString(), deviceId.toString(), sensorId.toString(),
                messageId.toString(), metric.name(), value, unit, quality.name(), source.name(), Timestamp.from(observedAt), Timestamp.from(receivedAt));
    }

    public List<ReadingRow> readings(UUID userId, UUID zoneId, SensorMetric metric, Instant from, Instant to, int limit) {
        String metricFilter = metric == null ? "" : " AND metric = ?";
        Object[] args = metric == null
                ? new Object[]{userId.toString(), zoneId.toString(), Timestamp.from(from), Timestamp.from(to), limit}
                : new Object[]{userId.toString(), zoneId.toString(), Timestamp.from(from), Timestamp.from(to), metric.name(), limit};
        return jdbc.query("""
                SELECT BIN_TO_UUID(id) id, BIN_TO_UUID(sensor_id) sensor_id, metric, value, unit, quality, source, observed_at, received_at
                FROM sensor_reading
                WHERE user_id=UUID_TO_BIN(?) AND zone_id=UUID_TO_BIN(?) AND observed_at>=? AND observed_at<=?
                """ + metricFilter + " ORDER BY observed_at ASC LIMIT ?", (rs, row) -> new ReadingRow(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("sensor_id")), SensorMetric.valueOf(rs.getString("metric")),
                rs.getBigDecimal("value"), rs.getString("unit"), ReadingQuality.valueOf(rs.getString("quality")),
                ReadingSource.valueOf(rs.getString("source")), rs.getTimestamp("observed_at").toInstant(), rs.getTimestamp("received_at").toInstant()), args);
    }

    public List<AggregateRow> latestGoodByZone(UUID zoneId, Instant cutoff) {
        return jdbc.query("""
                SELECT BIN_TO_UUID(ranked.sensor_id) sensor_id, ranked.metric, ranked.value,
                       ranked.observed_at, ranked.received_at
                FROM (
                  SELECT sensor_id, metric, value, observed_at, received_at,
                         ROW_NUMBER() OVER (
                           PARTITION BY sensor_id
                           ORDER BY observed_at DESC, received_at DESC, id DESC
                         ) reading_rank
                  FROM sensor_reading
                  WHERE zone_id=UUID_TO_BIN(?) AND quality='GOOD' AND observed_at>=?
                ) ranked
                WHERE ranked.reading_rank=1
                """, (rs, row) -> new AggregateRow(UUID.fromString(rs.getString("sensor_id")), SensorMetric.valueOf(rs.getString("metric")),
                rs.getBigDecimal("value"), rs.getTimestamp("observed_at").toInstant(), rs.getTimestamp("received_at").toInstant()),
                zoneId.toString(), Timestamp.from(cutoff));
    }

    public void aggregateHour(Instant from, Instant to) {
        jdbc.update("""
                INSERT INTO sensor_reading_hourly
                  (sensor_id,user_id,fridge_id,zone_id,metric,hour_start,min_value,max_value,avg_value,sample_count,updated_at)
                SELECT sensor_id,user_id,fridge_id,zone_id,metric,
                       TIMESTAMP(DATE_FORMAT(observed_at,'%Y-%m-%d %H:00:00')),
                       MIN(value),MAX(value),AVG(value),COUNT(*),UTC_TIMESTAMP(3)
                FROM sensor_reading
                WHERE observed_at>=? AND observed_at<? AND quality='GOOD'
                GROUP BY sensor_id,user_id,fridge_id,zone_id,metric,TIMESTAMP(DATE_FORMAT(observed_at,'%Y-%m-%d %H:00:00'))
                ON DUPLICATE KEY UPDATE min_value=VALUES(min_value),max_value=VALUES(max_value),avg_value=VALUES(avg_value),
                                        sample_count=VALUES(sample_count),updated_at=VALUES(updated_at)
                """, Timestamp.from(from), Timestamp.from(to));
    }

    public void maintainRetentionAndPartitions(Instant now) {
        jdbc.update("DELETE FROM sensor_reading WHERE observed_at < ?", Timestamp.from(now.minus(java.time.Duration.ofDays(90))));
        jdbc.update("DELETE FROM sensor_reading_hourly WHERE hour_start < ?", Timestamp.from(now.minus(java.time.Duration.ofDays(730))));
        jdbc.update("DELETE FROM telemetry_message WHERE received_at < ?", Timestamp.from(now.minus(java.time.Duration.ofDays(7))));
        Set<String> existing = new HashSet<>(jdbc.queryForList("""
                SELECT PARTITION_NAME FROM information_schema.PARTITIONS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sensor_reading' AND PARTITION_NAME IS NOT NULL
                """, String.class));
        YearMonth current = YearMonth.from(now.atZone(ZoneOffset.UTC));
        YearMonth latest = current.minusMonths(1);
        for (String name : existing) {
            if (name != null && name.matches("p\\d{6}")) {
                YearMonth candidate = YearMonth.parse(name.substring(1), DateTimeFormatter.ofPattern("yyyyMM"));
                if (candidate.isAfter(latest)) latest = candidate;
            }
        }
        YearMonth target = current.plusMonths(3);
        while (latest.isBefore(target)) {
            YearMonth next = latest.plusMonths(1); String partition = "p" + next.format(DateTimeFormatter.ofPattern("yyyyMM"));
            String boundary = next.plusMonths(1).atDay(1).toString();
            jdbc.execute("ALTER TABLE sensor_reading REORGANIZE PARTITION pmax INTO (PARTITION " + partition
                    + " VALUES LESS THAN ('" + boundary + "'), PARTITION pmax VALUES LESS THAN (MAXVALUE))");
            latest = next;
        }
    }

    public record ReadingRow(UUID id, UUID sensorId, SensorMetric metric, BigDecimal value, String unit,
                             ReadingQuality quality, ReadingSource source, Instant observedAt, Instant receivedAt) { }
    public record AggregateRow(UUID sensorId, SensorMetric metric, BigDecimal value, Instant observedAt, Instant receivedAt) { }
}
