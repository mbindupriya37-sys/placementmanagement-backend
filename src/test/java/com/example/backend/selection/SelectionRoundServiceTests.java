package com.example.backend.selection;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import com.example.backend.selection.dto.RoundResultRequest;
import com.example.backend.selection.dto.RoundResultResponse;
import com.example.backend.selection.dto.RoundResultUpdateRequest;
import com.example.backend.selection.dto.SelectionRoundRequest;
import com.example.backend.selection.dto.SelectionRoundResponse;
import com.example.backend.selection.entity.RoundResultStatus;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;
import com.example.backend.selection.repository.SelectionRoundRepository;
import com.example.backend.selection.service.RoundResultService;
import com.example.backend.selection.service.SelectionRoundService;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SelectionRoundServiceTests {

    @Autowired
    private SelectionRoundService selectionRoundService;

    @Autowired
    private RoundResultService roundResultService;

    @Autowired
    private SelectionRoundRepository selectionRoundRepository;

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

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private Validator validator;

    @Test
    void createsRoundAndReturnsItsDrive() {
        PlacementDrive drive = createDrive("Create Round Drive");
        Application shortlistedApplication = createApplication(
                drive, "round-notification@example.com", ApplicationStatus.SHORTLISTED);

        SelectionRoundResponse response = selectionRoundService.create(drive.getId(),
                roundRequest("Aptitude", 1));

        assertNotNull(response.getId());
        assertEquals(drive.getId(), response.getPlacementDriveId());
        assertEquals("Create Round Drive", response.getDriveTitle());
        assertEquals("Aptitude", response.getRoundName());
        var notice = notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                shortlistedApplication.getStudent().getEmail()).get(0);
        assertEquals(NotificationType.ROUND_SCHEDULED, notice.getNotificationType());
        assertEquals(NotificationReferenceType.SELECTION_ROUND, notice.getReferenceType());
        assertEquals(response.getId(), notice.getReferenceId());
        assertEquals(1, response.getRoundOrder());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void rejectsMissingDriveAndDuplicateRoundOrderForSameDrive() {
        assertThrows(ResourceNotFoundException.class,
                () -> selectionRoundService.create(Long.MAX_VALUE, roundRequest("Aptitude", 1)));

        PlacementDrive drive = createDrive("Unique Order Drive");
        selectionRoundService.create(drive.getId(), roundRequest("Aptitude", 1));
        assertThrows(BadRequestException.class,
                () -> selectionRoundService.create(drive.getId(), roundRequest("Technical", 1)));
    }

    @Test
    void permitsTheSameRoundOrderOnDifferentDrivesAndOrdersReads() {
        PlacementDrive firstDrive = createDrive("First Ordering Drive");
        PlacementDrive secondDrive = createDrive("Second Ordering Drive");
        selectionRoundService.create(firstDrive.getId(), roundRequest("Technical", 2));
        selectionRoundService.create(firstDrive.getId(), roundRequest("Aptitude", 1));

        SelectionRoundResponse otherDriveRound = selectionRoundService.create(
                secondDrive.getId(), roundRequest("Other Drive Aptitude", 1));
        List<SelectionRoundResponse> rounds = selectionRoundService.getForDrive(firstDrive.getId());

        assertEquals(List.of(1, 2), rounds.stream().map(SelectionRoundResponse::getRoundOrder).toList());
        assertEquals(1, otherDriveRound.getRoundOrder());
    }

    @Test
    void updatesRoundAndRejectsDuplicateOrder() {
        PlacementDrive drive = createDrive("Update Round Drive");
        SelectionRoundResponse first = selectionRoundService.create(drive.getId(), roundRequest("Aptitude", 1));
        selectionRoundService.create(drive.getId(), roundRequest("Technical", 2));

        SelectionRoundRequest update = roundRequest("Aptitude Updated", 3);
        update.setDescription("Updated details");
        SelectionRoundResponse updated = selectionRoundService.update(first.getId(), update);
        assertEquals("Aptitude Updated", updated.getRoundName());
        assertEquals(3, updated.getRoundOrder());
        assertEquals("Updated details", updated.getDescription());

        assertThrows(BadRequestException.class,
                () -> selectionRoundService.update(first.getId(), roundRequest("Duplicate", 2)));
        assertThrows(ResourceNotFoundException.class,
                () -> selectionRoundService.update(Long.MAX_VALUE, roundRequest("Missing", 4)));
    }

    @Test
    void deletesRoundWithoutResultsButProtectsRoundWithResults() {
        PlacementDrive drive = createDrive("Delete Round Drive");
        SelectionRoundResponse deletable = selectionRoundService.create(drive.getId(), roundRequest("Empty", 1));
        selectionRoundService.delete(deletable.getId());
        assertThrows(ResourceNotFoundException.class, () -> selectionRoundService.getById(deletable.getId()));

        SelectionRound withResult = createRound(drive, "With Results", 2);
        Application application = createApplication(drive, "protected-result@example.com",
                ApplicationStatus.SHORTLISTED);
        roundResultService.create(withResult.getId(), resultRequest(application.getId(), RoundResultStatus.PASSED));
        assertThrows(BadRequestException.class, () -> selectionRoundService.delete(withResult.getId()));
    }

    @Test
    void allowsOnlyValidRoundStatusTransitions() {
        PlacementDrive drive = createDrive("Round Status Drive");
        SelectionRoundResponse upcoming = selectionRoundService.create(drive.getId(), roundRequest("Status", 1));
        SelectionRoundResponse ongoing =
                selectionRoundService.updateStatus(upcoming.getId(), SelectionRoundStatus.ONGOING);
        assertEquals(SelectionRoundStatus.ONGOING, ongoing.getStatus());
        SelectionRoundResponse completed =
                selectionRoundService.updateStatus(upcoming.getId(), SelectionRoundStatus.COMPLETED);
        assertEquals(SelectionRoundStatus.COMPLETED, completed.getStatus());
        assertThrows(BadRequestException.class,
                () -> selectionRoundService.updateStatus(upcoming.getId(), SelectionRoundStatus.UPCOMING));

        SelectionRoundResponse cancellable =
                selectionRoundService.create(drive.getId(), roundRequest("Cancellable", 2));
        assertEquals(SelectionRoundStatus.CANCELLED,
                selectionRoundService.updateStatus(cancellable.getId(), SelectionRoundStatus.CANCELLED).getStatus());
        assertThrows(BadRequestException.class,
                () -> selectionRoundService.updateStatus(cancellable.getId(), SelectionRoundStatus.COMPLETED));
    }

    @Test
    void validatesRoundRequestFields() {
        SelectionRoundRequest request = roundRequest(" ", 0);
        request.setRoundType(null);
        request.setScheduledDateTime(null);
        request.setStatus(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void recordsPassedAndFailedResultsOnlyForShortlistedApplications() {
        PlacementDrive drive = createDrive("Round Result Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        Application passedApplication = createApplication(drive, "passed@example.com", ApplicationStatus.SHORTLISTED);
        Application failedApplication = createApplication(drive, "failed@example.com", ApplicationStatus.SHORTLISTED);

        RoundResultResponse passed = roundResultService.create(round.getId(),
                resultRequest(passedApplication.getId(), RoundResultStatus.PASSED));
        RoundResultResponse failed = roundResultService.create(round.getId(),
                resultRequest(failedApplication.getId(), RoundResultStatus.FAILED));

        assertEquals(RoundResultStatus.PASSED, passed.getResult());
        assertEquals(RoundResultStatus.FAILED, failed.getResult());
        assertNotNull(passed.getEvaluatedAt());
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(passedApplication.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsMissingRoundAndApplication() {
        assertThrows(ResourceNotFoundException.class,
                () -> roundResultService.create(Long.MAX_VALUE, resultRequest(1L, RoundResultStatus.PASSED)));
        PlacementDrive drive = createDrive("Missing Application Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        assertThrows(ResourceNotFoundException.class,
                () -> roundResultService.create(round.getId(),
                        resultRequest(Long.MAX_VALUE, RoundResultStatus.PASSED)));
    }

    @Test
    void rejectsApplicationFromDifferentDrive() {
        PlacementDrive drive = createDrive("Result Owning Drive");
        PlacementDrive otherDrive = createDrive("Result Other Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        Application application = createApplication(otherDrive, "different-drive@example.com",
                ApplicationStatus.SHORTLISTED);

        assertThrows(BadRequestException.class,
                () -> roundResultService.create(round.getId(),
                        resultRequest(application.getId(), RoundResultStatus.PASSED)));
    }

    @Test
    void rejectsNonShortlistedAndDuplicateResults() {
        PlacementDrive drive = createDrive("Shortlisted Rule Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        Application applied = createApplication(drive, "applied-result@example.com", ApplicationStatus.APPLIED);
        assertThrows(BadRequestException.class,
                () -> roundResultService.create(round.getId(),
                        resultRequest(applied.getId(), RoundResultStatus.PASSED)));

        Application shortlisted = createApplication(drive, "duplicate-result@example.com",
                ApplicationStatus.SHORTLISTED);
        roundResultService.create(round.getId(), resultRequest(shortlisted.getId(), RoundResultStatus.PASSED));
        assertThrows(BadRequestException.class,
                () -> roundResultService.create(round.getId(),
                        resultRequest(shortlisted.getId(), RoundResultStatus.FAILED)));
    }

    @Test
    void listsRoundResultsAndRestrictsStudentResultsToOwnApplication() {
        PlacementDrive drive = createDrive("Result Viewing Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        Application studentApp = createApplication(drive, "own-result@example.com", ApplicationStatus.SHORTLISTED);
        Application otherApp = createApplication(drive, "other-result@example.com", ApplicationStatus.SHORTLISTED);
        RoundResultResponse own = roundResultService.create(round.getId(),
                resultRequest(studentApp.getId(), RoundResultStatus.PASSED));
        roundResultService.create(round.getId(), resultRequest(otherApp.getId(), RoundResultStatus.FAILED));

        assertEquals(2, roundResultService.getForRound(round.getId()).size());
        assertEquals(List.of(own.getResultId()),
                roundResultService.getForStudentApplication(studentApp.getId(), studentApp.getStudent().getEmail())
                        .stream().map(RoundResultResponse::getResultId).toList());
        assertThrows(ResourceNotFoundException.class,
                () -> roundResultService.getForStudentApplication(
                        studentApp.getId(), otherApp.getStudent().getEmail()));
        assertThrows(ResourceNotFoundException.class, () -> roundResultService.getForRound(Long.MAX_VALUE));
    }

    @Test
    void updatesResultAndEvaluationTimestampWithoutChangingApplicationStatus() throws InterruptedException {
        PlacementDrive drive = createDrive("Result Update Drive");
        SelectionRound round = createRound(drive, "Technical", 1);
        Application application = createApplication(drive, "update-result@example.com", ApplicationStatus.SHORTLISTED);
        RoundResultResponse original = roundResultService.create(round.getId(),
                resultRequest(application.getId(), RoundResultStatus.PENDING));
        Thread.sleep(5);

        RoundResultUpdateRequest update = new RoundResultUpdateRequest();
        update.setResult(RoundResultStatus.PASSED);
        update.setScore(new BigDecimal("85.50"));
        update.setRemarks("Good performance");
        RoundResultResponse updated = roundResultService.update(original.getResultId(), update);

        assertEquals(RoundResultStatus.PASSED, updated.getResult());
        assertEquals(new BigDecimal("85.50"), updated.getScore());
        assertEquals("Good performance", updated.getRemarks());
        assertFalse(updated.getEvaluatedAt().isBefore(original.getEvaluatedAt()));
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(application.getId()).orElseThrow().getStatus());
        assertThrows(ResourceNotFoundException.class,
                () -> roundResultService.update(Long.MAX_VALUE, update));
    }

    @Test
    void rejectsNegativeScores() {
        RoundResultRequest request = resultRequest(1L, RoundResultStatus.PASSED);
        request.setScore(new BigDecimal("-0.01"));
        assertFalse(validator.validate(request).isEmpty());

        RoundResultUpdateRequest update = new RoundResultUpdateRequest();
        update.setResult(RoundResultStatus.PASSED);
        update.setScore(new BigDecimal("-1"));
        assertFalse(validator.validate(update).isEmpty());
    }

    private PlacementDrive createDrive(String title) {
        Company company = new Company();
        company.setCompanyName(title + " Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle(title);
        drive.setJobRole("Software Engineer");
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

    private SelectionRound createRound(PlacementDrive drive, String name, int order) {
        return selectionRoundRepository.saveAndFlush(roundEntity(drive, name, order));
    }

    private SelectionRound roundEntity(PlacementDrive drive, String name, int order) {
        SelectionRound round = new SelectionRound();
        round.setPlacementDrive(drive);
        round.setRoundName(name);
        round.setRoundType(SelectionRoundType.TECHNICAL);
        round.setRoundOrder(order);
        round.setScheduledDateTime(LocalDateTime.now().plusDays(1));
        round.setStatus(SelectionRoundStatus.UPCOMING);
        return round;
    }

    private SelectionRoundRequest roundRequest(String name, int order) {
        SelectionRoundRequest request = new SelectionRoundRequest();
        request.setRoundName(name);
        request.setRoundType(SelectionRoundType.TECHNICAL);
        request.setRoundOrder(order);
        request.setDescription("Selection round description");
        request.setScheduledDateTime(LocalDateTime.now().plusDays(1));
        request.setStatus(SelectionRoundStatus.UPCOMING);
        return request;
    }

    private Application createApplication(PlacementDrive drive, String email, ApplicationStatus status) {
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
        application.setStatus(status);
        return applicationRepository.saveAndFlush(application);
    }

    private RoundResultRequest resultRequest(Long applicationId, RoundResultStatus status) {
        RoundResultRequest request = new RoundResultRequest();
        request.setApplicationId(applicationId);
        request.setResult(status);
        request.setScore(new BigDecimal("85.50"));
        request.setRemarks("Round evaluation");
        return request;
    }
}
