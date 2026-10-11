package com.example.backend.eligibility.exception;

import com.example.backend.eligibility.dto.EligibilityResponse;

public class StudentProfileMissingException extends RuntimeException {

    private final EligibilityResponse eligibilityResponse;

    public StudentProfileMissingException(EligibilityResponse eligibilityResponse) {
        super("Student profile not found");
        this.eligibilityResponse = eligibilityResponse;
    }

    public EligibilityResponse getEligibilityResponse() {
        return eligibilityResponse;
    }
}
