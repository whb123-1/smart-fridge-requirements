package com.xianzhi.fridge;

import com.xianzhi.fridge.shared.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class XianzhiFridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianzhiFridgeApplication.class, args);
    }
}
