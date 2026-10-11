package com.example.backend.application;

import com.example.backend.application.controller.ApplicationController;
import com.example.backend.application.dto.ApplicationResponse;
import com.example.backend.application.service.ApplicationService;
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
class ApplicationSecurityTests {

    @Autowired
    private ApplicationController applicationController;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Test
    @WithMockUser(username = "jwt-owner@example.com", roles = "STUDENT")
    void applicationUsesAuthenticatedStudentInsteadOfClientStudentId() {
        User student = createStudent("jwt-owner@example.com");
        PlacementDrive drive = createDrive();

        ApplicationResponse result = applicationController.apply(
                drive.getId(), SecurityContextHolder.getContext().getAuthentication()).getData();

        assertEquals(1, applicationService.getMyApplications(student.getEmail()).size());
        assertEquals(drive.getId(), result.getDriveId());
    }

    @Test
    @WithMockUser(username = "jwt-owner@example.com", roles = "STUDENT")
    void studentCannotReadAnotherStudentsApplication() {
        User owner = createStudent("application-owner@example.com");
        User otherStudent = createStudent("jwt-owner@example.com");
        PlacementDrive drive = createDrive();
        ApplicationResponse application = applicationService.apply(owner.getEmail(), drive.getId());

        assertThrows(ResourceNotFoundException.class, () ->
                applicationController.getMyApplication(application.getApplicationId(),
                        SecurityContextHolder.getContext().getAuthentication()));
        assertEquals(0, applicationService.getMyApplications(otherStudent.getEmail()).size());
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void nonStudentRoleCannotUseApplicationEndpoints() {
        assertThrows(AccessDeniedException.class, () ->
                applicationController.getMyApplications(SecurityContextHolder.getContext().getAuthentication()));
    }

    private User createStudent(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test Student");
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
        company.setCompanyName("Application Security Test Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Application Security Test Drive");
        drive.setJobRole("Software Developer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.50"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("12.00"));
        drive.setApplicationStartDate(LocalDate.now().minusDays(1));
        drive.setApplicationEndDate(LocalDate.now().plusDays(1));
        drive.setDriveDate(LocalDate.now().plusDays(2));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
