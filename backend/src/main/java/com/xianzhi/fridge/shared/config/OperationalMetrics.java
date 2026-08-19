package com.xianzhi.fridge.shared.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private final JdbcTemplate jdbc;private final MeterRegistry meters;private final String instanceId;
    public OperationalMetrics(JdbcTemplate jdbc,MeterRegistry meters){this.jdbc=jdbc;this.meters=meters;this.instanceId=hostname();}
    @PostConstruct void register(){
        meters.gauge("xianzhi.outbox.pending",this,value->value.count("select count(*) from outbox_event where status='PENDING'"));
        meters.gauge("xianzhi.recipe.import.queued",this,value->value.count("select count(*) from recipe_import_job where status in ('QUEUED','PROCESSING')"));
        meters.gauge("xianzhi.recipe.index.rebuild.active",this,value->value.count("select count(*) from recipe_index_rebuild_job where status in ('QUEUED','PROCESSING')"));
        meters.gauge("xianzhi.worker.heartbeat.age.seconds",this,OperationalMetrics::workerHeartbeatAge);
    }
    public void heartbeat(String component){jdbc.update("insert into operational_heartbeat(component,instance_id,last_seen_at,metadata_json) values(?,?,UTC_TIMESTAMP(3),JSON_OBJECT()) on duplicate key update last_seen_at=values(last_seen_at)",component,instanceId);}
    private double workerHeartbeatAge(){try{Instant seen=jdbc.queryForObject("select max(last_seen_at) from operational_heartbeat where component='worker'",java.sql.Timestamp.class).toInstant();return Math.max(0,Instant.now().getEpochSecond()-seen.getEpochSecond());}catch(Exception exception){return -1;}}
    private double count(String sql){try{Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}catch(Exception exception){return -1;}}
    private static String hostname(){try{return InetAddress.getLocalHost().getHostName();}catch(Exception exception){return "unknown";}}
}
