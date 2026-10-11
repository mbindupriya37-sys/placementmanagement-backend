package com.example.backend.application;

import com.example.backend.application.controller.ShortlistingController;
import com.example.backend.application.dto.BulkApplicationStatusRequest;
import com.example.backend.application.dto.ShortlistApplicationResponse;
import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
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
class ShortlistingSecurityTests {

    @Autowired
    private ShortlistingController shortlistingController;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanRetrieveDriveApplications() {
        PlacementDrive drive = createDrive();
        createApplication(drive);

        assertEquals(1, shortlistingController.getApplicationsForDrive(drive.getId()).getData().size());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotRetrieveDriveApplicationsOrShortlist() {
        assertThrows(AccessDeniedException.class,
                () -> shortlistingController.getApplicationsForDrive(1L));
        assertThrows(AccessDeniedException.class,
                () -> shortlistingController.shortlist(1L));
    }

    @Test
    @WithMockUser(roles = "COMPANY_RECRUITER")
    void companyRecruiterCannotShortlist() {
        assertThrows(AccessDeniedException.class,
                () -> shortlistingController.shortlist(1L));
    }

    @Test
    @WithMockUser(roles = "DEPARTMENT_COORDINATOR")
    void departmentCoordinatorCannotShortlist() {
        assertThrows(AccessDeniedException.class,
                () -> shortlistingController.reject(1L));
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanShortlistThroughControllerAndStatusIsPersisted() {
        PlacementDrive drive = createDrive();
        Application application = createApplication(drive);

        ShortlistApplicationResponse response =
                shortlistingController.shortlist(application.getId()).getData();

        assertEquals(ApplicationStatus.SHORTLISTED, response.getApplicationStatus());
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(application.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanUseBulkShortlistEndpoint() {
        PlacementDrive drive = createDrive();
        Application application = createApplication(drive);
        BulkApplicationStatusRequest request = new BulkApplicationStatusRequest();
        request.setApplicationIds(List.of(application.getId()));

        assertEquals(ApplicationStatus.SHORTLISTED,
                shortlistingController.bulkShortlist(drive.getId(), request)
                        .getData().get(0).getApplicationStatus());
    }

    private Application createApplication(PlacementDrive drive) {
        User student = new User();
        student.setEmail("shortlisting-security@example.com");
        student.setName("Security Test Student");
        student.setPassword("encoded-password");
        student.setRole(Role.STUDENT);
        student = userRepository.saveAndFlush(student);

        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(ApplicationStatus.APPLIED);
        return applicationRepository.saveAndFlush(application);
    }

    private PlacementDrive createDrive() {
        Company company = new Company();
        company.setCompanyName("Shortlisting Security Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Shortlisting Security Drive");
        drive.setJobRole("Software Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.00"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("10.00"));
        drive.setApplicationStartDate(LocalDate.now().minusDays(1));
        drive.setApplicationEndDate(LocalDate.now().plusDays(1));
        drive.setDriveDate(LocalDate.now().plusDays(2));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
