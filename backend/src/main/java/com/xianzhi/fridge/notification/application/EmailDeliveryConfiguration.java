package com.xianzhi.fridge.notification.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailDeliveryConfiguration {
    @Bean EmailDeliveryPort emailDeliveryPort(){return new EmailDeliveryPort(){public boolean enabled(){return false;}public void send(String recipient,String subject,String body){throw new IllegalStateException("Email delivery is disabled");}};}
}
