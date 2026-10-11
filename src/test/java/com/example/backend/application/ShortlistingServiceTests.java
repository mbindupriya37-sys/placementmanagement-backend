package com.example.backend.application;

import com.example.backend.application.dto.BulkApplicationStatusRequest;
import com.example.backend.application.dto.ShortlistApplicationResponse;
import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.application.service.ShortlistingService;
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
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShortlistingServiceTests {

    @Autowired
    private ShortlistingService shortlistingService;

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

    @Test
    void listsApplicationsForRequestedDriveWithSafeStudentAndProfileDetails() {
        PlacementDrive drive = createDrive("Review Drive");
        Application application = createApplication(drive, "review@example.com", ApplicationStatus.APPLIED);

        List<ShortlistApplicationResponse> results = shortlistingService.getApplicationsForDrive(drive.getId());

        assertEquals(1, results.size());
        ShortlistApplicationResponse response = results.get(0);
        assertEquals(application.getId(), response.getApplicationId());
        assertEquals(application.getStudent().getId(), response.getStudentId());
        assertEquals("Student review@example.com", response.getStudentName());
        assertEquals("review@example.com", response.getStudentEmail());
        assertEquals("Computer Science", response.getBranch());
        assertEquals(2027, response.getGraduationYear());
        assertEquals(drive.getId(), response.getPlacementDriveId());
        assertEquals("Review Drive", response.getDriveTitle());
        assertEquals(ApplicationStatus.APPLIED, response.getApplicationStatus());
    }

    @Test
    void returnsExactApplicationStatusesForInterviewManagementFiltering() {
        PlacementDrive drive = createDrive("Interview Management Drive");
        Application shortlisted = createApplication(drive, "interview-shortlisted@example.com",
                ApplicationStatus.SHORTLISTED);
        Application selected = createApplication(drive, "interview-selected@example.com",
                ApplicationStatus.SELECTED);
        Application rejected = createApplication(drive, "interview-rejected@example.com",
                ApplicationStatus.REJECTED);

        List<ShortlistApplicationResponse> results = shortlistingService.getApplicationsForDrive(drive.getId());

        assertEquals(3, results.size());
        assertEquals(ApplicationStatus.SHORTLISTED, findResponse(results, shortlisted.getId()).getApplicationStatus());
        assertEquals(ApplicationStatus.SELECTED, findResponse(results, selected.getId()).getApplicationStatus());
        assertEquals(ApplicationStatus.REJECTED, findResponse(results, rejected.getId()).getApplicationStatus());
    }

    private ShortlistApplicationResponse findResponse(List<ShortlistApplicationResponse> responses, Long applicationId) {
        return responses.stream()
                .filter(response -> response.getApplicationId().equals(applicationId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void rejectsListingApplicationsForMissingDrive() {
        assertThrows(ResourceNotFoundException.class,
                () -> shortlistingService.getApplicationsForDrive(Long.MAX_VALUE));
    }

    @Test
    void shortlistsAndRejectsAppliedApplications() {
        PlacementDrive drive = createDrive("Decision Drive");
        Application toShortlist = createApplication(drive, "shortlist@example.com", ApplicationStatus.APPLIED);
        Application toReject = createApplication(drive, "reject@example.com", ApplicationStatus.APPLIED);

        ShortlistApplicationResponse shortlisted = shortlistingService.shortlist(toShortlist.getId());
        ShortlistApplicationResponse rejected = shortlistingService.reject(toReject.getId());

        assertEquals(ApplicationStatus.SHORTLISTED, shortlisted.getApplicationStatus());
        assertEquals(ApplicationStatus.REJECTED, rejected.getApplicationStatus());
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(toShortlist.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.REJECTED,
                applicationRepository.findById(toReject.getId()).orElseThrow().getStatus());
        assertEquals(NotificationType.APPLICATION_SHORTLISTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        toShortlist.getStudent().getEmail()).get(0).getNotificationType());
        assertEquals(NotificationType.APPLICATION_REJECTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        toReject.getStudent().getEmail()).get(0).getNotificationType());
    }

    @Test
    void rejectsInvalidSingleStatusTransitions() {
        PlacementDrive drive = createDrive("Invalid Transitions Drive");
        Application shortlisted = createApplication(drive, "already-shortlisted@example.com",
                ApplicationStatus.SHORTLISTED);
        Application rejected = createApplication(drive, "already-rejected@example.com",
                ApplicationStatus.REJECTED);

        assertThrows(BadRequestException.class, () -> shortlistingService.shortlist(shortlisted.getId()));
        assertThrows(BadRequestException.class, () -> shortlistingService.shortlist(rejected.getId()));
        assertThrows(BadRequestException.class, () -> shortlistingService.reject(shortlisted.getId()));
        assertThrows(BadRequestException.class, () -> shortlistingService.reject(rejected.getId()));
    }

    @Test
    void returnsNotFoundForMissingApplication() {
        assertThrows(ResourceNotFoundException.class,
                () -> shortlistingService.shortlist(Long.MAX_VALUE));
    }

    @Test
    void bulkShortlistsAndRejectsApplications() {
        PlacementDrive shortlistDrive = createDrive("Bulk Shortlist Drive");
        Application first = createApplication(shortlistDrive, "bulk-shortlist-1@example.com",
                ApplicationStatus.APPLIED);
        Application second = createApplication(shortlistDrive, "bulk-shortlist-2@example.com",
                ApplicationStatus.APPLIED);

        List<ShortlistApplicationResponse> shortlisted = shortlistingService.bulkShortlist(shortlistDrive.getId(),
                request(first.getId(), second.getId()));
        assertEquals(List.of(first.getId(), second.getId()),
                shortlisted.stream().map(ShortlistApplicationResponse::getApplicationId).toList());
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED,
                applicationRepository.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(1, notificationRepository.countByRecipient_Email(first.getStudent().getEmail()));
        assertEquals(1, notificationRepository.countByRecipient_Email(second.getStudent().getEmail()));

        PlacementDrive rejectDrive = createDrive("Bulk Reject Drive");
        Application third = createApplication(rejectDrive, "bulk-reject-1@example.com", ApplicationStatus.APPLIED);
        Application fourth = createApplication(rejectDrive, "bulk-reject-2@example.com", ApplicationStatus.APPLIED);
        List<ShortlistApplicationResponse> rejected = shortlistingService.bulkReject(rejectDrive.getId(),
                request(third.getId(), fourth.getId()));
        assertEquals(2, rejected.size());
        assertEquals(ApplicationStatus.REJECTED,
                applicationRepository.findById(third.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.REJECTED,
                applicationRepository.findById(fourth.getId()).orElseThrow().getStatus());
        assertEquals(NotificationType.APPLICATION_REJECTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        third.getStudent().getEmail()).get(0).getNotificationType());
    }

    @Test
    void bulkOperationsRejectApplicationsFromAnotherDriveWithoutPartialUpdates() {
        PlacementDrive requestedDrive = createDrive("Requested Drive");
        PlacementDrive otherDrive = createDrive("Other Drive");
        Application valid = createApplication(requestedDrive, "bulk-valid@example.com", ApplicationStatus.APPLIED);
        Application wrongDrive = createApplication(otherDrive, "bulk-wrong-drive@example.com",
                ApplicationStatus.APPLIED);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> shortlistingService.bulkShortlist(requestedDrive.getId(),
                        request(valid.getId(), wrongDrive.getId())));

        assertEquals(ApplicationStatus.APPLIED,
                applicationRepository.findById(valid.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.APPLIED,
                applicationRepository.findById(wrongDrive.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("does not belong"));
    }

    @Test
    void bulkOperationsRejectMissingIdsAndInvalidStatusesWithoutPartialUpdates() {
        PlacementDrive drive = createDrive("Bulk Validation Drive");
        Application valid = createApplication(drive, "bulk-validation-valid@example.com", ApplicationStatus.APPLIED);

        assertThrows(ResourceNotFoundException.class,
                () -> shortlistingService.bulkShortlist(drive.getId(),
                        request(valid.getId(), Long.MAX_VALUE)));
        assertEquals(ApplicationStatus.APPLIED,
                applicationRepository.findById(valid.getId()).orElseThrow().getStatus());

        Application alreadyRejected = createApplication(drive, "bulk-validation-rejected@example.com",
                ApplicationStatus.REJECTED);
        assertThrows(BadRequestException.class,
                () -> shortlistingService.bulkReject(drive.getId(),
                        request(valid.getId(), alreadyRejected.getId())));
        assertEquals(ApplicationStatus.APPLIED,
                applicationRepository.findById(valid.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.REJECTED,
                applicationRepository.findById(alreadyRejected.getId()).orElseThrow().getStatus());
    }

    @Test
    void bulkOperationRequiresDriveAndUniqueApplicationIds() {
        BulkApplicationStatusRequest empty = request();
        assertThrows(ResourceNotFoundException.class,
                () -> shortlistingService.bulkShortlist(Long.MAX_VALUE, empty));

        PlacementDrive drive = createDrive("Duplicate IDs Drive");
        Application application = createApplication(drive, "duplicate-id@example.com", ApplicationStatus.APPLIED);
        assertThrows(BadRequestException.class,
                () -> shortlistingService.bulkShortlist(drive.getId(),
                        request(application.getId(), application.getId())));
        assertEquals(ApplicationStatus.APPLIED,
                applicationRepository.findById(application.getId()).orElseThrow().getStatus());
    }

    private BulkApplicationStatusRequest request(Long... ids) {
        BulkApplicationStatusRequest request = new BulkApplicationStatusRequest();
        request.setApplicationIds(List.of(ids));
        return request;
    }

    private Application createApplication(
            PlacementDrive drive, String email, ApplicationStatus status) {
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
        drive.setApplicationStartDate(LocalDate.now().minusDays(1));
        drive.setApplicationEndDate(LocalDate.now().plusDays(1));
        drive.setDriveDate(LocalDate.now().plusDays(2));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
