package com.example.backend.application.controller;

import com.example.backend.application.dto.ApplicationResponse;
import com.example.backend.application.service.ApplicationService;
import com.example.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Applications", description = "Apply to placement drives and view your own applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{driveId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to a placement drive as the authenticated student")
    public ApiResponse<ApplicationResponse> apply(
            @PathVariable Long driveId,
            Authentication authentication) {
        return ApiResponse.success(applicationService.apply(authentication.getName(), driveId),
                "Application submitted successfully");
    }

    @GetMapping("/my")
    @Operation(summary = "List applications belonging to the authenticated student")
    public ApiResponse<List<ApplicationResponse>> getMyApplications(Authentication authentication) {
        return ApiResponse.success(applicationService.getMyApplications(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an application belonging to the authenticated student")
    public ApiResponse<ApplicationResponse> getMyApplication(
            @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.success(applicationService.getMyApplication(authentication.getName(), id));
    }
}
