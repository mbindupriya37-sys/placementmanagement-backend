package com.example.backend.selection.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.selection.dto.SelectionRoundRequest;
import com.example.backend.selection.dto.SelectionRoundResponse;
import com.example.backend.selection.dto.SelectionRoundStatusRequest;
import com.example.backend.selection.service.SelectionRoundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Selection Rounds", description = "Manage recruitment rounds for placement drives")
public class SelectionRoundController {

    private final SelectionRoundService selectionRoundService;

    public SelectionRoundController(SelectionRoundService selectionRoundService) {
        this.selectionRoundService = selectionRoundService;
    }

    @PostMapping("/api/drives/{driveId}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Create a selection round (TPO_ADMIN only)")
    public ApiResponse<SelectionRoundResponse> create(
            @PathVariable Long driveId,
            @Valid @RequestBody SelectionRoundRequest request) {
        return ApiResponse.success(selectionRoundService.create(driveId, request),
                "Selection round created successfully");
    }

    @GetMapping("/api/drives/{driveId}/rounds")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'STUDENT', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "List rounds for a drive (authenticated placement roles)")
    public ApiResponse<List<SelectionRoundResponse>> getForDrive(
            @PathVariable Long driveId, Authentication authentication) {
        boolean recruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        return ApiResponse.success(recruiter
                ? selectionRoundService.getForDriveForRecruiter(driveId, authentication.getName())
                : selectionRoundService.getForDrive(driveId));
    }

    @GetMapping("/api/rounds/{roundId}")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'STUDENT', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "Get a round (authenticated placement roles)")
    public ApiResponse<SelectionRoundResponse> getById(
            @PathVariable Long roundId, Authentication authentication) {
        boolean recruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        return ApiResponse.success(recruiter
                ? selectionRoundService.getByIdForRecruiter(roundId, authentication.getName())
                : selectionRoundService.getById(roundId));
    }

    @PutMapping("/api/rounds/{roundId}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update a selection round (TPO_ADMIN only)")
    public ApiResponse<SelectionRoundResponse> update(
            @PathVariable Long roundId,
            @Valid @RequestBody SelectionRoundRequest request) {
        return ApiResponse.success(selectionRoundService.update(roundId, request),
                "Selection round updated successfully");
    }

    @DeleteMapping("/api/rounds/{roundId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Delete a round without results (TPO_ADMIN only)")
    public void delete(@PathVariable Long roundId) {
        selectionRoundService.delete(roundId);
    }

    @PatchMapping("/api/rounds/{roundId}/status")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update round status using permitted transitions (TPO_ADMIN only)")
    public ApiResponse<SelectionRoundResponse> updateStatus(
            @PathVariable Long roundId,
            @Valid @RequestBody SelectionRoundStatusRequest request) {
        return ApiResponse.success(selectionRoundService.updateStatus(roundId, request.getStatus()),
                "Selection round status updated successfully");
    }
}
