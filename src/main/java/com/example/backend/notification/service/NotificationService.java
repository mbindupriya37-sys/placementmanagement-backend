package com.example.backend.notification.service;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.notification.dto.MarkNotificationReadResponse;
import com.example.backend.notification.dto.NotificationCountResponse;
import com.example.backend.notification.dto.NotificationResponse;
import com.example.backend.notification.entity.Notification;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(
            User recipient,
            String title,
            String message,
            NotificationType notificationType,
            NotificationReferenceType referenceType,
            Long referenceId) {
        if (recipient == null || recipient.getId() == null) {
            throw new BadRequestException("Notification recipient is required");
        }
        if (!userRepository.existsById(recipient.getId())) {
            throw new ResourceNotFoundException("Notification recipient", "id", recipient.getId());
        }
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw new BadRequestException("Notification title is required and must not exceed 200 characters");
        }
        if (message == null || message.isBlank()) {
            throw new BadRequestException("Notification message is required");
        }
        if (notificationType == null || referenceType == null) {
            throw new BadRequestException("Notification and reference types are required");
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title.trim());
        notification.setMessage(message.trim());
        notification.setNotificationType(notificationType);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setActivityCount(1);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification createNotificationIfAbsent(
            User recipient,
            String title,
            String message,
            NotificationType notificationType,
            NotificationReferenceType referenceType,
            Long referenceId) {
        if (recipient != null && recipient.getId() != null) {
            Notification existing = notificationRepository
                    .findByRecipient_IdAndNotificationTypeAndReferenceTypeAndReferenceId(
                            recipient.getId(), notificationType, referenceType, referenceId)
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        return createNotification(recipient, title, message, notificationType, referenceType, referenceId);
    }

    @Transactional
    public Notification recordApplicationActivity(User recipient, Long driveId, String driveTitle) {
        if (recipient == null || recipient.getRole() != Role.TPO_ADMIN) {
            throw new BadRequestException("Application activity notifications require a TPO_ADMIN recipient");
        }
        if (driveId == null || driveTitle == null || driveTitle.isBlank()) {
            throw new BadRequestException("Placement drive details are required for application activity");
        }

        List<Notification> unreadNotifications =
                notificationRepository.findUnreadByRecipientAndTypeAndReference(
                        recipient.getId(), NotificationType.APPLICATION_ACTIVITY,
                        NotificationReferenceType.PLACEMENT_DRIVE, driveId);
        Notification notification = unreadNotifications.isEmpty()
                ? null
                : unreadNotifications.get(0);
        int activityCount = notification == null ? 1 : notification.getActivityCount() + 1;
        String countLabel = activityCount == 1 ? "1 new application received for "
                : activityCount + " new applications received for ";
        String message = countLabel + driveTitle + ".";

        if (notification == null) {
            return createNotification(recipient, "New application activity", message,
                    NotificationType.APPLICATION_ACTIVITY,
                    NotificationReferenceType.PLACEMENT_DRIVE, driveId);
        }

        notification.setActivityCount(activityCount);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String email) {
        return notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationCountResponse getCount(String email) {
        return new NotificationCountResponse(
                notificationRepository.countByRecipient_Email(email),
                notificationRepository.countByRecipient_EmailAndIsReadFalse(email));
    }

    @Transactional
    public MarkNotificationReadResponse markAsRead(String email, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipient_Email(notificationId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return toReadResponse(notification);
    }

    @Transactional
    public long markAllAsRead(String email) {
        List<Notification> unreadNotifications =
                notificationRepository.findAllByRecipient_EmailAndIsReadFalseOrderByCreatedAtDesc(email);
        LocalDateTime readAt = LocalDateTime.now();
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(readAt);
        });
        notificationRepository.saveAll(unreadNotifications);
        return unreadNotifications.size();
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setNotificationType(notification.getNotificationType());
        response.setReferenceType(notification.getReferenceType());
        response.setReferenceId(notification.getReferenceId());
        response.setRead(notification.isRead());
        response.setCreatedAt(notification.getCreatedAt());
        response.setReadAt(notification.getReadAt());
        return response;
    }

    private MarkNotificationReadResponse toReadResponse(Notification notification) {
        MarkNotificationReadResponse response = new MarkNotificationReadResponse();
        response.setId(notification.getId());
        response.setRead(notification.isRead());
        response.setReadAt(notification.getReadAt());
        return response;
    }
}
