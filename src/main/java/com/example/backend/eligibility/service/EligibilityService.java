package com.example.backend.eligibility.service;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.eligibility.exception.StudentProfileMissingException;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class EligibilityService {

    private static final String PROFILE_NOT_FOUND_REASON = "Student profile not found";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PlacementDriveRepository placementDriveRepository;

    public EligibilityService(UserRepository userRepository,
                              StudentProfileRepository studentProfileRepository,
                              PlacementDriveRepository placementDriveRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.placementDriveRepository = placementDriveRepository;
    }

    @Transactional(readOnly = true)
    public EligibilityResponse checkEligibility(String email, Long driveId) {
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "email", email));
        PlacementDrive drive = placementDriveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", "id", driveId));

        EligibilityResponse response = new EligibilityResponse();
        response.setStudentId(student.getId());
        response.setStudentName(student.getName());
        response.setDriveId(drive.getId());
        response.setDriveTitle(drive.getDriveTitle());

        StudentProfile profile = studentProfileRepository.findByUserEmail(email).orElse(null);
        if (profile == null) {
            response.setEligible(false);
            response.setReasons(List.of(PROFILE_NOT_FOUND_REASON));
            throw new StudentProfileMissingException(response);
        }

        List<String> reasons = evaluate(profile, drive);
        response.setEligible(reasons.isEmpty());
        response.setReasons(reasons.isEmpty()
                ? List.of("Student satisfies all eligibility criteria")
                : List.copyOf(reasons));
        return response;
    }

    private List<String> evaluate(StudentProfile profile, PlacementDrive drive) {
        List<String> reasons = new ArrayList<>();

        boolean studentCgpaMissing = profile.getCgpa() == null;
        boolean driveCgpaMissing = drive.getMinimumCgpa() == null;
        if (studentCgpaMissing) {
            reasons.add("Student CGPA is missing");
        }
        if (driveCgpaMissing) {
            reasons.add("Drive minimum CGPA criterion is missing");
        }
        if (!studentCgpaMissing && !driveCgpaMissing
                && profile.getCgpa().compareTo(drive.getMinimumCgpa()) < 0) {
            reasons.add("CGPA " + profile.getCgpa().stripTrailingZeros().toPlainString()
                    + " is below the required minimum CGPA "
                    + drive.getMinimumCgpa().stripTrailingZeros().toPlainString());
        }

        boolean studentBacklogsMissing = profile.getBacklogs() == null;
        boolean driveBacklogsMissing = drive.getMaximumBacklogs() == null;
        if (studentBacklogsMissing) {
            reasons.add("Student backlog count is missing");
        }
        if (driveBacklogsMissing) {
            reasons.add("Drive maximum backlogs criterion is missing");
        }
        if (!studentBacklogsMissing && !driveBacklogsMissing
                && profile.getBacklogs() > drive.getMaximumBacklogs()) {
            reasons.add("Backlogs " + profile.getBacklogs()
                    + " exceed the maximum allowed backlogs " + drive.getMaximumBacklogs());
        }

        List<String> eligibleBranches = drive.getEligibleBranches();
        boolean studentBranchMissing = profile.getBranch() == null || profile.getBranch().isBlank();
        boolean driveBranchesMissing = eligibleBranches == null || eligibleBranches.isEmpty();
        if (studentBranchMissing) {
            reasons.add("Student branch is missing");
        }
        if (driveBranchesMissing) {
            reasons.add("Drive eligible branches criterion is missing");
        }
        if (!studentBranchMissing && !driveBranchesMissing && eligibleBranches.stream().noneMatch(branch ->
                branch != null && branch.trim().equalsIgnoreCase(profile.getBranch().trim()))) {
            reasons.add("Branch " + profile.getBranch()
                    + " is not eligible for this drive");
        }

        boolean studentGraduationYearMissing = profile.getGraduationYear() == null;
        boolean driveGraduationYearMissing = drive.getGraduationYear() == null;
        if (studentGraduationYearMissing) {
            reasons.add("Student graduation year is missing");
        }
        if (driveGraduationYearMissing) {
            reasons.add("Drive graduation year criterion is missing");
        }
        if (!studentGraduationYearMissing && !driveGraduationYearMissing
                && !profile.getGraduationYear().equals(drive.getGraduationYear())) {
            reasons.add("Graduation year " + profile.getGraduationYear()
                    + " does not match the required graduation year " + drive.getGraduationYear());
        }

        return reasons;
    }
}
