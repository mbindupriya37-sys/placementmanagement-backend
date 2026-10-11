package com.example.backend.application;

import com.example.backend.application.dto.ApplicationResponse;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.service.ApplicationService;
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
import com.example.backend.notification.entity.Notification;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import com.example.backend.notification.service.NotificationService;
import com.example.backend.notification.controller.NotificationController;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationServiceTests {

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

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationController notificationController;

    @Test
    void createsApplicationWithAppliedStatusAndResponseDetails() {
        User student = createStudent("apply@example.com", "Computer Science", new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        ApplicationResponse response = applicationService.apply(student.getEmail(), drive.getId());

        assertNotNull(response.getApplicationId());
        assertEquals(drive.getId(), response.getDriveId());
        assertEquals("Software Developer Campus Drive", response.getDriveTitle());
        assertEquals("Application Test Company", response.getCompanyName());
        assertEquals(ApplicationStatus.APPLIED, response.getStatus());
        assertNotNull(response.getAppliedAt());
        assertNotNull(response.getUpdatedAt());
        var notification = notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                student.getEmail()).get(0);
        assertEquals(NotificationType.APPLICATION_SUBMITTED, notification.getNotificationType());
        assertEquals(NotificationReferenceType.APPLICATION, notification.getReferenceType());
        assertEquals(response.getApplicationId(), notification.getReferenceId());
    }

    @Test
    void notifiesTpoAdminsAndAggregatesApplicationsForTheSameDrive() {
        User firstTpo = createUser("application-activity-tpo-one@example.com", Role.TPO_ADMIN);
        User secondTpo = createUser("application-activity-tpo-two@example.com", Role.TPO_ADMIN);
        User firstStudent = createStudent("activity-student-one@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        User secondStudent = createStudent("activity-student-two@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        applicationService.apply(firstStudent.getEmail(), drive.getId());
        applicationService.apply(secondStudent.getEmail(), drive.getId());

        for (User tpo : List.of(firstTpo, secondTpo)) {
            List<Notification> tpoNotifications = notificationRepository
                    .findAllByRecipient_EmailOrderByCreatedAtDesc(tpo.getEmail()).stream()
                    .filter(notification -> notification.getNotificationType() == NotificationType.APPLICATION_ACTIVITY)
                    .toList();
            assertEquals(1, tpoNotifications.size());
            assertEquals(NotificationReferenceType.PLACEMENT_DRIVE, tpoNotifications.get(0).getReferenceType());
            assertEquals(drive.getId(), tpoNotifications.get(0).getReferenceId());
            assertEquals(2, tpoNotifications.get(0).getActivityCount());
            assertEquals("2 new applications received for " + drive.getDriveTitle() + ".",
                    tpoNotifications.get(0).getMessage());
        }

        var tpoAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                firstTpo.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_TPO_ADMIN")));
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(tpoAuthentication);
        SecurityContextHolder.setContext(securityContext);
        try {
            var apiNotifications = notificationController.getMyNotifications(tpoAuthentication).getData();
            assertEquals(1, apiNotifications.size());
            assertEquals(NotificationType.APPLICATION_ACTIVITY, apiNotifications.get(0).getNotificationType());
            assertEquals(drive.getId(), apiNotifications.get(0).getReferenceId());
            assertEquals(1L, notificationController.getCount(tpoAuthentication).getData().getUnreadCount());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(1, notificationRepository.countByRecipient_Email(firstStudent.getEmail()));
        assertEquals(NotificationType.APPLICATION_SUBMITTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(firstStudent.getEmail())
                        .get(0).getNotificationType());
    }

    @Test
    void createsSeparateTpoActivityNotificationsForDifferentDrives() {
        User tpo = createUser("application-activity-different-drives@example.com", Role.TPO_ADMIN);
        User firstStudent = createStudent("activity-drive-student-one@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        User secondStudent = createStudent("activity-drive-student-two@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive firstDrive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), "Software Developer Campus Drive", "First Application Test Company");
        PlacementDrive secondDrive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), "Data Analyst Campus Drive", "Second Application Test Company");

        applicationService.apply(firstStudent.getEmail(), firstDrive.getId());
        applicationService.apply(secondStudent.getEmail(), secondDrive.getId());

        List<Notification> tpoActivities = notificationRepository
                .findAllByRecipient_EmailOrderByCreatedAtDesc(tpo.getEmail()).stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.APPLICATION_ACTIVITY)
                .toList();
        assertEquals(2, tpoActivities.size());
        assertTrue(tpoActivities.stream()
                .anyMatch(notification -> notification.getReferenceId().equals(firstDrive.getId())));
        assertTrue(tpoActivities.stream()
                .anyMatch(notification -> notification.getReferenceId().equals(secondDrive.getId())));
    }

    @Test
    void newApplicationAfterTpoMarksActivityReadCreatesFreshActivity() {
        User tpo = createUser("application-activity-read@example.com", Role.TPO_ADMIN);
        User firstStudent = createStudent("activity-read-student-one@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        User secondStudent = createStudent("activity-read-student-two@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        applicationService.apply(firstStudent.getEmail(), drive.getId());
        var firstActivity = notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(tpo.getEmail())
                .stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.APPLICATION_ACTIVITY)
                .findFirst()
                .orElseThrow();
        notificationService.markAsRead(tpo.getEmail(), firstActivity.getId());

        applicationService.apply(secondStudent.getEmail(), drive.getId());

        List<Notification> tpoActivities = notificationRepository
                .findAllByRecipient_EmailOrderByCreatedAtDesc(tpo.getEmail()).stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.APPLICATION_ACTIVITY)
                .toList();
        assertEquals(2, tpoActivities.size());
        assertTrue(tpoActivities.stream().anyMatch(notification -> notification.isRead()
                && notification.getId().equals(firstActivity.getId())));
        assertTrue(tpoActivities.stream().anyMatch(notification -> !notification.isRead()
                && notification.getActivityCount() == 1
                && notification.getMessage().equals("1 new application received for " + drive.getDriveTitle() + ".")));
    }

    @Test
    void applicationActivityIsSentOnlyToTpoAdmins() {
        User recruiter = createUser("application-activity-recruiter@example.com", Role.COMPANY_RECRUITER);
        User coordinator = createUser("application-activity-coordinator@example.com", Role.DEPARTMENT_COORDINATOR);
        User student = createStudent("application-activity-only-student@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        applicationService.apply(student.getEmail(), drive.getId());

        assertEquals(0, notificationRepository.countByRecipient_Email(recruiter.getEmail()));
        assertEquals(0, notificationRepository.countByRecipient_Email(coordinator.getEmail()));
        assertEquals(1, notificationRepository.countByRecipient_Email(student.getEmail()));
    }

    @Test
    void rejectsIneligibleStudent() {
        User student = createStudent("ineligible@example.com", "Computer Science", new BigDecimal("6.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertTrue(exception.getMessage().startsWith("Student is not eligible:"));
        assertEquals(0, notificationRepository.countByRecipient_Email(student.getEmail()));
    }

    @Test
    void rejectsStudentWithoutProfile() {
        User student = createUser("no-profile-application@example.com");
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertEquals("Student profile not found", exception.getMessage());
    }

    @Test
    void rejectsMissingDrive() {
        User student = createStudent("missing-drive-application@example.com",
                "Computer Science", new BigDecimal("8.00"), 0, 2027);

        assertThrows(ResourceNotFoundException.class,
                () -> applicationService.apply(student.getEmail(), Long.MAX_VALUE));
    }

    @Test
    void rejectsDriveThatIsNotOpen() {
        User student = createStudent("closed-drive@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.DRAFT, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertEquals("Drive is not open", exception.getMessage());
    }

    @Test
    void rejectsApplicationBeforeStartDate() {
        User student = createStudent("early-application@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertEquals("Application period has not started", exception.getMessage());
    }

    @Test
    void rejectsApplicationAfterEndDate() {
        User student = createStudent("late-application@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(3),
                LocalDate.now().minusDays(1));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertEquals("Application period has ended", exception.getMessage());
    }

    @Test
    void preventsDuplicateApplications() {
        User student = createStudent("duplicate-application@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));
        applicationService.apply(student.getEmail(), drive.getId());

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> applicationService.apply(student.getEmail(), drive.getId()));

        assertEquals("Student has already applied", exception.getMessage());
    }

    @Test
    void studentCanRetrieveOnlyTheirOwnApplications() {
        User student = createStudent("my-applications@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        User otherStudent = createStudent("other-applications@example.com", "Computer Science",
                new BigDecimal("8.00"), 0, 2027);
        PlacementDrive drive = createDrive(PlacementDriveStatus.OPEN, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));

        ApplicationResponse application = applicationService.apply(student.getEmail(), drive.getId());

        assertEquals(1, applicationService.getMyApplications(student.getEmail()).size());
        assertEquals(0, applicationService.getMyApplications(otherStudent.getEmail()).size());
        assertEquals(application.getApplicationId(),
                applicationService.getMyApplication(student.getEmail(), application.getApplicationId())
                        .getApplicationId());
        assertThrows(ResourceNotFoundException.class,
                () -> applicationService.getMyApplication(otherStudent.getEmail(), application.getApplicationId()));
    }

    private User createStudent(String email, String branch, BigDecimal cgpa, int backlogs, int graduationYear) {
        User user = createUser(email);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setPhoneNumber("+1 555 123 4567");
        profile.setCollege("Example University");
        profile.setBranch(branch);
        profile.setGraduationYear(graduationYear);
        profile.setCgpa(cgpa);
        profile.setBacklogs(backlogs);
        profile.setSkills(List.of("Java"));
        profile.setResumeUrl("resumes/student.pdf");
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    private User createUser(String email) {
        return createUser(email, Role.STUDENT);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName(role == Role.TPO_ADMIN ? "Test TPO Admin" : "Test User");
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private PlacementDrive createDrive(PlacementDriveStatus status, LocalDate start, LocalDate end) {
        return createDrive(status, start, end, "Software Developer Campus Drive", "Application Test Company");
    }

    private PlacementDrive createDrive(
            PlacementDriveStatus status, LocalDate start, LocalDate end, String driveTitle, String companyName) {
        Company company = new Company();
        company.setCompanyName(companyName);
        company = companyRepository.saveAndFlush(company);

        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle(driveTitle);
        drive.setJobRole("Software Developer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.50"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science", "Information Technology"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("12.00"));
        drive.setApplicationStartDate(start);
        drive.setApplicationEndDate(end);
        drive.setDriveDate(end.plusDays(1));
        drive.setStatus(status);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
