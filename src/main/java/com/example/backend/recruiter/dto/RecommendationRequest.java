package com.example.backend.recruiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RecommendationRequest {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotBlank(message = "Recommendation reason is required")
    @Size(max = 500, message = "Recommendation reason must not exceed 500 characters")
    private String reason;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
