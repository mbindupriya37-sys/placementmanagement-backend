package com.example.backend.notification.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.notification.dto.MarkNotificationReadResponse;
import com.example.backend.notification.dto.NotificationCountResponse;
import com.example.backend.notification.dto.NotificationResponse;
import com.example.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('STUDENT', 'TPO_ADMIN', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
@Tag(name = "Notifications", description = "View and manage in-app notifications for the authenticated user")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List notifications belonging to the authenticated user, newest first")
    public ApiResponse<List<NotificationResponse>> getMyNotifications(Authentication authentication) {
        return ApiResponse.success(notificationService.getMyNotifications(authentication.getName()));
    }

    @GetMapping("/count")
    @Operation(summary = "Get total and unread notification counts for the authenticated user")
    public ApiResponse<NotificationCountResponse> getCount(Authentication authentication) {
        return ApiResponse.success(notificationService.getCount(authentication.getName()));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark one of the authenticated user's notifications as read")
    public ApiResponse<MarkNotificationReadResponse> markAsRead(
            @PathVariable Long notificationId, Authentication authentication) {
        return ApiResponse.success(notificationService.markAsRead(authentication.getName(), notificationId),
                "Notification marked as read");
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all unread notifications for the authenticated user as read")
    public ApiResponse<Long> markAllAsRead(Authentication authentication) {
        return ApiResponse.success(notificationService.markAllAsRead(authentication.getName()),
                "Unread notifications marked as read");
    }
}
