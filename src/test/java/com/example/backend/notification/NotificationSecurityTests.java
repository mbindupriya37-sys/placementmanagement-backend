package com.example.backend.notification;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.notification.controller.NotificationController;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationSecurityTests {

    @Autowired
    private NotificationController notificationController;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanAccessNotificationApis() {
        assertEquals(0, notificationController.getMyNotifications(
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
        assertEquals(0L, notificationController.getCount(
                SecurityContextHolder.getContext().getAuthentication()).getData().getTotalCount());
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoAdminCanAccessNotificationApis() {
        assertEquals(0, notificationController.getMyNotifications(
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
    }

    @Test
    @WithMockUser(roles = "COMPANY_RECRUITER")
    void companyRecruiterCanAccessNotificationApis() {
        assertEquals(0, notificationController.getCount(
                SecurityContextHolder.getContext().getAuthentication()).getData().getUnreadCount());
    }

    @Test
    @WithMockUser(roles = "DEPARTMENT_COORDINATOR")
    void departmentCoordinatorCanAccessNotificationApis() {
        assertEquals(0, notificationController.getMyNotifications(
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
    }

    @Test
    @WithMockUser(username = "notification-api-owner@example.com", roles = "STUDENT")
    void notificationApiUsesAuthenticatedEmailToScopeReadOperations() {
        User owner = createUser("notification-api-owner@example.com", Role.STUDENT);
        User other = createUser("notification-api-other@example.com", Role.STUDENT);
        var notification = notificationService.createNotification(owner, "Owned", "Owned message",
                NotificationType.GENERAL, NotificationReferenceType.GENERAL, null);

        assertEquals(notification.getId(), notificationController.markAsRead(notification.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().getId());
        var otherNotification = notificationService.createNotification(other, "Other", "Other message",
                NotificationType.GENERAL, NotificationReferenceType.GENERAL, null);
        assertThrows(ResourceNotFoundException.class, () -> notificationController.markAsRead(
                otherNotification.getId(), SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(authorities = "ROLE_UNKNOWN")
    void unlistedRolesCannotAccessNotificationApi() {
        assertThrows(AuthorizationDeniedException.class, () -> notificationController.getCount(
                SecurityContextHolder.getContext().getAuthentication()));
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName("Notification security user");
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
