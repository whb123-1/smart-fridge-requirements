package com.xianzhi.fridge.notification.application;

public interface EmailDeliveryPort { boolean enabled(); void send(String recipient,String subject,String body); }
