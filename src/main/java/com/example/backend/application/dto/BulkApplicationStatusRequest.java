package com.example.backend.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkApplicationStatusRequest {

    @NotEmpty(message = "At least one application ID is required")
    @Size(max = 500, message = "No more than 500 applications can be updated at once")
    private List<@NotNull @Positive Long> applicationIds;

    public List<Long> getApplicationIds() { return applicationIds; }
    public void setApplicationIds(List<Long> applicationIds) { this.applicationIds = applicationIds; }
}
