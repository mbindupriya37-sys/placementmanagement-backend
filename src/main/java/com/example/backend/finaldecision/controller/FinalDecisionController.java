package com.example.backend.finaldecision.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.finaldecision.dto.FinalDecisionRequest;
import com.example.backend.finaldecision.dto.FinalDecisionResponse;
import com.example.backend.finaldecision.service.FinalDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Final Decisions", description = "Record and view TPO final candidate decisions")
public class FinalDecisionController {

    private final FinalDecisionService finalDecisionService;

    public FinalDecisionController(FinalDecisionService finalDecisionService) {
        this.finalDecisionService = finalDecisionService;
    }

    @PatchMapping("/api/applications/{applicationId}/final-decision")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Record the final decision for a shortlisted application (TPO_ADMIN only)")
    public ApiResponse<FinalDecisionResponse> decide(
            @PathVariable Long applicationId,
            @Valid @RequestBody FinalDecisionRequest request,
            Authentication authentication) {
        return ApiResponse.success(finalDecisionService.decide(
                applicationId, authentication.getName(), request), "Final decision recorded successfully");
    }

    @GetMapping("/api/applications/{applicationId}/final-decision")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'STUDENT')")
    @Operation(summary = "View a final decision (TPO_ADMIN or application-owning STUDENT)")
    public ApiResponse<FinalDecisionResponse> getForApplication(
            @PathVariable Long applicationId,
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TPO_ADMIN"));
        return ApiResponse.success(finalDecisionService.getForApplication(
                applicationId, authentication.getName(), admin));
    }

    @GetMapping("/api/drives/{driveId}/final-decisions")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "List final decisions for a drive (TPO_ADMIN only)")
    public ApiResponse<List<FinalDecisionResponse>> getForDrive(@PathVariable Long driveId) {
        return ApiResponse.success(finalDecisionService.getForDrive(driveId));
    }
}
