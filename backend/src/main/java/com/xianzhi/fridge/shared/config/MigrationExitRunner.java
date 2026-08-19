package com.xianzhi.fridge.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Component
@Profile("migration")
@Order(Ordered.LOWEST_PRECEDENCE)
public class MigrationExitRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    public MigrationExitRunner(ConfigurableApplicationContext context){this.context=context;}
    @Override public void run(ApplicationArguments arguments){context.close();}
}
