package com.xianzhi.fridge.shared.config;

import com.xianzhi.fridge.shared.application.OutboxProcessor;
import com.xianzhi.fridge.telemetry.application.EnvironmentAggregationService;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import com.xianzhi.fridge.speech.application.VoiceDraftService;
import com.xianzhi.fridge.notification.application.InventoryNotificationScanner;
import com.xianzhi.fridge.notification.application.NotificationDeliveryService;
import com.xianzhi.fridge.recipe.application.RecipeImportProcessor;
import com.xianzhi.fridge.recipe.application.RecipeIndexRebuildProcessor;
import com.xianzhi.fridge.identity.application.UserLifecycleService;
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
    private final VoiceDraftService voiceDrafts; private final InventoryNotificationScanner notificationScanner;
    private final NotificationDeliveryService notificationDelivery;
    private final RecipeImportProcessor recipeImports;
    private final RecipeIndexRebuildProcessor recipeIndexRebuilds;
    private final UserLifecycleService userLifecycle;
    private final OperationalMetrics operationalMetrics;
    public WorkerJobs(OutboxProcessor outbox, EnvironmentAggregationService environment, SensorReadingStore readings,
                      VoiceDraftService voiceDrafts, InventoryNotificationScanner notificationScanner,
                      NotificationDeliveryService notificationDelivery, RecipeImportProcessor recipeImports,
                      RecipeIndexRebuildProcessor recipeIndexRebuilds, UserLifecycleService userLifecycle,
                      OperationalMetrics operationalMetrics, Clock clock) {
        this.outbox = outbox; this.environment = environment; this.readings = readings; this.voiceDrafts=voiceDrafts;
        this.notificationScanner=notificationScanner; this.notificationDelivery=notificationDelivery;
        this.recipeImports=recipeImports; this.clock = clock;
        this.recipeIndexRebuilds=recipeIndexRebuilds;
        this.userLifecycle=userLifecycle;
        this.operationalMetrics=operationalMetrics;
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
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "inventory-notification-scan", lockAtMostFor = "PT4M")
    public void inventoryNotifications() { notificationScanner.scan(); }
    @Scheduled(fixedDelayString = "${app.notification.delivery-delay:PT10S}", initialDelayString = "PT10S")
    @SchedulerLock(name = "notification-delivery", lockAtMostFor = "PT1M")
    public void notificationDelivery() { notificationDelivery.process(); }
    @Scheduled(cron = "0 15 * * * *")
    @SchedulerLock(name = "voice-draft-expiry", lockAtMostFor = "PT10M")
    public void voiceExpiry() { voiceDrafts.expire(); }
    @Scheduled(fixedDelayString = "${app.ai.import-delay:PT5S}", initialDelayString = "PT5S")
    @SchedulerLock(name = "recipe-import", lockAtMostFor = "PT5M")
    public void recipeImports() { recipeImports.processBatch(); }
    @Scheduled(fixedDelayString = "${app.ai.index-rebuild-delay:PT10S}", initialDelayString = "PT10S")
    @SchedulerLock(name = "recipe-index-rebuild", lockAtMostFor = "PT2H")
    public void recipeIndexRebuilds() { recipeIndexRebuilds.processNext(); }
    @Scheduled(cron = "0 35 3 * * *")
    @SchedulerLock(name = "user-anonymization", lockAtMostFor = "PT30M")
    public void userAnonymization() { userLifecycle.anonymizeDue(); }
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT1S")
    public void heartbeat() { operationalMetrics.heartbeat("worker"); }
}
