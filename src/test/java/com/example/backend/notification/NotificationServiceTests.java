package com.example.backend.notification;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.notification.dto.MarkNotificationReadResponse;
import com.example.backend.notification.dto.NotificationResponse;
import com.example.backend.notification.entity.Notification;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import com.example.backend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsUnreadNotificationWithReferenceAndTimestamp() {
        User recipient = createUser("notification-create@example.com", Role.STUDENT);

        Notification notification = notificationService.createNotification(recipient, "Application Submitted",
                "Your application has been submitted.", NotificationType.APPLICATION_SUBMITTED,
                NotificationReferenceType.APPLICATION, 15L);

        assertNotNull(notification.getId());
        assertFalse(notification.isRead());
        assertNotNull(notification.getCreatedAt());
        assertEquals(NotificationReferenceType.APPLICATION, notification.getReferenceType());
        assertEquals(15L, notification.getReferenceId());
        assertEquals(recipient.getId(), notification.getRecipient().getId());
    }

    @Test
    void returnsOnlyOwnNotificationsNewestFirst() {
        User owner = createUser("notification-owner@example.com", Role.STUDENT);
        User other = createUser("notification-other@example.com", Role.STUDENT);
        Notification oldest = createAt(owner, "Old", 1L, LocalDateTime.now().minusDays(1));
        Notification newest = createAt(owner, "New", 2L, LocalDateTime.now().plusDays(1));
        create(other, "Other user's notice", 3L);

        List<NotificationResponse> results = notificationService.getMyNotifications(owner.getEmail());

        assertEquals(2, results.size());
        assertEquals(newest.getId(), results.get(0).getId());
        assertEquals(oldest.getId(), results.get(1).getId());
    }

    @Test
    void returnsTotalAndUnreadCountsForOnlyTheUser() {
        User owner = createUser("notification-count@example.com", Role.TPO_ADMIN);
        User other = createUser("notification-count-other@example.com", Role.STUDENT);
        Notification read = create(owner, "Read", 10L);
        read.setRead(true);
        read.setReadAt(LocalDateTime.now());
        notificationRepository.save(read);
        create(owner, "Unread", 11L);
        create(other, "Other", 12L);

        assertEquals(2, notificationService.getCount(owner.getEmail()).getTotalCount());
        assertEquals(1, notificationService.getCount(owner.getEmail()).getUnreadCount());
    }

    @Test
    void marksOwnNotificationReadAndPreservesOriginalReadTimestamp() {
        User owner = createUser("notification-read@example.com", Role.STUDENT);
        Notification notification = create(owner, "Read me", 20L);

        MarkNotificationReadResponse marked = notificationService.markAsRead(owner.getEmail(), notification.getId());

        assertTrue(marked.isRead());
        assertNotNull(marked.getReadAt());
        MarkNotificationReadResponse repeated =
                notificationService.markAsRead(owner.getEmail(), notification.getId());
        assertEquals(marked.getReadAt(), repeated.getReadAt());
    }

    @Test
    void cannotReadOrMarkAnotherUsersNotification() {
        User owner = createUser("notification-privacy-owner@example.com", Role.STUDENT);
        User other = createUser("notification-privacy-other@example.com", Role.STUDENT);
        Notification notification = create(owner, "Private", 30L);

        assertTrue(notificationService.getMyNotifications(other.getEmail()).isEmpty());
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(other.getEmail(), notification.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(owner.getEmail(), Long.MAX_VALUE));
    }

    @Test
    void marksAllOfOnlyTheUsersUnreadNotificationsRead() {
        User owner = createUser("notification-read-all@example.com", Role.COMPANY_RECRUITER);
        User other = createUser("notification-read-all-other@example.com", Role.STUDENT);
        Notification first = create(owner, "First", 40L);
        Notification second = create(owner, "Second", 41L);
        Notification otherNotice = create(other, "Other", 42L);
        first.setRead(true);
        first.setReadAt(LocalDateTime.now().minusHours(1));
        notificationRepository.save(first);
        LocalDateTime existingReadAt = first.getReadAt();

        assertEquals(1, notificationService.markAllAsRead(owner.getEmail()));
        assertTrue(notificationRepository.findById(second.getId()).orElseThrow().isRead());
        assertEquals(existingReadAt, notificationRepository.findById(first.getId()).orElseThrow().getReadAt());
        assertFalse(notificationRepository.findById(otherNotice.getId()).orElseThrow().isRead());
    }

    @Test
    void rejectsInvalidNotificationData() {
        User recipient = createUser("notification-invalid@example.com", Role.STUDENT);
        assertThrows(BadRequestException.class, () -> notificationService.createNotification(recipient,
                "  ", "message", NotificationType.GENERAL, NotificationReferenceType.GENERAL, null));
        assertThrows(BadRequestException.class, () -> notificationService.createNotification(recipient,
                "x".repeat(201), "message", NotificationType.GENERAL, NotificationReferenceType.GENERAL, null));
        assertThrows(BadRequestException.class, () -> notificationService.createNotification(recipient,
                "Title", " ", NotificationType.GENERAL, NotificationReferenceType.GENERAL, null));
        assertThrows(BadRequestException.class, () -> notificationService.createNotification(recipient,
                "Title", "message", null, NotificationReferenceType.GENERAL, null));
        assertThrows(BadRequestException.class, () -> notificationService.createNotification(null,
                "Title", "message", NotificationType.GENERAL, NotificationReferenceType.GENERAL, null));
    }

    private Notification create(User recipient, String title, Long referenceId) {
        return notificationService.createNotification(recipient, title, "Notification message",
                NotificationType.GENERAL, NotificationReferenceType.GENERAL, referenceId);
    }

    private Notification createAt(User recipient, String title, Long referenceId, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage("Notification message");
        notification.setNotificationType(NotificationType.GENERAL);
        notification.setReferenceType(NotificationReferenceType.GENERAL);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(createdAt);
        return notificationRepository.saveAndFlush(notification);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName("Notification test user");
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
