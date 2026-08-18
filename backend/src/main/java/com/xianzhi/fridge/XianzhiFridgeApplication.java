package com.xianzhi.fridge;

import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, TelemetryProperties.class})
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class XianzhiFridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianzhiFridgeApplication.class, args);
    }
}
