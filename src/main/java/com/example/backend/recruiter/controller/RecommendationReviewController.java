package com.example.backend.recruiter.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.recruiter.dto.RecommendationResponse;
import com.example.backend.recruiter.dto.RecommendationStatusRequest;
import com.example.backend.recruiter.service.RecruiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tpo/recommendations")
@PreAuthorize("hasRole('TPO_ADMIN')")
@Tag(name = "Recruiter Recommendations", description = "TPO-only recommendation review operations")
public class RecommendationReviewController {

    private final RecruiterService recruiterService;

    public RecommendationReviewController(RecruiterService recruiterService) {
        this.recruiterService = recruiterService;
    }

    @GetMapping
    @Operation(summary = "List candidate recommendations (TPO_ADMIN only)")
    public ApiResponse<List<RecommendationResponse>> getRecommendations() {
        return ApiResponse.success(recruiterService.getAllRecommendations());
    }

    @PatchMapping("/{recommendationId}/status")
    @Operation(summary = "Approve or decline a candidate recommendation (TPO_ADMIN only)")
    public ApiResponse<RecommendationResponse> updateStatus(
            @PathVariable Long recommendationId,
            @Valid @RequestBody RecommendationStatusRequest request) {
        return ApiResponse.success(
                recruiterService.updateRecommendationStatus(recommendationId, request.getStatus()),
                "Candidate recommendation reviewed successfully");
    }
}
