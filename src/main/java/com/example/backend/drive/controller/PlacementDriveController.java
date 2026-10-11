package com.example.backend.drive.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.drive.dto.PlacementDriveRequest;
import com.example.backend.drive.dto.PlacementDriveResponse;
import com.example.backend.drive.dto.PlacementDriveStatusRequest;
import com.example.backend.drive.service.PlacementDriveService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drives")
@Tag(name = "Placement Drives", description = "Manage and browse company recruitment drives")
public class PlacementDriveController {

    private final PlacementDriveService placementDriveService;

    public PlacementDriveController(PlacementDriveService placementDriveService) {
        this.placementDriveService = placementDriveService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Create a drive (TPO_ADMIN)")
    public ApiResponse<PlacementDriveResponse> create(@Valid @RequestBody PlacementDriveRequest request) {
        return ApiResponse.success(placementDriveService.create(request), "Placement drive created successfully");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'TPO_ADMIN', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "List drives (STUDENT, TPO_ADMIN, COMPANY_RECRUITER, DEPARTMENT_COORDINATOR)")
    public ApiResponse<List<PlacementDriveResponse>> getAll(Authentication authentication) {
        boolean recruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        return ApiResponse.success(recruiter
                ? placementDriveService.getAllForRecruiter(authentication.getName())
                : placementDriveService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TPO_ADMIN', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "Get a drive (STUDENT, TPO_ADMIN, COMPANY_RECRUITER, DEPARTMENT_COORDINATOR)")
    public ApiResponse<PlacementDriveResponse> getById(
            @PathVariable Long id, Authentication authentication) {
        boolean recruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        return ApiResponse.success(recruiter
                ? placementDriveService.getByIdForRecruiter(id, authentication.getName())
                : placementDriveService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update a drive (TPO_ADMIN)")
    public ApiResponse<PlacementDriveResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PlacementDriveRequest request) {
        return ApiResponse.success(placementDriveService.update(id, request),
                "Placement drive updated successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Delete a drive (TPO_ADMIN)")
    public void delete(@PathVariable Long id) {
        placementDriveService.delete(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update drive status (TPO_ADMIN)")
    public ApiResponse<PlacementDriveResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PlacementDriveStatusRequest request) {
        return ApiResponse.success(placementDriveService.updateStatus(id, request.getStatus()),
                "Placement drive status updated successfully");
    }
}
