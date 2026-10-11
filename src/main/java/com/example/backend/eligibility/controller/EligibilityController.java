package com.example.backend.eligibility.controller;

import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.eligibility.exception.StudentProfileMissingException;
import com.example.backend.eligibility.service.EligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drives/{driveId}/eligibility")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Eligibility", description = "Check eligibility for a placement drive")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping
    @Operation(summary = "Check the authenticated student's eligibility for a drive")
    public EligibilityResponse checkEligibility(
            @PathVariable Long driveId,
            Authentication authentication) {
        return eligibilityService.checkEligibility(authentication.getName(), driveId);
    }

    @ExceptionHandler(StudentProfileMissingException.class)
    public ResponseEntity<EligibilityResponse> handleStudentProfileMissing(
            StudentProfileMissingException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getEligibilityResponse());
    }
}
