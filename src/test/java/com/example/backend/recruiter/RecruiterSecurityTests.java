package com.example.backend.recruiter;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.company.controller.CompanyController;
import com.example.backend.drive.controller.PlacementDriveController;
import com.example.backend.company.dto.RecruiterAssignmentRequest;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.recruiter.controller.RecommendationReviewController;
import com.example.backend.recruiter.controller.RecruiterController;
import com.example.backend.recruiter.dto.RecommendationRequest;
import com.example.backend.recruiter.entity.RecommendationStatus;
import com.example.backend.recruiter.service.RecruiterService;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
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
class RecruiterSecurityTests {

    @Autowired
    private RecruiterController recruiterController;

    @Autowired
    private RecommendationReviewController reviewController;

    @Autowired
    private CompanyController companyController;

    @Autowired
    private PlacementDriveController placementDriveController;

    @Autowired
    private RecruiterService recruiterService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository driveRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCanOnlyListApplicationsForAssignedCompany() {
        Company company = createCompany("Recruiter Own Company");
        User recruiter = createUser("recruiter@example.com", Role.COMPANY_RECRUITER);
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        Application ownApplication = createApplication(company, "Own Company Student");
        createApplication(createCompany("Other Recruiter Company"), "Other Company Student");

        var results = recruiterController.getApplications(
                SecurityContextHolder.getContext().getAuthentication(), null, null).getData();

        assertEquals(1, results.size());
        assertEquals(ownApplication.getId(), results.get(0).getApplicationId());
        assertEquals("Own Company Student", results.get(0).getStudentName());
        assertEquals("Computer Science", results.get(0).getBranch());
        assertEquals(new BigDecimal("8.50"), results.get(0).getCgpa());
        assertEquals(List.of("Java", "SQL"), results.get(0).getSkills());
        assertEquals(ApplicationStatus.APPLIED, results.get(0).getStatus());
        assertFalse(results.get(0).isResumeAvailable());
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCannotRecommendApplicantFromAnotherCompany() {
        Company company = createCompany("Recommendation Own Company");
        User recruiter = createUser("recruiter@example.com", Role.COMPANY_RECRUITER);
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        Application otherApplication = createApplication(
                createCompany("Recommendation Other Company"), "Other Candidate");
        RecommendationRequest request = recommendationRequest(otherApplication.getId(), "Strong technical fit");

        assertThrows(ResourceNotFoundException.class, () -> recruiterController.submitRecommendation(
                SecurityContextHolder.getContext().getAuthentication(), request));
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCanSubmitAndRetrieveValidRecommendationWithReviewedStatus() {
        Company company = createCompany("Recommendation Company");
        User recruiter = createUser("recruiter@example.com", Role.COMPANY_RECRUITER);
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        Application application = createApplication(company, "Recommended Student");
        var submitted = recruiterController.submitRecommendation(
                SecurityContextHolder.getContext().getAuthentication(),
                recommendationRequest(application.getId(), "Strong Java and SQL experience")).getData();

        assertEquals(RecommendationStatus.PENDING, submitted.getStatus());
        assertEquals("Strong Java and SQL experience", submitted.getReason());
        assertEquals(1, recruiterController.getMyRecommendations(
                SecurityContextHolder.getContext().getAuthentication()).getData().size());

        var reviewed = recruiterService.updateRecommendationStatus(
                submitted.getRecommendationId(), RecommendationStatus.APPROVED);
        assertEquals(RecommendationStatus.APPROVED, reviewed.getStatus());
        assertEquals(RecommendationStatus.APPROVED, recruiterController.getMyRecommendations(
                SecurityContextHolder.getContext().getAuthentication()).getData().get(0).getStatus());
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void invalidRecommendationRequestsAreRejected() {
        Company company = createCompany("Invalid Recommendation Company");
        User recruiter = createUser("recruiter@example.com", Role.COMPANY_RECRUITER);
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        Application application = createApplication(company, "Candidate With Invalid Request");

        assertThrows(BadRequestException.class, () -> recruiterController.submitRecommendation(
                SecurityContextHolder.getContext().getAuthentication(),
                recommendationRequest(null, "Missing application ID")));
        assertThrows(BadRequestException.class, () -> recruiterController.submitRecommendation(
                SecurityContextHolder.getContext().getAuthentication(),
                recommendationRequest(application.getId(), "   ")));
        recruiterController.submitRecommendation(SecurityContextHolder.getContext().getAuthentication(),
                recommendationRequest(application.getId(), "Qualified candidate"));
        assertThrows(BadRequestException.class, () -> recruiterController.submitRecommendation(
                SecurityContextHolder.getContext().getAuthentication(),
                recommendationRequest(application.getId(), "Duplicate recommendation")));
        assertThrows(BadRequestException.class, () -> recruiterService.updateRecommendationStatus(
                recruiterController.getMyRecommendations(SecurityContextHolder.getContext().getAuthentication())
                        .getData().get(0).getRecommendationId(),
                RecommendationStatus.PENDING));
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCannotAccessTpoRecommendationOperations() {
        assertThrows(AccessDeniedException.class, () -> reviewController.getRecommendations());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "TPO_ADMIN")
    void tpoCanAssignRecruiterToCompany() {
        Company company = createCompany("Assigned By Tpo Company");
        User recruiter = createUser("company.recruiter@example.com", Role.COMPANY_RECRUITER);
        RecruiterAssignmentRequest request = new RecruiterAssignmentRequest();
        request.setEmail(recruiter.getEmail());

        companyController.assignRecruiter(company.getId(), request);

        User persistedRecruiter = userRepository.findById(recruiter.getId()).orElseThrow();
        assertEquals(company.getId(), persistedRecruiter.getCompany().getId());
    }

    @Test
    @WithMockUser(username = "drive-recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCanOnlyReadDrivesForAssignedCompany() {
        Company company = createCompany("Drive Access Company");
        User recruiter = createUser("drive-recruiter@example.com", Role.COMPANY_RECRUITER);
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        Application ownApplication = createApplication(company, "Own Drive Candidate");
        Application otherApplication = createApplication(createCompany("Other Drive Company"), "Other Drive Candidate");

        var drives = placementDriveController.getAll(
                SecurityContextHolder.getContext().getAuthentication()).getData();
        assertEquals(1, drives.size());
        assertEquals(ownApplication.getPlacementDrive().getId(), drives.get(0).getId());
        assertThrows(com.example.backend.common.exception.ResourceNotFoundException.class,
                () -> placementDriveController.getById(otherApplication.getPlacementDrive().getId(),
                        SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "COMPANY_RECRUITER")
    void unassignedRecruiterCannotListApplications() {
        createUser("recruiter@example.com", Role.COMPANY_RECRUITER);
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> recruiterController.getApplications(
                SecurityContextHolder.getContext().getAuthentication(), null, null));
        assertEquals("Your recruiter account is not assigned to a company", exception.getMessage());
    }

    @Test
    void recruiterJwtAuthorityMatchesControllerRole() {
        User recruiter = createUser("jwt-recruiter@example.com", Role.COMPANY_RECRUITER);

        UserDetails details = userDetailsService.loadUserByUsername(recruiter.getEmail());

        assertTrue(details.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COMPANY_RECRUITER")));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = "STUDENT")
    void studentCannotUseRecruiterApplicationEndpoint() {
        assertThrows(AccessDeniedException.class, () -> recruiterController.getApplications(
                SecurityContextHolder.getContext().getAuthentication(), null, null));
    }

    private Company createCompany(String name) {
        Company company = new Company();
        company.setCompanyName(name);
        return companyRepository.saveAndFlush(company);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setName(role == Role.STUDENT ? "Test Student" : "Test Recruiter");
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Application createApplication(Company company, String studentName) {
        User student = createUser(studentName.toLowerCase().replace(' ', '.') + "@example.com", Role.STUDENT);
        student.setName(studentName);
        student = userRepository.saveAndFlush(student);
        StudentProfile profile = new StudentProfile();
        profile.setUser(student);
        profile.setPhoneNumber("+1 555 123 4567");
        profile.setCollege("Example University");
        profile.setBranch("Computer Science");
        profile.setGraduationYear(2027);
        profile.setCgpa(new BigDecimal("8.50"));
        profile.setBacklogs(0);
        profile.setSkills(List.of("Java", "SQL"));
        profile.setResumeUrl(null);
        studentProfileRepository.saveAndFlush(profile);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle(company.getCompanyName() + " Drive");
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
        drive = driveRepository.saveAndFlush(drive);

        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(ApplicationStatus.APPLIED);
        return applicationRepository.saveAndFlush(application);
    }

    private RecommendationRequest recommendationRequest(Long applicationId, String reason) {
        RecommendationRequest request = new RecommendationRequest();
        request.setApplicationId(applicationId);
        request.setReason(reason);
        return request;
    }
}
