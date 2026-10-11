package com.example.backend.company.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.company.dto.CompanyRequest;
import com.example.backend.company.dto.CompanyResponse;
import com.example.backend.company.dto.RecruiterAssignmentRequest;
import com.example.backend.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@Tag(name = "Companies", description = "Manage and browse companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PutMapping("/{id}/recruiter")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Assign a recruiter account to a company (TPO_ADMIN)")
    public ApiResponse<CompanyResponse> assignRecruiter(
            @PathVariable Long id,
            @Valid @RequestBody RecruiterAssignmentRequest request) {
        return ApiResponse.success(companyService.assignRecruiter(id, request.getEmail()),
                "Recruiter assigned to company successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Create a company (TPO_ADMIN)")
    public ApiResponse<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ApiResponse.success(companyService.create(request), "Company created successfully");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'COMPANY_RECRUITER', 'STUDENT')")
    @Operation(summary = "List companies (TPO_ADMIN, COMPANY_RECRUITER, STUDENT)")
    public ApiResponse<List<CompanyResponse>> getAll() {
        return ApiResponse.success(companyService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'COMPANY_RECRUITER', 'STUDENT')")
    @Operation(summary = "Get a company (TPO_ADMIN, COMPANY_RECRUITER, STUDENT)")
    public ApiResponse<CompanyResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(companyService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update a company (TPO_ADMIN)")
    public ApiResponse<CompanyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CompanyRequest request) {
        return ApiResponse.success(companyService.update(id, request), "Company updated successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Delete a company (TPO_ADMIN)")
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}
