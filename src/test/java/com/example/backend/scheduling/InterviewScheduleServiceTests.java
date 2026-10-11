package com.example.backend.scheduling;

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
import com.example.backend.scheduling.dto.InterviewScheduleRequest;
import com.example.backend.scheduling.dto.InterviewScheduleResponse;
import com.example.backend.scheduling.dto.InterviewScheduleUpdateRequest;
import com.example.backend.scheduling.entity.InterviewMode;
import com.example.backend.scheduling.entity.InterviewScheduleStatus;
import com.example.backend.scheduling.service.InterviewScheduleService;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;
import com.example.backend.selection.repository.SelectionRoundRepository;
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
class InterviewScheduleServiceTests {

    @Autowired
    private InterviewScheduleService interviewScheduleService;

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
    void createsOnlineAndOfflineSchedules() {
        PlacementDrive drive = createDrive("Modes Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Modes Round");
        Application onlineApplication = createApplication(drive, "online@example.com", ApplicationStatus.SHORTLISTED);
        Application offlineApplication = createApplication(drive, "offline@example.com", ApplicationStatus.SHORTLISTED);

        InterviewScheduleResponse online = interviewScheduleService.create(round.getId(),
                request(onlineApplication.getId(), InterviewMode.ONLINE, 1, "interviewer@example.com"));
        InterviewScheduleRequest offlineRequest = request(
                offlineApplication.getId(), InterviewMode.OFFLINE, 2, null);
        offlineRequest.setMeetingLink(null);
        offlineRequest.setVenue("Room 201");
        InterviewScheduleResponse offline = interviewScheduleService.create(round.getId(), offlineRequest);

        assertEquals(InterviewScheduleStatus.SCHEDULED, online.getStatus());
        assertEquals("https://example.com/meeting", online.getMeetingLink());
        assertEquals(InterviewMode.OFFLINE, offline.getMode());
        assertEquals("Room 201", offline.getVenue());
        assertNotNull(online.getCreatedAt());
        var onlineNotification = notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                onlineApplication.getStudent().getEmail()).get(0);
        assertEquals(NotificationType.INTERVIEW_SCHEDULED, onlineNotification.getNotificationType());
        assertEquals(NotificationReferenceType.INTERVIEW_SCHEDULE, onlineNotification.getReferenceType());
        assertEquals(online.getScheduleId(), onlineNotification.getReferenceId());
    }

    @Test
    void rejectsMissingApplicationOrRoundAndMismatchedDrive() {
        PlacementDrive drive = createDrive("Required Relation Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Required Relation Round");
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.create(Long.MAX_VALUE, request(1L, InterviewMode.ONLINE, 1, null)));
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.create(round.getId(),
                        request(Long.MAX_VALUE, InterviewMode.ONLINE, 1, null)));

        PlacementDrive otherDrive = createDrive("Other Relation Drive");
        Application application = createApplication(otherDrive, "wrong-drive@example.com",
                ApplicationStatus.SHORTLISTED);
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(round.getId(),
                        request(application.getId(), InterviewMode.ONLINE, 1, null)));
    }

    @Test
    void rejectsNonShortlistedApplicationsAndClosedRounds() {
        PlacementDrive drive = createDrive("Eligibility Schedule Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Eligibility Schedule Round");
        Application rejected = createApplication(drive, "rejected-applicant@example.com", ApplicationStatus.REJECTED);
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(round.getId(),
                        request(rejected.getId(), InterviewMode.ONLINE, 1, null)));

        SelectionRound cancelled = createRound(drive, SelectionRoundStatus.CANCELLED, "Cancelled Round");
        Application shortlisted = createApplication(drive, "cancelled-round@example.com",
                ApplicationStatus.SHORTLISTED);
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(cancelled.getId(),
                        request(shortlisted.getId(), InterviewMode.ONLINE, 1, null)));

        SelectionRound completed = createRound(drive, SelectionRoundStatus.COMPLETED, "Completed Round");
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(completed.getId(),
                        request(shortlisted.getId(), InterviewMode.ONLINE, 1, null)));
    }

    @Test
    void validatesTimesAndModeRequirements() {
        PlacementDrive drive = createDrive("Input Validation Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.ONGOING, "Input Validation Round");
        Application first = createApplication(drive, "invalid-time@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleRequest invalidOrder = request(first.getId(), InterviewMode.ONLINE, 1, null);
        invalidOrder.setScheduledEndTime(invalidOrder.getScheduledStartTime());
        assertThrows(BadRequestException.class, () -> interviewScheduleService.create(round.getId(), invalidOrder));

        InterviewScheduleRequest past = request(first.getId(), InterviewMode.ONLINE, 1, null);
        past.setScheduledStartTime(LocalDateTime.now().minusMinutes(1));
        past.setScheduledEndTime(LocalDateTime.now().plusMinutes(20));
        assertThrows(BadRequestException.class, () -> interviewScheduleService.create(round.getId(), past));

        InterviewScheduleRequest noLink = request(first.getId(), InterviewMode.ONLINE, 1, null);
        noLink.setMeetingLink(null);
        assertThrows(BadRequestException.class, () -> interviewScheduleService.create(round.getId(), noLink));

        InterviewScheduleRequest noVenue = request(first.getId(), InterviewMode.OFFLINE, 1, null);
        noVenue.setVenue(" ");
        assertThrows(BadRequestException.class, () -> interviewScheduleService.create(round.getId(), noVenue));

        InterviewScheduleRequest invalidEmail = request(first.getId(), InterviewMode.ONLINE, 1, null);
        invalidEmail.setInterviewerEmail("not-an-email");
        assertFalse(validator.validate(invalidEmail).isEmpty());
    }

    @Test
    void rejectsDuplicateActiveScheduleButAllowsReplacementAfterCancellation() {
        PlacementDrive drive = createDrive("Duplicate Schedule Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Duplicate Schedule Round");
        Application application = createApplication(drive, "duplicate-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse schedule = interviewScheduleService.create(round.getId(),
                request(application.getId(), InterviewMode.ONLINE, 1, null));
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(round.getId(),
                        request(application.getId(), InterviewMode.ONLINE, 2, null)));

        interviewScheduleService.cancel(schedule.getScheduleId());
        InterviewScheduleResponse replacement = interviewScheduleService.create(round.getId(),
                request(application.getId(), InterviewMode.ONLINE, 2, null));
        assertNotNull(replacement.getScheduleId());
    }

    @Test
    void preventsInterviewerOverlapsButAllowsNonOverlappingSchedules() {
        PlacementDrive drive = createDrive("Interviewer Conflict Drive");
        SelectionRound firstRound = createRound(drive, SelectionRoundStatus.UPCOMING, "First Interview Round");
        SelectionRound secondRound = createRound(drive, SelectionRoundStatus.UPCOMING, "Second Interview Round");
        Application first = createApplication(drive, "first-interview@example.com", ApplicationStatus.SHORTLISTED);
        Application second = createApplication(drive, "second-interview@example.com", ApplicationStatus.SHORTLISTED);

        interviewScheduleService.create(firstRound.getId(),
                request(first.getId(), InterviewMode.ONLINE, 1, "panel@example.com"));
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(secondRound.getId(),
                        request(second.getId(), InterviewMode.ONLINE, 1, "PANEL@example.com")));

        interviewScheduleService.create(secondRound.getId(),
                request(second.getId(), InterviewMode.ONLINE, 3, "panel@example.com"));
    }

    @Test
    void retrievesScheduleAndScopesStudentReadsToOwner() {
        PlacementDrive drive = createDrive("Schedule Ownership Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Ownership Round");
        Application owner = createApplication(drive, "schedule-owner@example.com", ApplicationStatus.SHORTLISTED);
        Application other = createApplication(drive, "schedule-other@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse ownSchedule = interviewScheduleService.create(round.getId(),
                request(owner.getId(), InterviewMode.ONLINE, 1, null));
        InterviewScheduleResponse otherSchedule = interviewScheduleService.create(round.getId(),
                request(other.getId(), InterviewMode.ONLINE, 2, null));

        assertEquals(ownSchedule.getScheduleId(), interviewScheduleService
                .getByIdForStudent(ownSchedule.getScheduleId(), owner.getStudent().getEmail()).getScheduleId());
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.getByIdForStudent(
                        otherSchedule.getScheduleId(), owner.getStudent().getEmail()));
        assertEquals(ownSchedule.getScheduleId(), interviewScheduleService
                .getForStudentApplication(owner.getId(), owner.getStudent().getEmail()).get(0).getScheduleId());
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.getForStudentApplication(
                        other.getId(), owner.getStudent().getEmail()));
        assertEquals(2, interviewScheduleService.getForRound(round.getId()).size());
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.getById(Long.MAX_VALUE));
    }

    @Test
    void rejectsAssessmentRoundsAndSupportsInterviewRoundTypes() {
        PlacementDrive drive = createDrive("Interview Type Drive");
        Application aptitudeApplication = createApplication(drive, "aptitude-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        Application codingApplication = createApplication(drive, "coding-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        SelectionRound aptitude = createRound(drive, SelectionRoundStatus.UPCOMING, "Assessment Aptitude",
                SelectionRoundType.APTITUDE);
        SelectionRound coding = createRound(drive, SelectionRoundStatus.UPCOMING, "Assessment Coding",
                SelectionRoundType.CODING);

        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(aptitude.getId(),
                        request(aptitudeApplication.getId(), InterviewMode.ONLINE, 1, null)));
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.create(coding.getId(),
                        request(codingApplication.getId(), InterviewMode.ONLINE, 1, null)));

        Application technicalApplication = createApplication(drive, "technical-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        Application hrApplication = createApplication(drive, "hr-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        SelectionRound technical = createRound(drive, SelectionRoundStatus.UPCOMING, "Technical Interview",
                SelectionRoundType.TECHNICAL);
        SelectionRound hr = createRound(drive, SelectionRoundStatus.UPCOMING, "HR Interview",
                SelectionRoundType.HR);

        assertEquals(InterviewScheduleStatus.SCHEDULED,
                interviewScheduleService.create(technical.getId(),
                        request(technicalApplication.getId(), InterviewMode.ONLINE, 1, null)).getStatus());
        assertEquals(InterviewScheduleStatus.SCHEDULED,
                interviewScheduleService.create(hr.getId(),
                        request(hrApplication.getId(), InterviewMode.ONLINE, 1, null)).getStatus());
    }

    @Test
    void selectedApplicationKeepsExistingScheduleVisibleToItsStudent() {
        PlacementDrive drive = createDrive("Selected Candidate Schedule Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Selection Interview Round");
        Application application = createApplication(drive, "selected-schedule@example.com",
                ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse schedule = interviewScheduleService.create(round.getId(),
                request(application.getId(), InterviewMode.ONLINE, 1, null));

        application.setStatus(ApplicationStatus.SELECTED);
        applicationRepository.saveAndFlush(application);

        assertEquals(schedule.getScheduleId(), interviewScheduleService
                .getForStudentApplication(application.getId(), application.getStudent().getEmail())
                .get(0).getScheduleId());
        assertEquals(schedule.getScheduleId(), interviewScheduleService
                .getByIdForStudent(schedule.getScheduleId(), application.getStudent().getEmail())
                .getScheduleId());
    }

    @Test
    void reschedulesAndRejectsCompletedOrCancelledSchedules() {
        PlacementDrive drive = createDrive("Reschedule Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Reschedule Round");
        Application application = createApplication(drive, "reschedule@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse created = interviewScheduleService.create(round.getId(),
                request(application.getId(), InterviewMode.ONLINE, 1, null));
        InterviewScheduleResponse updated = interviewScheduleService.reschedule(created.getScheduleId(),
                updateRequest(2, null));
        assertEquals(InterviewScheduleStatus.RESCHEDULED, updated.getStatus());
        assertEquals(NotificationType.INTERVIEW_RESCHEDULED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        application.getStudent().getEmail()).get(0).getNotificationType());

        interviewScheduleService.updateStatus(created.getScheduleId(), InterviewScheduleStatus.COMPLETED);
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.reschedule(created.getScheduleId(), updateRequest(3, null)));

        Application another = createApplication(drive, "cancel-reschedule@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse cancelled = interviewScheduleService.create(round.getId(),
                request(another.getId(), InterviewMode.ONLINE, 4, null));
        interviewScheduleService.cancel(cancelled.getScheduleId());
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.reschedule(cancelled.getScheduleId(), updateRequest(5, null)));
    }

    @Test
    void cancelsAndRejectsInvalidCancelOrStatusTransitions() {
        PlacementDrive drive = createDrive("Cancel Transition Drive");
        SelectionRound round = createRound(drive, SelectionRoundStatus.UPCOMING, "Cancel Transition Round");
        Application app = createApplication(drive, "cancel-transition@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse schedule = interviewScheduleService.create(round.getId(),
                request(app.getId(), InterviewMode.ONLINE, 1, null));
        assertEquals(InterviewScheduleStatus.CANCELLED,
                interviewScheduleService.cancel(schedule.getScheduleId()).getStatus());
        assertEquals(NotificationType.INTERVIEW_CANCELLED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        app.getStudent().getEmail()).get(0).getNotificationType());
        assertThrows(BadRequestException.class, () -> interviewScheduleService.cancel(schedule.getScheduleId()));
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.updateStatus(schedule.getScheduleId(),
                        InterviewScheduleStatus.SCHEDULED));
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleService.cancel(Long.MAX_VALUE));

        Application completedApp = createApplication(
                drive, "completed-transition@example.com", ApplicationStatus.SHORTLISTED);
        InterviewScheduleResponse completed = interviewScheduleService.create(round.getId(),
                request(completedApp.getId(), InterviewMode.ONLINE, 2, null));
        interviewScheduleService.updateStatus(completed.getScheduleId(), InterviewScheduleStatus.COMPLETED);
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.cancel(completed.getScheduleId()));
        assertThrows(BadRequestException.class,
                () -> interviewScheduleService.updateStatus(completed.getScheduleId(),
                        InterviewScheduleStatus.RESCHEDULED));
    }

    private PlacementDrive createDrive(String name) {
        Company company = new Company();
        company.setCompanyName(name + " Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle(name);
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

    private SelectionRound createRound(PlacementDrive drive, SelectionRoundStatus status, String name) {
        return createRound(drive, status, name, SelectionRoundType.TECHNICAL);
    }

    private SelectionRound createRound(PlacementDrive drive, SelectionRoundStatus status, String name,
                                       SelectionRoundType type) {
        SelectionRound round = new SelectionRound();
        round.setPlacementDrive(drive);
        round.setRoundName(name);
        round.setRoundType(type);
        round.setRoundOrder(selectionRoundRepository.findAll().size() + 1);
        round.setScheduledDateTime(LocalDateTime.now().plusDays(1));
        round.setStatus(status);
        return selectionRoundRepository.saveAndFlush(round);
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

    private InterviewScheduleRequest request(
            Long applicationId, InterviewMode mode, int slot, String interviewerEmail) {
        InterviewScheduleRequest request = new InterviewScheduleRequest();
        request.setApplicationId(applicationId);
        request.setScheduledStartTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0));
        request.setScheduledStartTime(request.getScheduledStartTime().plusHours(slot - 1L));
        request.setScheduledEndTime(request.getScheduledStartTime().plusMinutes(30));
        request.setMode(mode);
        request.setMeetingLink(mode == InterviewMode.ONLINE ? "https://example.com/meeting" : null);
        request.setVenue(mode == InterviewMode.OFFLINE ? "Room 201" : null);
        request.setInterviewerName("Panel Member");
        request.setInterviewerEmail(interviewerEmail);
        request.setNotes("Interview notes");
        return request;
    }

    private InterviewScheduleUpdateRequest updateRequest(int slot, String interviewerEmail) {
        InterviewScheduleUpdateRequest request = new InterviewScheduleUpdateRequest();
        request.setScheduledStartTime(LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0)
                .plusHours(slot - 1L));
        request.setScheduledEndTime(request.getScheduledStartTime().plusMinutes(30));
        request.setMode(InterviewMode.ONLINE);
        request.setMeetingLink("https://example.com/meeting");
        request.setInterviewerName("Updated Panel Member");
        request.setInterviewerEmail(interviewerEmail);
        request.setNotes("Updated notes");
        return request;
    }
}
