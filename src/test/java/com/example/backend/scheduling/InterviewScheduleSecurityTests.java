package com.example.backend.scheduling;

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
import com.example.backend.scheduling.controller.InterviewScheduleController;
import com.example.backend.scheduling.dto.InterviewScheduleRequest;
import com.example.backend.scheduling.entity.InterviewMode;
import com.example.backend.scheduling.entity.InterviewSchedule;
import com.example.backend.scheduling.entity.InterviewScheduleStatus;
import com.example.backend.scheduling.repository.InterviewScheduleRepository;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.entity.SelectionRoundType;
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
class InterviewScheduleSecurityTests {

    @Autowired
    private InterviewScheduleController interviewScheduleController;

    @Autowired
    private InterviewScheduleRepository interviewScheduleRepository;

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

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanCreateAndRetrieveSchedule() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application application = createApplication(drive, "tpo-schedule@example.com");

        var created = interviewScheduleController.create(round.getId(), request(application.getId())).getData();

        assertEquals(application.getId(), created.getApplicationId());
        assertEquals(created.getScheduleId(),
                interviewScheduleController.getById(created.getScheduleId(),
                        SecurityContextHolder.getContext().getAuthentication()).getData().getScheduleId());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotCreateOrListSchedulesForRound() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);

        assertThrows(AccessDeniedException.class,
                () -> interviewScheduleController.create(round.getId(), request(1L)));
        assertThrows(AccessDeniedException.class,
                () -> interviewScheduleController.getForRound(round.getId(),
                        SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(username = "schedule-recruiter@example.com", roles = "COMPANY_RECRUITER")
    void recruiterCanViewRoundSchedulesAndCannotModifySchedule() {
        PlacementDrive drive = createDrive();
        User recruiter = new User();
        recruiter.setEmail("schedule-recruiter@example.com");
        recruiter.setName("Schedule Recruiter");
        recruiter.setPassword("encoded-password");
        recruiter.setRole(Role.COMPANY_RECRUITER);
        recruiter.setCompany(drive.getCompany());
        userRepository.saveAndFlush(recruiter);
        SelectionRound round = createRound(drive);
        Application application = createApplication(drive, "recruiter-schedule@example.com");
        InterviewSchedule schedule = createSchedule(round, application);

        assertEquals(1, interviewScheduleController.getForRound(round.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
        assertThrows(AccessDeniedException.class,
                () -> interviewScheduleController.cancel(schedule.getId()));
    }

    @Test
    @WithMockUser(roles = "DEPARTMENT_COORDINATOR")
    void departmentCoordinatorCanViewRoundSchedules() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application application = createApplication(drive, "coordinator-schedule@example.com");
        createSchedule(round, application);

        assertEquals(1, interviewScheduleController.getForRound(round.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
    }

    @Test
    @WithMockUser(username = "schedule-owner@example.com", roles = "STUDENT")
    void studentCanOnlyRetrieveSchedulesForOwnApplication() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application ownApplication = createApplication(drive, "schedule-owner@example.com");
        Application otherApplication = createApplication(drive, "schedule-other-owner@example.com");
        InterviewSchedule ownSchedule = createSchedule(round, ownApplication);
        InterviewSchedule otherSchedule = createSchedule(round, otherApplication);

        assertEquals(ownSchedule.getId(), interviewScheduleController.getById(ownSchedule.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().getScheduleId());
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleController.getById(otherSchedule.getId(),
                        SecurityContextHolder.getContext().getAuthentication()));
        assertEquals(1, interviewScheduleController.getForStudentApplication(ownApplication.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().size());
        assertThrows(ResourceNotFoundException.class,
                () -> interviewScheduleController.getForStudentApplication(otherApplication.getId(),
                        SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void tpoCanCancelAndChangeScheduleStatus() {
        PlacementDrive drive = createDrive();
        SelectionRound round = createRound(drive);
        Application application = createApplication(drive, "tpo-status@example.com");
        InterviewSchedule schedule = createSchedule(round, application);

        var cancelled = interviewScheduleController.cancel(schedule.getId()).getData();
        assertEquals(InterviewScheduleStatus.CANCELLED, cancelled.getStatus());
    }

    private PlacementDrive createDrive() {
        Company company = new Company();
        company.setCompanyName("Schedule Security Company");
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Schedule Security Drive");
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

    private InterviewSchedule createSchedule(SelectionRound round, Application application) {
        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setApplication(application);
        schedule.setSelectionRound(round);
        schedule.setScheduledStartTime(LocalDateTime.now().plusDays(2));
        schedule.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusMinutes(30));
        schedule.setMode(InterviewMode.ONLINE);
        schedule.setMeetingLink("https://example.com/meeting");
        schedule.setStatus(InterviewScheduleStatus.SCHEDULED);
        return interviewScheduleRepository.saveAndFlush(schedule);
    }

    private InterviewScheduleRequest request(Long applicationId) {
        InterviewScheduleRequest request = new InterviewScheduleRequest();
        request.setApplicationId(applicationId);
        request.setScheduledStartTime(LocalDateTime.now().plusDays(2));
        request.setScheduledEndTime(request.getScheduledStartTime().plusMinutes(30));
        request.setMode(InterviewMode.ONLINE);
        request.setMeetingLink("https://example.com/meeting");
        return request;
    }
}
