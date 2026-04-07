package com.crewhorizon.notificationservice.service.impl;

import com.crewhorizon.notificationservice.entity.NotificationEntity;
import com.crewhorizon.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ============================================================
 * Notification Service Implementation
 * ============================================================
 * WHAT: Handles sending notifications via email (extensible
 *       to SMS and push) with async execution, persistence,
 *       and automatic retry for failed deliveries.
 *
 * WHY @Async on sendNotification:
 *       Email delivery (SMTP handshake, server response) can
 *       take 100ms–2s. If the roster-service waits synchronously
 *       for email delivery before confirming the assignment,
 *       scheduler UX is degraded. @Async returns immediately
 *       to the caller and delivers the notification in a
 *       background thread from the configured thread pool.
 *
 * WHY store before send (not after):
 *       Persisting the notification record BEFORE attempting
 *       delivery means even if the process crashes mid-send,
 *       the notification exists in PENDING state and will be
 *       picked up by the retry scheduler.
 *       This is the "outbox pattern" — write intent, then act.
 *
 * WHY @Scheduled for retry (not a message queue like Kafka):
 *       For simplicity in phase 1 of the project, a scheduled
 *       retry job is sufficient. For high-volume notifications,
 *       Kafka/RabbitMQ would provide better throughput and
 *       guaranteed delivery semantics. The notification entity
 *       structure already supports that migration.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Sends a roster assignment notification asynchronously.
     *
     * WHY pass all data (not fetch from other services):
     * The notification service is a leaf service — it should
     * not make outbound calls to other services. All data needed
     * for the notification is passed in by the caller (roster-service).
     * This keeps the notification service decoupled and independently
     * deployable.
     */
    @Async
    @Transactional
    public void sendRosterAssignmentNotification(
            String employeeId, String email,
            String flightNumber, String dutyDate,
            String assignedRole, String departureAirport, String arrivalAirport) {

        String subject = String.format("[CREW HORIZON] Roster Assignment: %s on %s",
                flightNumber, dutyDate);

        String body = String.format("""
                Dear Crew Member %s,
                
                You have been assigned to the following flight:
                
                Flight Number : %s
                Duty Date     : %s
                Role          : %s
                Route         : %s → %s
                
                Please log into the CREW Horizon portal to confirm your assignment.
                
                Important: Your assignment must be confirmed within 24 hours.
                
                This is an automated notification from CREW Horizon.
                Please do not reply to this email.
                """,
                employeeId, flightNumber, dutyDate, assignedRole,
                departureAirport, arrivalAirport);

        sendEmail(employeeId, email, subject, body,
                NotificationEntity.NotificationType.ROSTER_ASSIGNMENT,
                flightNumber);
    }

    /**
     * Sends qualification expiry warning to crew member and HR.
     */
    @Async
    @Transactional
    public void sendQualificationExpiryWarning(
            String employeeId, String email,
            String aircraftType, String qualificationType, String expiryDate) {

        String subject = String.format("[CREW HORIZON] URGENT: Qualification Expiry – %s %s",
                qualificationType, aircraftType);

        String body = String.format("""
                QUALIFICATION EXPIRY NOTICE
                
                Employee ID : %s
                Aircraft    : %s
                Qual Type   : %s
                Expiry Date : %s
                
                This qualification will expire in less than 30 days.
                Please contact your Training Manager to schedule renewal.
                
                Note: Flights requiring this qualification cannot be assigned
                after the expiry date.
                """,
                employeeId, aircraftType, qualificationType, expiryDate);

        sendEmail(employeeId, email, subject, body,
                NotificationEntity.NotificationType.QUALIFICATION_EXPIRY,
                employeeId + "-" + qualificationType);
    }

    /**
     * Core email sending logic with persistence and error handling.
     */
    private void sendEmail(String employeeId, String email, String subject,
                            String body, NotificationEntity.NotificationType type,
                            String referenceId) {
        // WHY persist before sending: outbox pattern — record intent first
        NotificationEntity notification = NotificationEntity.builder()
                .recipientEmployeeId(employeeId)
                .recipientEmail(email)
                .notificationType(type)
                .channel(NotificationEntity.NotificationChannel.EMAIL)
                .subject(subject)
                .body(body)
                .status(NotificationEntity.NotificationStatus.PENDING)
                .referenceId(referenceId)
                .build();

        notification = notificationRepository.save(notification);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@crew-horizon.com");

            mailSender.send(message);

            notification.setStatus(NotificationEntity.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

            log.info("Notification sent: type={}, employee={}, email={}",
                    type, employeeId, email);

        } catch (MailException e) {
            notification.setStatus(NotificationEntity.NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)));
            notificationRepository.save(notification);

            log.error("Email delivery failed for {}: {}", email, e.getMessage());
        }
    }

    /**
     * Retry scheduler for failed notifications.
     *
     * WHY cron "0 * * * * *" (every minute):
     * Frequent enough to minimize delivery delay on retry.
     * Combined with MAX_RETRY_ATTEMPTS = 3, a notification
     * gets up to 3 attempts within 3 minutes of first failure.
     *
     * WHY @Scheduled in the notification service (not a separate job):
     * Keeping the retry logic co-located with the sending logic
     * means one service owns the entire notification lifecycle.
     * A separate "notification-worker" service would add
     * operational complexity without proportionate benefit.
     */
    @Scheduled(cron = "0 * * * * *")  // Every minute
    @Transactional
    public void retryFailedNotifications() {
        List<NotificationEntity> failedNotifications = notificationRepository
                .findRetryableNotifications(MAX_RETRY_ATTEMPTS);

        if (!failedNotifications.isEmpty()) {
            log.info("Retrying {} failed notifications", failedNotifications.size());
        }

        for (NotificationEntity notification : failedNotifications) {
            try {
                notification.setStatus(NotificationEntity.NotificationStatus.RETRYING);
                notification.setRetryCount(notification.getRetryCount() + 1);
                notificationRepository.save(notification);

                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(notification.getRecipientEmail());
                message.setSubject(notification.getSubject());
                message.setText(notification.getBody());
                message.setFrom("noreply@crew-horizon.com");

                mailSender.send(message);

                notification.setStatus(NotificationEntity.NotificationStatus.SENT);
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);

                log.info("Retry successful for notification id={}", notification.getId());

            } catch (MailException e) {
                notification.setStatus(NotificationEntity.NotificationStatus.FAILED);
                notification.setErrorMessage(e.getMessage().substring(
                        0, Math.min(e.getMessage().length(), 500)));
                notificationRepository.save(notification);

                log.warn("Retry {} failed for notification id={}: {}",
                        notification.getRetryCount(), notification.getId(), e.getMessage());
            }
        }
    }
}
