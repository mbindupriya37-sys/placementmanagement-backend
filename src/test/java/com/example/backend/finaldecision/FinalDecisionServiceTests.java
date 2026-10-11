package com.example.backend.finaldecision;

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
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import com.example.backend.finaldecision.dto.FinalDecisionRequest;
import com.example.backend.finaldecision.dto.FinalDecisionResponse;
import com.example.backend.finaldecision.entity.FinalDecision;
import com.example.backend.finaldecision.repository.FinalDecisionRecordRepository;
import com.example.backend.finaldecision.service.FinalDecisionService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
class FinalDecisionServiceTests {

    @Autowired
    private FinalDecisionService finalDecisionService;

    @Autowired
    private FinalDecisionRecordRepository finalDecisionRecordRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlacementDriveRepository placementDriveRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private Validator validator;

    @Test
    void selectsAndRejectsShortlistedApplicationsAndAuditsDecider() {
        User tpo = createUser("tpo-final@example.com", Role.TPO_ADMIN);
        Application selectedApplication = createApplication("selected@example.com", ApplicationStatus.SHORTLISTED);
        Application rejectedApplication = createApplication("rejected@example.com", ApplicationStatus.SHORTLISTED);

        FinalDecisionResponse selected = finalDecisionService.decide(selectedApplication.getId(), tpo.getEmail(),
                request(FinalDecision.SELECTED, "Selected after interviews"));
        FinalDecisionResponse rejected = finalDecisionService.decide(rejectedApplication.getId(), tpo.getEmail(),
                request(FinalDecision.REJECTED, "Not selected"));

        assertEquals(FinalDecision.SELECTED, selected.getDecision());
        FinalDecisionResponse savedSelected = finalDecisionService.getForApplication(
                selectedApplication.getId(), "ignored@example.com", true);
        assertEquals(FinalDecision.SELECTED, savedSelected.getDecision());
        assertEquals("Selected after interviews", savedSelected.getRemarks());
        assertEquals(ApplicationStatus.SELECTED,
                applicationRepository.findById(selectedApplication.getId()).orElseThrow().getStatus());
        assertEquals(tpo.getEmail(), selected.getDecidedByEmail());
        assertNotNull(selected.getDecidedAt());
        assertEquals(FinalDecision.REJECTED, rejected.getDecision());
        assertEquals(ApplicationStatus.REJECTED,
                applicationRepository.findById(rejectedApplication.getId()).orElseThrow().getStatus());
        assertEquals(NotificationType.FINAL_DECISION,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        selectedApplication.getStudent().getEmail()).get(0).getNotificationType());
        assertEquals("Congratulations! You are selected",
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        selectedApplication.getStudent().getEmail()).get(0).getTitle());
        assertEquals(NotificationType.FINAL_DECISION,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        rejectedApplication.getStudent().getEmail()).get(0).getNotificationType());
    }

    @Test
    void rejectsNonShortlistedAndRepeatedFinalDecisions() {
        User tpo = createUser("tpo-transitions@example.com", Role.TPO_ADMIN);
        for (ApplicationStatus status : List.of(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN, ApplicationStatus.SELECTED)) {
            Application application = createApplication(status.name().toLowerCase() + "@example.com", status);
            assertThrows(BadRequestException.class, () -> finalDecisionService.decide(
                    application.getId(), tpo.getEmail(), request(FinalDecision.SELECTED, null)));
        }

        Application application = createApplication("duplicate-decision@example.com", ApplicationStatus.SHORTLISTED);
        finalDecisionService.decide(application.getId(), tpo.getEmail(), request(FinalDecision.SELECTED, null));
        assertThrows(BadRequestException.class, () -> finalDecisionService.decide(
                application.getId(), tpo.getEmail(), request(FinalDecision.REJECTED, null)));
        assertEquals(1, finalDecisionRecordRepository.count());
    }

    @Test
    void viewsOwnDecisionListsDriveDecisionsAndHidesOtherStudentApplication() {
        User tpo = createUser("tpo-list-decisions@example.com", Role.TPO_ADMIN);
        Application first = createApplication("decision-owner@example.com", ApplicationStatus.SHORTLISTED);
        Application second = createApplication("decision-other@example.com", ApplicationStatus.SHORTLISTED);
        finalDecisionService.decide(first.getId(), tpo.getEmail(), request(FinalDecision.SELECTED, null));
        finalDecisionService.decide(second.getId(), tpo.getEmail(), request(FinalDecision.REJECTED, null));

        assertEquals(first.getId(), finalDecisionService.getForApplication(
                first.getId(), first.getStudent().getEmail(), false).getApplicationId());
        assertThrows(ResourceNotFoundException.class, () -> finalDecisionService.getForApplication(
                first.getId(), second.getStudent().getEmail(), false));
        assertEquals(1, finalDecisionService.getForDrive(first.getPlacementDrive().getId()).size());
        assertEquals(0, finalDecisionService.getForDrive(
                createDrive("Empty Decision Drive").getId()).size());
        assertThrows(ResourceNotFoundException.class, () -> finalDecisionService.getForDrive(Long.MAX_VALUE));
        assertEquals(first.getId(), finalDecisionService.getForApplication(
                first.getId(), "ignored@example.com", true).getApplicationId());
    }

    @Test
    void reportsNoDecisionForApplicationUntilOneIsRecorded() {
        Application application = createApplication("no-final-decision@example.com", ApplicationStatus.SHORTLISTED);

        assertThrows(ResourceNotFoundException.class, () -> finalDecisionService.getForApplication(
                application.getId(), "tpo@example.com", true));
        assertEquals(0, finalDecisionRecordRepository.count());
    }

    @Test
    void listsPendingShortlistedApplicationsAlongsideRecordedDecisions() {
        User tpo = createUser("tpo-pending-list@example.com", Role.TPO_ADMIN);
        PlacementDrive drive = createDrive("Pending final decision drive");
        Application pending = createApplication(drive, "pending@example.com", ApplicationStatus.SHORTLISTED);
        Application decided = createApplication(drive, "decided@example.com", ApplicationStatus.SHORTLISTED);
        Application rejected = createApplication(drive, "not-shortlisted@example.com", ApplicationStatus.REJECTED);

        finalDecisionService.decide(decided.getId(), tpo.getEmail(), request(FinalDecision.SELECTED, null));

        List<FinalDecisionResponse> results = finalDecisionService.getForDrive(drive.getId());

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(result -> result.getApplicationId().equals(pending.getId())
                && result.getDecision() == null));
        assertTrue(results.stream().anyMatch(result -> result.getApplicationId().equals(decided.getId())
                && result.getDecision() == FinalDecision.SELECTED));
        assertTrue(results.stream().noneMatch(result -> result.getApplicationId().equals(rejected.getId())));
    }

    @Test
    void validatesDecisionAndRemarks() {
        FinalDecisionRequest request = request(null, "x".repeat(2001));
        assertTrue(!validator.validate(request).isEmpty());
    }

    private FinalDecisionRequest request(FinalDecision decision, String remarks) {
        FinalDecisionRequest request = new FinalDecisionRequest();
        request.setDecision(decision);
        request.setRemarks(remarks);
        return request;
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName("User " + email);
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Application createApplication(String email, ApplicationStatus status) {
        return createApplication(createDrive("Drive " + email), email, status);
    }

    private Application createApplication(PlacementDrive drive, String email, ApplicationStatus status) {
        User student = createUser(email, Role.STUDENT);
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
        drive.setJobRole("Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.0"));
        drive.setMaximumBacklogs(0);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("10.0"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(5));
        drive.setDriveDate(LocalDate.now().plusDays(6));
        drive.setStatus(PlacementDriveStatus.OPEN);
        return placementDriveRepository.saveAndFlush(drive);
    }
}
