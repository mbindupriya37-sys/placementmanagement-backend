package com.example.backend.selection;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
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
import com.example.backend.selection.controller.RoundResultController;
import com.example.backend.selection.controller.SelectionRoundController;
import com.example.backend.selection.dto.RoundResultRequest;
import com.example.backend.selection.dto.SelectionRoundRequest;
import com.example.backend.selection.entity.RoundResultStatus;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;
import com.example.backend.selection.repository.RoundResultRepository;
import com.example.backend.selection.repository.SelectionRoundRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SelectionRoundSecurityTests {

    @Autowired
    private SelectionRoundController selectionRoundController;

    @Autowired
    private RoundResultController roundResultController;

    @Autowired
    private SelectionRoundRepository selectionRoundRepository;

    @Autowired
    private RoundResultRepository roundResultRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanCreateRound() {
        PlacementDrive drive = createDrive();

        assertEquals(drive.getId(), selectionRoundController.create(drive.getId(), roundRequest())
                .getData().getPlacementDriveId());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanViewRoundsButCannotCreateOne() {
        PlacementDrive drive = createDrive();
        createRound(drive);

        assertEquals(1, selectionRoundController.getForDrive(drive.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
        assertThrows(AccessDeniedException.class,
                () -> selectionRoundController.create(drive.getId(), roundRequest()));
    }

    @Test
    @WithMockUser(username = "round-recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCanViewRound() {
        PlacementDrive drive = createDrive();
        User recruiter = new User();
        recruiter.setEmail("round-recruiter@example.com");
        recruiter.setName("Round Recruiter");
        recruiter.setPassword("encoded-password");
        recruiter.setRole(Role.COMPANY_RECRUITER);
        recruiter.setCompany(drive.getCompany());
        userRepository.saveAndFlush(recruiter);
        SelectionRound round = createRound(drive);
        assertEquals(round.getId(), selectionRoundController.getById(round.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().getId());
    }

    @Test
    @WithMockUser(roles = "DEPARTMENT_COORDINATOR")
    void departmentCoordinatorCanViewRound() {
        PlacementDrive drive = createDrive();
        createRound(drive);
        assertEquals(1, selectionRoundController.getForDrive(drive.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
    }

    @Test
    @WithMockUser(roles = "COMPANY_RECRUITER")
    void nonTpoCannotUpdateOrDeleteRoundsOrManageResults() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        assertThrows(AccessDeniedException.class,
                () -> selectionRoundController.update(round.getId(), roundRequest()));
        assertThrows(AccessDeniedException.class, () -> selectionRoundController.delete(round.getId()));
        assertThrows(AccessDeniedException.class,
                () -> roundResultController.create(round.getId(), resultRequest(1L)));
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanRecordAndViewRoundResults() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application application = createApplication(drive, "tpo-results@example.com");

        var response = roundResultController.create(round.getId(), resultRequest(application.getId())).getData();
        assertEquals(RoundResultStatus.PASSED, response.getResult());
        assertEquals(1, roundResultController.getForRound(round.getId()).getData().size());
        assertEquals(0, roundResultController.update(response.getResultId(), updateResultRequest())
                .getData().getScore().compareTo(new BigDecimal("95.00")));
    }

    @Test
    @WithMockUser(username = "round-owner@example.com", roles = "STUDENT")
    void studentCanViewOwnResultsButNotAnotherStudentsResults() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application ownerApplication = createApplication(drive, "round-owner@example.com");
        Application otherApplication = createApplication(drive, "round-other@example.com");
        addResult(round, ownerApplication);
        addResult(round, otherApplication);

        assertEquals(1, roundResultController.getForStudentApplication(ownerApplication.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
        assertThrows(ResourceNotFoundException.class,
                () -> roundResultController.getForStudentApplication(otherApplication.getId(),
                        SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotReadAllRoundResults() {
        assertThrows(AccessDeniedException.class, () -> roundResultController.getForRound(1L));
    }

    private PlacementDrive createDrive() {
        Company company = new Company();
        company.setCompanyName("Selection Security Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Selection Security Drive");
        drive.setJobRole("Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.00"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("10.00"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(7));
        drive.setDriveDate(LocalDate.now().plusDays(8));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }

    private SelectionRound createRound(PlacementDrive drive) {
        SelectionRound round = new SelectionRound();
        round.setPlacementDrive(drive);
        round.setRoundName("Technical");
        round.setRoundType(SelectionRoundType.TECHNICAL);
        round.setRoundOrder(1);
        round.setScheduledDateTime(LocalDateTime.now().plusDays(1));
        round.setStatus(SelectionRoundStatus.UPCOMING);
        return selectionRoundRepository.saveAndFlush(round);
    }

    private Application createApplication(PlacementDrive drive, String email) {
        User student = new User();
        student.setEmail(email);
        student.setName("Student " + email);
        student.setPassword("encoded-password");
        student.setRole(Role.STUDENT);
        student = userRepository.saveAndFlush(student);

        StudentProfile profile = new StudentProfile();
        profile.setUser(student);
        profile.setPhoneNumber("+1 555 123 4567");
        profile.setCollege("Example University");
        profile.setBranch("Computer Science");
        profile.setGraduationYear(2027);
        profile.setCgpa(new BigDecimal("8.00"));
        profile.setBacklogs(0);
        profile.setSkills(List.of("Java"));
        profile.setResumeUrl("resumes/student.pdf");
        studentProfileRepository.saveAndFlush(profile);

        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(ApplicationStatus.SHORTLISTED);
        return applicationRepository.saveAndFlush(application);
    }

    private void addResult(SelectionRound round, Application application) {
        com.example.backend.selection.entity.RoundResult result =
                new com.example.backend.selection.entity.RoundResult();
        result.setApplication(application);
        result.setSelectionRound(round);
        result.setResult(RoundResultStatus.PASSED);
        roundResultRepository.saveAndFlush(result);
    }

    private SelectionRoundRequest roundRequest() {
        SelectionRoundRequest request = new SelectionRoundRequest();
        request.setRoundName("Technical");
        request.setRoundType(SelectionRoundType.TECHNICAL);
        request.setRoundOrder(1);
        request.setScheduledDateTime(LocalDateTime.now().plusDays(1));
        request.setStatus(SelectionRoundStatus.UPCOMING);
        return request;
    }

    private RoundResultRequest resultRequest(Long applicationId) {
        RoundResultRequest request = new RoundResultRequest();
        request.setApplicationId(applicationId);
        request.setResult(RoundResultStatus.PASSED);
        request.setScore(new BigDecimal("85.50"));
        return request;
    }

    private com.example.backend.selection.dto.RoundResultUpdateRequest updateResultRequest() {
        com.example.backend.selection.dto.RoundResultUpdateRequest request =
                new com.example.backend.selection.dto.RoundResultUpdateRequest();
        request.setResult(RoundResultStatus.PASSED);
        request.setScore(new BigDecimal("95.00"));
        return request;
    }
}
