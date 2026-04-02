package com.crewhorizon.notificationservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
/**
 * CREW Horizon - Notification Service
 * WHY @EnableAsync: Notifications (email, push, SMS) are I/O-bound
 * and should NEVER block the calling service. Async dispatch means
 * roster creation doesn't wait for email delivery.
 * WHY standalone service: Notification logic (templates, channels,
 * retry logic) is a cross-cutting concern used by all services.
 */
@SpringBootApplication
@EnableAsync
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
