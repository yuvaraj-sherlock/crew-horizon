package com.crewhorizon.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================
 * Notification Entity
 * ============================================================
 * WHAT: Persists a record of every notification sent or
 *       attempted, including delivery status.
 *
 * WHY persist notification records:
 *       1. RETRY LOGIC: If email delivery fails, a scheduled
 *          job scans for FAILED notifications and retries.
 *          Without persistence, transient failures lose data.
 *       2. AUDIT TRAIL: Crew members can view notification
 *          history ("when was I notified about this roster?")
 *       3. DELIVERY CONFIRMATION: Track which notifications
 *          were successfully delivered vs. bounced/failed.
 *       4. COMPLIANCE: Aviation regulations require proof that
 *          crew were notified of assignments within specific
 *          timeframes.
 * ============================================================
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_recipient", columnList = "recipient_employee_id"),
                @Index(name = "idx_notif_status", columnList = "status"),
                @Index(name = "idx_notif_type", columnList = "notification_type"),
                @Index(name = "idx_notif_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_employee_id", nullable = false, length = 20)
    private String recipientEmployeeId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "reference_id", length = 50)
    private String referenceId;  // e.g., assignment ID

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        ROSTER_ASSIGNMENT,    // New flight assigned
        ROSTER_CANCELLATION,  // Assignment cancelled
        ROSTER_CHANGE,        // Roster details modified
        QUALIFICATION_EXPIRY, // License/cert expiring soon
        DUTY_STATUS_CHANGE,   // Status changed by ops
        SYSTEM_ALERT,         // System-generated alerts
        COMPLIANCE_REMINDER   // FTL or rest period reminder
    }

    public enum NotificationChannel {
        EMAIL,
        PUSH,    // Mobile push notification
        SMS      // SMS for critical alerts
    }

    public enum NotificationStatus {
        PENDING,   // Queued, not yet sent
        SENT,      // Successfully delivered
        FAILED,    // Delivery failed
        RETRYING   // Being retried after failure
    }
}
