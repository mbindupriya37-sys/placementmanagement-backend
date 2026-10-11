package com.example.backend.eligibility;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.eligibility.exception.StudentProfileMissingException;
import com.example.backend.eligibility.service.EligibilityService;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EligibilityServiceTests {

    @Autowired
    private EligibilityService eligibilityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Test
    void returnsSuccessfulEligibilityResponseForMatchingStudent() {
        StudentProfile profile = createStudent("eligible@example.com");
        PlacementDrive drive = createDrive();

        EligibilityResponse result = eligibilityService.checkEligibility(
                profile.getUser().getEmail(), drive.getId());

        assertEquals(profile.getUser().getId(), result.getStudentId());
        assertEquals("Test Student", result.getStudentName());
        assertEquals(drive.getId(), result.getDriveId());
        assertEquals("Software Developer Campus Drive", result.getDriveTitle());
        assertTrue(result.isEligible());
        assertEquals(List.of("Student satisfies all eligibility criteria"), result.getReasons());
    }

    @Test
    void reportsCgpaBelowMinimum() {
        StudentProfile profile = createStudent("cgpa@example.com");
        profile.setCgpa(new BigDecimal("6.80"));
        PlacementDrive drive = createDrive();

        EligibilityResponse result = check(profile, drive);

        assertFalse(result.isEligible());
        assertEquals(List.of("CGPA 6.8 is below the required minimum CGPA 7.5"), result.getReasons());
    }

    @Test
    void reportsBacklogsAboveMaximum() {
        StudentProfile profile = createStudent("backlogs@example.com");
        profile.setBacklogs(2);
        PlacementDrive drive = createDrive();

        EligibilityResponse result = check(profile, drive);

        assertFalse(result.isEligible());
        assertEquals(List.of("Backlogs 2 exceed the maximum allowed backlogs 1"), result.getReasons());
    }

    @Test
    void reportsBranchNotEligible() {
        StudentProfile profile = createStudent("branch@example.com");
        profile.setBranch("ECE");
        PlacementDrive drive = createDrive();

        EligibilityResponse result = check(profile, drive);

        assertFalse(result.isEligible());
        assertEquals(List.of("Branch ECE is not eligible for this drive"), result.getReasons());
    }

    @Test
    void reportsGraduationYearMismatch() {
        StudentProfile profile = createStudent("year@example.com");
        profile.setGraduationYear(2028);
        PlacementDrive drive = createDrive();

        EligibilityResponse result = check(profile, drive);

        assertFalse(result.isEligible());
        assertEquals(List.of("Graduation year 2028 does not match the required graduation year 2027"),
                result.getReasons());
    }

    @Test
    void reportsAllApplicableFailures() {
        StudentProfile profile = createStudent("multiple@example.com");
        profile.setCgpa(new BigDecimal("6.80"));
        profile.setBacklogs(2);
        profile.setBranch("ECE");
        profile.setGraduationYear(2028);
        PlacementDrive drive = createDrive();

        EligibilityResponse result = check(profile, drive);

        assertFalse(result.isEligible());
        assertEquals(List.of(
                "CGPA 6.8 is below the required minimum CGPA 7.5",
                "Backlogs 2 exceed the maximum allowed backlogs 1",
                "Branch ECE is not eligible for this drive",
                "Graduation year 2028 does not match the required graduation year 2027"), result.getReasons());
    }

    @Test
    void returnsNotEligibleWithReasonWhenProfileDoesNotExist() {
        User student = createUser("no-profile@example.com");
        PlacementDrive drive = createDrive();

        StudentProfileMissingException exception = assertThrows(StudentProfileMissingException.class,
                () -> eligibilityService.checkEligibility(student.getEmail(), drive.getId()));
        EligibilityResponse result = exception.getEligibilityResponse();

        assertFalse(result.isEligible());
        assertEquals(List.of("Student profile not found"), result.getReasons());
    }

    @Test
    void returnsNotFoundWhenDriveDoesNotExist() {
        User student = createUser("missing-drive@example.com");

        assertThrows(ResourceNotFoundException.class,
                () -> eligibilityService.checkEligibility(student.getEmail(), Long.MAX_VALUE));
    }

    @Test
    void returnsNotFoundWhenStudentDoesNotExist() {
        assertThrows(ResourceNotFoundException.class,
                () -> eligibilityService.checkEligibility("missing@example.com", 1L));
    }

    private EligibilityResponse check(StudentProfile profile, PlacementDrive drive) {
        studentProfileRepository.saveAndFlush(profile);
        return eligibilityService.checkEligibility(profile.getUser().getEmail(), drive.getId());
    }

    private StudentProfile createStudent(String email) {
        User user = createUser(email);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setPhoneNumber("+1 555 123 4567");
        profile.setCollege("Example University");
        profile.setBranch("Computer Science");
        profile.setGraduationYear(2027);
        profile.setCgpa(new BigDecimal("8.00"));
        profile.setBacklogs(0);
        profile.setSkills(List.of("Java"));
        profile.setResumeUrl("resumes/student.pdf");
        return studentProfileRepository.saveAndFlush(profile);
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test Student");
        user.setPassword("encoded-password");
        user.setRole(Role.STUDENT);
        return userRepository.saveAndFlush(user);
    }

    private PlacementDrive createDrive() {
        Company company = new Company();
        company.setCompanyName("Eligibility Test Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Software Developer Campus Drive");
        drive.setJobRole("Software Developer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.50"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science", "Information Technology"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("12.00"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(7));
        drive.setDriveDate(LocalDate.now().plusDays(8));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
