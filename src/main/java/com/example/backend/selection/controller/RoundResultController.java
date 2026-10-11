package com.example.backend.selection.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.selection.dto.RoundResultRequest;
import com.example.backend.selection.dto.RoundResultResponse;
import com.example.backend.selection.dto.RoundResultUpdateRequest;
import com.example.backend.selection.service.RoundResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Round Results", description = "Record and view student selection-round results")
public class RoundResultController {

    private final RoundResultService roundResultService;

    public RoundResultController(RoundResultService roundResultService) {
        this.roundResultService = roundResultService;
    }

    @PostMapping("/api/rounds/{roundId}/results")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Record a result for a shortlisted application (TPO_ADMIN only)")
    public ApiResponse<RoundResultResponse> create(
            @PathVariable Long roundId,
            @Valid @RequestBody RoundResultRequest request) {
        return ApiResponse.success(roundResultService.create(roundId, request),
                "Round result recorded successfully");
    }

    @GetMapping("/api/rounds/{roundId}/results")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "List all results for a round (TPO_ADMIN only)")
    public ApiResponse<List<RoundResultResponse>> getForRound(@PathVariable Long roundId) {
        return ApiResponse.success(roundResultService.getForRound(roundId));
    }

    @GetMapping("/api/applications/{applicationId}/round-results")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "List round results for the authenticated student's own application")
    public ApiResponse<List<RoundResultResponse>> getForStudentApplication(
            @PathVariable Long applicationId,
            Authentication authentication) {
        return ApiResponse.success(
                roundResultService.getForStudentApplication(applicationId, authentication.getName()));
    }

    @PutMapping("/api/round-results/{resultId}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update a round result (TPO_ADMIN only)")
    public ApiResponse<RoundResultResponse> update(
            @PathVariable Long resultId,
            @Valid @RequestBody RoundResultUpdateRequest request) {
        return ApiResponse.success(roundResultService.update(resultId, request),
                "Round result updated successfully");
    }
}
