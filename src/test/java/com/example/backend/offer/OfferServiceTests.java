package com.example.backend.offer;

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
import com.example.backend.offer.dto.OfferRequest;
import com.example.backend.offer.dto.OfferResponse;
import com.example.backend.offer.dto.OfferUpdateRequest;
import com.example.backend.offer.entity.Offer;
import com.example.backend.offer.entity.OfferStatus;
import com.example.backend.offer.repository.OfferRepository;
import com.example.backend.offer.service.OfferService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OfferServiceTests {

    @Autowired
    private OfferService offerService;

    @Autowired
    private OfferRepository offerRepository;

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
    void createsDraftForSelectedApplicationAndRejectsOtherStatusesAndDuplicates() {
        Application selected = createApplication("offer-selected@example.com", ApplicationStatus.SELECTED);
        OfferResponse response = offerService.create(selected.getId(), offerRequest("OFFER-SELECTED"));
        assertEquals(OfferStatus.DRAFT, response.getStatus());
        assertEquals(selected.getId(), response.getApplicationId());
        assertEquals(selected.getPlacementDrive().getId(), response.getDriveId());
        assertNotNull(response.getCreatedAt());

        assertThrows(BadRequestException.class,
                () -> offerService.create(selected.getId(), offerRequest("OFFER-DUP-APP")));
        for (ApplicationStatus status : List.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED)) {
            Application application = createApplication(status.name().toLowerCase() + "-offer@example.com", status);
            assertThrows(BadRequestException.class,
                    () -> offerService.create(application.getId(), offerRequest("OFFER-" + status)));
        }
    }

    @Test
    void rejectsDuplicateOfferLetterNumbersAndInvalidInput() {
        Application first = createApplication("first-letter@example.com", ApplicationStatus.SELECTED);
        Application second = createApplication("second-letter@example.com", ApplicationStatus.SELECTED);
        offerService.create(first.getId(), offerRequest("LETTER-001"));
        assertThrows(BadRequestException.class,
                () -> offerService.create(second.getId(), offerRequest("letter-001")));

        OfferRequest invalid = offerRequest("VALIDATION-001");
        invalid.setJobTitle(" ");
        invalid.setAnnualPackageLpa(new BigDecimal("-0.01"));
        invalid.setExpiryDate(LocalDate.now().minusDays(1));
        assertFalse(validator.validate(invalid).isEmpty());

        OfferRequest invalidJoining = offerRequest("VALIDATION-002");
        invalidJoining.setJoiningDate(LocalDate.now().minusDays(1));
        assertFalse(validator.validate(invalidJoining).isEmpty());
    }

    @Test
    void updatesDraftButRejectsTerminalOfferChanges() {
        Application application = createApplication("update-offer@example.com", ApplicationStatus.SELECTED);
        OfferResponse draft = offerService.create(application.getId(), offerRequest("UPDATE-001"));
        OfferUpdateRequest update = offerUpdateRequest();
        OfferResponse updated = offerService.update(draft.getOfferId(), update);
        assertEquals("Senior Engineer", updated.getJobTitle());
        assertEquals(new BigDecimal("9.50"), updated.getAnnualPackageLpa());

        offerService.issue(draft.getOfferId());
        offerService.accept(draft.getOfferId(), application.getStudent().getEmail());
        assertThrows(BadRequestException.class, () -> offerService.update(draft.getOfferId(), update));
        assertThrows(ResourceNotFoundException.class, () -> offerService.update(Long.MAX_VALUE, update));
    }

    @Test
    void issuesOnlyDraftOffersForSelectedApplications() {
        Application application = createApplication("issue-offer@example.com", ApplicationStatus.SELECTED);
        OfferResponse offer = offerService.create(application.getId(), offerRequest("ISSUE-001"));
        OfferResponse issued = offerService.issue(offer.getOfferId());
        assertEquals(OfferStatus.ISSUED, issued.getStatus());
        assertEquals(LocalDate.now(), issued.getOfferDate());
        var issuedNotification = notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                application.getStudent().getEmail()).get(0);
        assertEquals(NotificationType.OFFER_ISSUED, issuedNotification.getNotificationType());
        assertEquals(NotificationReferenceType.OFFER, issuedNotification.getReferenceType());
        assertEquals(issued.getOfferId(), issuedNotification.getReferenceId());
        assertThrows(BadRequestException.class, () -> offerService.issue(offer.getOfferId()));

        Application other = createApplication("unselected-at-issue@example.com", ApplicationStatus.SELECTED);
        OfferResponse secondOffer = offerService.create(other.getId(), offerRequest("ISSUE-002"));
        other.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.saveAndFlush(other);
        assertThrows(BadRequestException.class, () -> offerService.issue(secondOffer.getOfferId()));
    }

    @Test
    void studentAcceptsOrDeclinesOnlyOwnIssuedOffersAndCannotAcceptExpiredOffer() {
        User tpo = new User();
        tpo.setEmail("offer-acceptance-tpo@example.com");
        tpo.setName("Offer TPO");
        tpo.setPassword("encoded-password");
        tpo.setRole(Role.TPO_ADMIN);
        userRepository.saveAndFlush(tpo);

        Application acceptedApp = createApplication("accept-owner@example.com", ApplicationStatus.SELECTED);
        OfferResponse accepted = createIssuedOffer(acceptedApp, "ACCEPT-001", LocalDate.now().plusDays(4));
        assertEquals(OfferStatus.ACCEPTED,
                offerService.accept(accepted.getOfferId(), acceptedApp.getStudent().getEmail()).getStatus());
        assertEquals(ApplicationStatus.SELECTED,
                applicationRepository.findById(acceptedApp.getId()).orElseThrow().getStatus());
        assertEquals(OfferStatus.ACCEPTED,
                offerService.getForDrive(acceptedApp.getPlacementDrive().getId()).get(0).getStatus());
        assertEquals(NotificationType.OFFER_ACCEPTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        acceptedApp.getStudent().getEmail()).get(0).getNotificationType());
        assertEquals(1, notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                tpo.getEmail()).size());
        assertEquals(NotificationType.OFFER_ACCEPTED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        tpo.getEmail()).get(0).getNotificationType());
        assertThrows(BadRequestException.class,
                () -> offerService.accept(accepted.getOfferId(), acceptedApp.getStudent().getEmail()));
        assertEquals(1, notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                tpo.getEmail()).size());

        Application declinedApp = createApplication("decline-owner@example.com", ApplicationStatus.SELECTED);
        OfferResponse declined = createIssuedOffer(declinedApp, "DECLINE-001", LocalDate.now().plusDays(4));
        assertEquals(OfferStatus.DECLINED,
                offerService.decline(declined.getOfferId(), declinedApp.getStudent().getEmail()).getStatus());
        assertEquals(NotificationType.OFFER_DECLINED,
                notificationRepository.findAllByRecipient_EmailOrderByCreatedAtDesc(
                        declinedApp.getStudent().getEmail()).get(0).getNotificationType());

        Application expiredApp = createApplication("expired-owner@example.com", ApplicationStatus.SELECTED);
        OfferResponse expired = createIssuedOffer(expiredApp, "EXPIRED-001", LocalDate.now().plusDays(4));
        Offer expiredEntity = offerRepository.findById(expired.getOfferId()).orElseThrow();
        expiredEntity.setExpiryDate(LocalDate.now().minusDays(1));
        offerRepository.saveAndFlush(expiredEntity);
        assertThrows(BadRequestException.class,
                () -> offerService.accept(expired.getOfferId(), expiredApp.getStudent().getEmail()));

        assertThrows(ResourceNotFoundException.class,
                () -> offerService.accept(declined.getOfferId(), "another-student@example.com"));
        assertThrows(ResourceNotFoundException.class,
                () -> offerService.decline(declined.getOfferId(), "another-student@example.com"));
    }

    @Test
    void withdrawsIssuedOffersAndRejectsInvalidTransitions() {
        Application application = createApplication("withdraw-offer@example.com", ApplicationStatus.SELECTED);
        OfferResponse offer = createIssuedOffer(application, "WITHDRAW-001", LocalDate.now().plusDays(4));
        assertEquals(OfferStatus.WITHDRAWN, offerService.withdraw(offer.getOfferId()).getStatus());
        assertThrows(BadRequestException.class, () -> offerService.withdraw(offer.getOfferId()));

        Application acceptedApp = createApplication("withdraw-accepted@example.com", ApplicationStatus.SELECTED);
        OfferResponse accepted = createIssuedOffer(acceptedApp, "WITHDRAW-002", LocalDate.now().plusDays(4));
        offerService.accept(accepted.getOfferId(), acceptedApp.getStudent().getEmail());
        assertThrows(BadRequestException.class, () -> offerService.withdraw(accepted.getOfferId()));
    }

    @Test
    void retrievesOffersForOwnerAndDriveOnly() {
        Application owner = createApplication("offer-view-owner@example.com", ApplicationStatus.SELECTED);
        Application other = createApplication("offer-view-other@example.com", ApplicationStatus.SELECTED);
        OfferResponse own = offerService.create(owner.getId(), offerRequest("VIEW-001"));
        offerService.create(other.getId(), offerRequest("VIEW-002"));

        assertEquals(own.getOfferId(), offerService.get(own.getOfferId(), owner.getStudent().getEmail(), false)
                .getOfferId());
        assertThrows(ResourceNotFoundException.class,
                () -> offerService.get(own.getOfferId(), other.getStudent().getEmail(), false));
        assertEquals(own.getOfferId(), offerService.get(own.getOfferId(), "admin@example.com", true).getOfferId());
        assertEquals(1, offerService.getMyOffers(owner.getStudent().getEmail()).size());
        assertEquals(1, offerService.getForDrive(owner.getPlacementDrive().getId()).size());
        assertThrows(ResourceNotFoundException.class, () -> offerService.getForDrive(Long.MAX_VALUE));
        assertThrows(ResourceNotFoundException.class,
                () -> offerService.get(Long.MAX_VALUE, owner.getStudent().getEmail(), true));
    }

    private OfferResponse createIssuedOffer(Application application, String number, LocalDate expiryDate) {
        OfferRequest request = offerRequest(number);
        request.setExpiryDate(expiryDate);
        OfferResponse draft = offerService.create(application.getId(), request);
        return offerService.issue(draft.getOfferId());
    }

    private OfferRequest offerRequest(String number) {
        OfferRequest request = new OfferRequest();
        request.setOfferLetterNumber(number);
        request.setJobTitle("Software Engineer");
        request.setEmploymentType(EmploymentType.FULL_TIME);
        request.setLocation("Hyderabad");
        request.setAnnualPackageLpa(new BigDecimal("8.50"));
        request.setJoiningDate(LocalDate.now().plusMonths(2));
        request.setExpiryDate(LocalDate.now().plusDays(30));
        request.setNotes("Offer details");
        return request;
    }

    private OfferUpdateRequest offerUpdateRequest() {
        OfferUpdateRequest request = new OfferUpdateRequest();
        request.setJobTitle("Senior Engineer");
        request.setEmploymentType(EmploymentType.FULL_TIME);
        request.setLocation("Pune");
        request.setAnnualPackageLpa(new BigDecimal("9.50"));
        request.setJoiningDate(LocalDate.now().plusMonths(3));
        request.setExpiryDate(LocalDate.now().plusDays(45));
        request.setNotes("Updated terms");
        return request;
    }

    private Application createApplication(String email, ApplicationStatus status) {
        User student = new User();
        student.setEmail(email);
        student.setName("Student " + email);
        student.setPassword("encoded-password");
        student.setRole(Role.STUDENT);
        student = userRepository.saveAndFlush(student);

        Company company = new Company();
        company.setCompanyName("Offer Company " + email);
        company = companyRepository.saveAndFlush(company);
        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Offer Drive " + email);
        drive.setJobRole("Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7.00"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("10.00"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(5));
        drive.setDriveDate(LocalDate.now().plusDays(6));
        drive.setStatus(PlacementDriveStatus.OPEN);
        drive = placementDriveRepository.saveAndFlush(drive);
        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(status);
        return applicationRepository.saveAndFlush(application);
    }
}
