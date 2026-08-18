package com.xianzhi.fridge.shared.config;

import com.xianzhi.fridge.shared.application.OutboxProcessor;
import com.xianzhi.fridge.telemetry.application.EnvironmentAggregationService;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class WorkerJobs {
    private final OutboxProcessor outbox; private final EnvironmentAggregationService environment;
    private final SensorReadingStore readings; private final Clock clock;
    public WorkerJobs(OutboxProcessor outbox, EnvironmentAggregationService environment, SensorReadingStore readings, Clock clock) {
        this.outbox = outbox; this.environment = environment; this.readings = readings; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${app.telemetry.outbox-delay:PT2S}", initialDelayString = "PT2S")
    @SchedulerLock(name = "outbox-consumer", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void outbox() { outbox.processBatch(); }
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "environment-aggregation", lockAtMostFor = "PT4M")
    public void environment() { environment.aggregateAll(); }
    @Scheduled(cron = "30 */10 * * * *")
    @SchedulerLock(name = "sensor-hourly-aggregation", lockAtMostFor = "PT5M")
    public void hourly() { Instant to = clock.instant().truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS); readings.aggregateHour(to.minus(2, ChronoUnit.HOURS), to); }
    @Scheduled(cron = "0 20 3 * * *")
    @SchedulerLock(name = "telemetry-retention-partitions", lockAtMostFor = "PT30M")
    public void retention() { readings.maintainRetentionAndPartitions(clock.instant()); }
}
