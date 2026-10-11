package com.example.backend.notification.repository;

import com.example.backend.notification.entity.Notification;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("select notification from Notification notification "
            + "where notification.recipient.email = :email "
            + "order by notification.createdAt desc, notification.id desc")
    List<Notification> findAllByRecipient_EmailOrderByCreatedAtDesc(@Param("email") String email);

    @Query("select notification from Notification notification "
            + "where notification.recipient.email = :email and notification.isRead = false "
            + "order by notification.createdAt desc, notification.id desc")
    List<Notification> findAllByRecipient_EmailAndIsReadFalseOrderByCreatedAtDesc(@Param("email") String email);

    long countByRecipient_Email(String email);
    long countByRecipient_EmailAndIsReadFalse(String email);
    Optional<Notification> findByIdAndRecipient_Email(Long id, String email);
    Optional<Notification> findByRecipient_IdAndNotificationTypeAndReferenceTypeAndReferenceId(
            Long recipientId, NotificationType notificationType,
            NotificationReferenceType referenceType, Long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification "
            + "where notification.recipient.id = :recipientId "
            + "and notification.notificationType = :notificationType "
            + "and notification.referenceType = :referenceType "
            + "and notification.referenceId = :referenceId "
            + "and notification.isRead = false "
            + "order by notification.id desc")
    List<Notification> findUnreadByRecipientAndTypeAndReference(
            @Param("recipientId") Long recipientId,
            @Param("notificationType") NotificationType notificationType,
            @Param("referenceType") NotificationReferenceType referenceType,
            @Param("referenceId") Long referenceId);
}
