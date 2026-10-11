package com.example.backend.drive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PlacementDriveStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "DRAFT|OPEN|CLOSED|COMPLETED|CANCELLED",
            message = "Status must be DRAFT, OPEN, CLOSED, COMPLETED, or CANCELLED")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
