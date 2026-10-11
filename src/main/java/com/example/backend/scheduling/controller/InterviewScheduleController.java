package com.example.backend.scheduling.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.scheduling.dto.InterviewScheduleRequest;
import com.example.backend.scheduling.dto.InterviewScheduleResponse;
import com.example.backend.scheduling.dto.InterviewScheduleStatusRequest;
import com.example.backend.scheduling.dto.InterviewScheduleUpdateRequest;
import com.example.backend.scheduling.service.InterviewScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@Tag(name = "Interview Scheduling", description = "Schedule interviews and manage candidate slots")
public class InterviewScheduleController {

    private final InterviewScheduleService interviewScheduleService;

    public InterviewScheduleController(InterviewScheduleService interviewScheduleService) {
        this.interviewScheduleService = interviewScheduleService;
    }

    @PostMapping("/api/rounds/{roundId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Schedule a shortlisted application (TPO_ADMIN only)")
    public ApiResponse<InterviewScheduleResponse> create(
            @PathVariable Long roundId,
            @Valid @RequestBody InterviewScheduleRequest request) {
        return ApiResponse.success(interviewScheduleService.create(roundId, request),
                "Interview scheduled successfully");
    }

    @GetMapping("/api/schedules/{scheduleId}")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'STUDENT', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "Get a schedule; students can only view their own")
    public ApiResponse<InterviewScheduleResponse> getById(
            @PathVariable Long scheduleId,
            Authentication authentication) {
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT"));
        boolean isRecruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        InterviewScheduleResponse response = isStudent
                ? interviewScheduleService.getByIdForStudent(scheduleId, authentication.getName())
                : isRecruiter
                ? interviewScheduleService.getByIdForRecruiter(scheduleId, authentication.getName())
                : interviewScheduleService.getById(scheduleId);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/rounds/{roundId}/schedules")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'COMPANY_RECRUITER', 'DEPARTMENT_COORDINATOR')")
    @Operation(summary = "List schedules for a round (TPO_ADMIN, COMPANY_RECRUITER, DEPARTMENT_COORDINATOR)")
    public ApiResponse<List<InterviewScheduleResponse>> getForRound(
            @PathVariable Long roundId, Authentication authentication) {
        boolean recruiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER"));
        return ApiResponse.success(recruiter
                ? interviewScheduleService.getForRoundForRecruiter(roundId, authentication.getName())
                : interviewScheduleService.getForRound(roundId));
    }

    @GetMapping("/api/applications/{applicationId}/schedules")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "List schedules for the authenticated student's own application")
    public ApiResponse<List<InterviewScheduleResponse>> getForStudentApplication(
            @PathVariable Long applicationId,
            Authentication authentication) {
        return ApiResponse.success(interviewScheduleService.getForStudentApplication(
                applicationId, authentication.getName()));
    }

    @PutMapping("/api/schedules/{scheduleId}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Reschedule an interview (TPO_ADMIN only)")
    public ApiResponse<InterviewScheduleResponse> reschedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody InterviewScheduleUpdateRequest request) {
        return ApiResponse.success(interviewScheduleService.reschedule(scheduleId, request),
                "Interview rescheduled successfully");
    }

    @PatchMapping("/api/schedules/{scheduleId}/cancel")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Cancel an interview schedule (TPO_ADMIN only)")
    public ApiResponse<InterviewScheduleResponse> cancel(@PathVariable Long scheduleId) {
        return ApiResponse.success(interviewScheduleService.cancel(scheduleId),
                "Interview schedule cancelled successfully");
    }

    @PatchMapping("/api/schedules/{scheduleId}/status")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update an interview schedule status (TPO_ADMIN only)")
    public ApiResponse<InterviewScheduleResponse> updateStatus(
            @PathVariable Long scheduleId,
            @Valid @RequestBody InterviewScheduleStatusRequest request) {
        return ApiResponse.success(interviewScheduleService.updateStatus(scheduleId, request.getStatus()),
                "Interview schedule status updated successfully");
    }
}
