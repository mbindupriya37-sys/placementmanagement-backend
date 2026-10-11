package com.example.backend.eligibility;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.eligibility.controller.EligibilityController;
import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EligibilitySecurityTests {

    @Autowired
    private EligibilityController eligibilityController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Test
    @WithMockUser(username = "jwt-student@example.com", roles = "STUDENT")
    void endpointUsesAuthenticatedEmailRatherThanClientStudentId() {
        User authenticatedStudent = createStudent();
        PlacementDrive drive = createDrive();

        EligibilityResponse result = eligibilityController.checkEligibility(
                drive.getId(), SecurityContextHolder.getContext().getAuthentication());

        assertEquals(authenticatedStudent.getId(), result.getStudentId());
        assertEquals("Authenticated Student", result.getStudentName());
        assertEquals(drive.getId(), result.getDriveId());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "TPO_ADMIN")
    void nonStudentCannotCheckEligibility() {
        assertThrows(AccessDeniedException.class,
                () -> eligibilityController.checkEligibility(
                        1L, SecurityContextHolder.getContext().getAuthentication()));
    }

    private User createStudent() {
        User user = new User();
        user.setEmail("jwt-student@example.com");
        user.setName("Authenticated Student");
        user.setPassword("encoded-password");
        user.setRole(Role.STUDENT);
        user = userRepository.saveAndFlush(user);

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
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    private PlacementDrive createDrive() {
        Company company = new Company();
        company.setCompanyName("Eligibility Security Test Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Security Test Drive");
        drive.setJobRole("Software Developer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.50"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("12.00"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(7));
        drive.setDriveDate(LocalDate.now().plusDays(8));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
