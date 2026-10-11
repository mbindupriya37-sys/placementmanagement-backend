package com.example.backend.scheduling.dto;

import com.example.backend.scheduling.entity.InterviewScheduleStatus;
import jakarta.validation.constraints.NotNull;

public class InterviewScheduleStatusRequest {

    @NotNull
    private InterviewScheduleStatus status;

    public InterviewScheduleStatus getStatus() { return status; }
    public void setStatus(InterviewScheduleStatus status) { this.status = status; }
}
