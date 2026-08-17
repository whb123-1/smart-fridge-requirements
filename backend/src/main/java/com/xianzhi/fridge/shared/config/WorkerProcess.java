package com.xianzhi.fridge.shared.config;

import java.util.concurrent.CountDownLatch;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class WorkerProcess implements ApplicationRunner {
    private final CountDownLatch shutdown = new CountDownLatch(1);

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        shutdown.await();
    }
}
