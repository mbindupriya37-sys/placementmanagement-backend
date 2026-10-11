package com.example.backend.eligibility;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.eligibility.service.EligibilityService;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EligibilityMissingCriteriaTests {

    @Test
    void reportsAllMissingStudentValuesAndDriveCriteria() {
        UserRepository userRepository = mock(UserRepository.class);
        StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
        PlacementDriveRepository driveRepository = mock(PlacementDriveRepository.class);
        EligibilityService eligibilityService =
                new EligibilityService(userRepository, profileRepository, driveRepository);

        User student = new User();
        student.setId(1L);
        student.setEmail("missing-values@example.com");
        student.setName("Incomplete Student");
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

        StudentProfile profile = new StudentProfile();
        when(profileRepository.findByUserEmail(student.getEmail())).thenReturn(Optional.of(profile));

        PlacementDrive drive = new PlacementDrive();
        drive.setId(2L);
        drive.setDriveTitle("Incomplete Drive");
        drive.setEligibleBranches(List.of());
        when(driveRepository.findById(2L)).thenReturn(Optional.of(drive));

        EligibilityResponse result = eligibilityService.checkEligibility(student.getEmail(), 2L);

        assertFalse(result.isEligible());
        assertEquals(List.of(
                "Student CGPA is missing",
                "Drive minimum CGPA criterion is missing",
                "Student backlog count is missing",
                "Drive maximum backlogs criterion is missing",
                "Student branch is missing",
                "Drive eligible branches criterion is missing",
                "Student graduation year is missing",
                "Drive graduation year criterion is missing"), result.getReasons());
    }
}
