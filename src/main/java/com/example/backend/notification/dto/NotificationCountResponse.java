package com.example.backend.notification.dto;

public class NotificationCountResponse {

    private long totalCount;
    private long unreadCount;

    public NotificationCountResponse() {
    }

    public NotificationCountResponse(long totalCount, long unreadCount) {
        this.totalCount = totalCount;
        this.unreadCount = unreadCount;
    }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }
}
