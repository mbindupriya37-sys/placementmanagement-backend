package com.example.backend.application.controller;

import com.example.backend.application.dto.BulkApplicationStatusRequest;
import com.example.backend.application.dto.ShortlistApplicationResponse;
import com.example.backend.application.service.ShortlistingService;
import com.example.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('TPO_ADMIN')")
@Tag(name = "Application Shortlisting", description = "TPO_ADMIN application review actions")
public class ShortlistingController {

    private final ShortlistingService shortlistingService;

    public ShortlistingController(ShortlistingService shortlistingService) {
        this.shortlistingService = shortlistingService;
    }

    @GetMapping("/api/drives/{driveId}/applications")
    @Operation(summary = "List applications for a drive (TPO_ADMIN only)")
    public ApiResponse<List<ShortlistApplicationResponse>> getApplicationsForDrive(
            @PathVariable Long driveId) {
        return ApiResponse.success(shortlistingService.getApplicationsForDrive(driveId));
    }

    @PatchMapping("/api/applications/{applicationId}/shortlist")
    @Operation(summary = "Shortlist an APPLIED application (TPO_ADMIN only)")
    public ApiResponse<ShortlistApplicationResponse> shortlist(@PathVariable Long applicationId) {
        return ApiResponse.success(shortlistingService.shortlist(applicationId),
                "Application shortlisted successfully");
    }

    @PatchMapping("/api/applications/{applicationId}/reject")
    @Operation(summary = "Reject an APPLIED application (TPO_ADMIN only)")
    public ApiResponse<ShortlistApplicationResponse> reject(@PathVariable Long applicationId) {
        return ApiResponse.success(shortlistingService.reject(applicationId),
                "Application rejected successfully");
    }

    @PatchMapping("/api/drives/{driveId}/applications/shortlist")
    @Operation(summary = "Shortlist multiple APPLIED applications (TPO_ADMIN only)")
    public ApiResponse<List<ShortlistApplicationResponse>> bulkShortlist(
            @PathVariable Long driveId,
            @Valid @RequestBody BulkApplicationStatusRequest request) {
        return ApiResponse.success(shortlistingService.bulkShortlist(driveId, request),
                "Applications shortlisted successfully");
    }

    @PatchMapping("/api/drives/{driveId}/applications/reject")
    @Operation(summary = "Reject multiple APPLIED applications (TPO_ADMIN only)")
    public ApiResponse<List<ShortlistApplicationResponse>> bulkReject(
            @PathVariable Long driveId,
            @Valid @RequestBody BulkApplicationStatusRequest request) {
        return ApiResponse.success(shortlistingService.bulkReject(driveId, request),
                "Applications rejected successfully");
    }
}
