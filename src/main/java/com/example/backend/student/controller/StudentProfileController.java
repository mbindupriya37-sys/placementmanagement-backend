package com.example.backend.student.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.student.dto.StudentProfileRequest;
import com.example.backend.student.dto.StudentProfileResponse;
import com.example.backend.student.dto.StudentResumeFile;
import com.example.backend.student.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Student Profile", description = "Manage the authenticated student's profile")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create the authenticated student's profile")
    public ApiResponse<StudentProfileResponse> create(
            @Valid @RequestBody StudentProfileRequest request,
            Authentication authentication) {
        return ApiResponse.success(studentProfileService.create(authentication.getName(), request),
                "Student profile created successfully");
    }

    @GetMapping
    @Operation(summary = "Get the authenticated student's profile")
    public ApiResponse<StudentProfileResponse> get(Authentication authentication) {
        return ApiResponse.success(studentProfileService.get(authentication.getName()));
    }

    @PutMapping
    @Operation(summary = "Update the authenticated student's profile")
    public ApiResponse<StudentProfileResponse> update(
            @Valid @RequestBody StudentProfileRequest request,
            Authentication authentication) {
        return ApiResponse.success(studentProfileService.update(authentication.getName(), request),
                "Student profile updated successfully");
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the authenticated student's resume PDF")
    public ApiResponse<StudentProfileResponse> uploadResume(
            @RequestParam("resume") MultipartFile resume,
            Authentication authentication) throws IOException {
        return ApiResponse.success(studentProfileService.uploadResume(authentication.getName(), resume),
                "Resume uploaded successfully");
    }

    @GetMapping("/resume/{filename:.+}")
    @Operation(summary = "Open the authenticated student's uploaded resume")
    public ResponseEntity<Resource> getResume(
            @PathVariable String filename,
            Authentication authentication) {
        StudentResumeFile resume = studentProfileService.getResume(authentication.getName(), filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(resume.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(resume.path()));
    }
}
