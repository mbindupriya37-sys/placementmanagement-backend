package com.example.backend.recruiter.controller;

import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.common.dto.ApiResponse;
import com.example.backend.drive.dto.PlacementDriveResponse;
import com.example.backend.recruiter.dto.RecommendationRequest;
import com.example.backend.recruiter.dto.RecommendationResponse;
import com.example.backend.recruiter.dto.RecruiterApplicationResponse;
import com.example.backend.recruiter.service.RecruiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter")
@PreAuthorize("hasRole('COMPANY_RECRUITER')")
@Tag(name = "Company Recruiter", description = "Company-scoped applicant review and candidate recommendations")
public class RecruiterController {

    private final RecruiterService recruiterService;

    public RecruiterController(RecruiterService recruiterService) {
        this.recruiterService = recruiterService;
    }

    @GetMapping("/drives")
    @Operation(summary = "List placement drives belonging to the authenticated recruiter's company")
    public ApiResponse<List<PlacementDriveResponse>> getMyDrives(Authentication authentication) {
        return ApiResponse.success(recruiterService.getMyDrives(authentication.getName()));
    }

    @GetMapping("/applications")
    @Operation(summary = "List applications for the authenticated recruiter's company")
    public ApiResponse<List<RecruiterApplicationResponse>> getApplications(
            Authentication authentication,
            @RequestParam(required = false) Long driveId,
            @RequestParam(required = false) ApplicationStatus status) {
        return ApiResponse.success(recruiterService.getApplications(authentication.getName(), driveId, status));
    }

    @PostMapping("/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recommend an applicant to the placement office")
    public ApiResponse<RecommendationResponse> submitRecommendation(
            Authentication authentication,
            @Valid @RequestBody RecommendationRequest request) {
        return ApiResponse.success(recruiterService.submitRecommendation(authentication.getName(), request),
                "Candidate recommendation submitted successfully");
    }

    @GetMapping("/recommendations")
    @Operation(summary = "List recommendations submitted by the authenticated recruiter")
    public ApiResponse<List<RecommendationResponse>> getMyRecommendations(Authentication authentication) {
        return ApiResponse.success(recruiterService.getMyRecommendations(authentication.getName()));
    }
}
