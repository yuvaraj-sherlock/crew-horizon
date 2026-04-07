package com.crewhorizon.notificationservice.repository;
import com.crewhorizon.notificationservice.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries ORDER BY n.createdAt ASC")
    List<NotificationEntity> findRetryableNotifications(@Param("maxRetries") int maxRetries);
    List<NotificationEntity> findByRecipientEmployeeIdOrderByCreatedAtDesc(String employeeId);
}
