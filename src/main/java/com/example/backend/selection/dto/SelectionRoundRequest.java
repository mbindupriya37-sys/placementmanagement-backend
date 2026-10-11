package com.example.backend.selection.dto;

import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class SelectionRoundRequest {

    @NotBlank
    @Size(max = 200)
    private String roundName;

    @NotNull
    private SelectionRoundType roundType;

    @NotNull
    @Positive
    private Integer roundOrder;

    @Size(max = 10000)
    private String description;

    @NotNull
    private LocalDateTime scheduledDateTime;

    @NotNull
    private SelectionRoundStatus status;

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
}
