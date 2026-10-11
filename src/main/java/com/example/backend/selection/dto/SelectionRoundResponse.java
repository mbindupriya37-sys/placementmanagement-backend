package com.example.backend.selection.dto;

import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;

import java.time.LocalDateTime;

public class SelectionRoundResponse {

    private Long id;
    private Long placementDriveId;
    private String driveTitle;
    private String roundName;
    private SelectionRoundType roundType;
    private Integer roundOrder;
    private String description;
    private LocalDateTime scheduledDateTime;
    private SelectionRoundStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlacementDriveId() { return placementDriveId; }
    public void setPlacementDriveId(Long placementDriveId) { this.placementDriveId = placementDriveId; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String driveTitle) { this.driveTitle = driveTitle; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public SelectionRoundType getRoundType() { return roundType; }
    public void setRoundType(SelectionRoundType roundType) { this.roundType = roundType; }
    public Integer getRoundOrder() { return roundOrder; }
    public void setRoundOrder(Integer roundOrder) { this.roundOrder = roundOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getScheduledDateTime() { return scheduledDateTime; }
    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
    public SelectionRoundStatus getStatus() { return status; }
    public void setStatus(SelectionRoundStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
