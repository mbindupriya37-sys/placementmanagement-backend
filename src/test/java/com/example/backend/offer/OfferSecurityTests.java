package com.example.backend.offer;

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
import com.example.backend.offer.controller.OfferController;
import com.example.backend.offer.dto.OfferRequest;
import com.example.backend.offer.entity.OfferStatus;
import com.example.backend.offer.service.OfferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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
class OfferSecurityTests {

    @Autowired
    private OfferController offerController;

    @Autowired
    private OfferService offerService;

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
    void tpoCanCreateIssueAndListDriveOffers() {
        Application application = createApplication("offer-tpo@example.com");
        OfferRequest request = offerRequest("SEC-OFFER-01");
        var offer = offerController.create(application.getId(), request).getData();
        assertEquals(OfferStatus.DRAFT, offer.getStatus());
        assertEquals(OfferStatus.ISSUED, offerController.issue(offer.getOfferId()).getData().getStatus());
        assertEquals(1, offerController.getForDrive(application.getPlacementDrive().getId()).getData().size());
    }

    @Test
    @WithMockUser(username = "offer-student@example.com", roles = "STUDENT")
    void studentCanAcceptAndViewOwnOfferButCannotManageOrSeeOthers() {
        Application own = createApplication("offer-student@example.com");
        Application other = createApplication("offer-other@example.com");
        var ownOffer = offerService.create(own.getId(), offerRequest("SEC-OFFER-02"));
        var otherOffer = offerService.create(other.getId(), offerRequest("SEC-OFFER-03"));
        offerService.issue(ownOffer.getOfferId());
        offerService.issue(otherOffer.getOfferId());

        assertEquals(OfferStatus.ACCEPTED,
                offerController.accept(ownOffer.getOfferId(), SecurityContextHolder.getContext().getAuthentication())
                        .getData().getStatus());
        assertEquals(ownOffer.getOfferId(), offerController.get(ownOffer.getOfferId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().getOfferId());
        assertEquals(1, offerController.getMine(SecurityContextHolder.getContext().getAuthentication())
                .getData().size());
        assertThrows(com.example.backend.common.exception.ResourceNotFoundException.class,
                () -> offerController.get(otherOffer.getOfferId(),
                        SecurityContextHolder.getContext().getAuthentication()));
        assertThrows(AuthorizationDeniedException.class, () -> offerController.issue(otherOffer.getOfferId()));
        assertThrows(AuthorizationDeniedException.class, () -> offerController.withdraw(otherOffer.getOfferId()));
    }

    @Test
    @WithMockUser(roles = "COMPANY_RECRUITER")
    void recruiterCannotViewOrManageOffers() {
        assertThrows(AuthorizationDeniedException.class,
                () -> offerController.getForDrive(1L));
        assertThrows(AuthorizationDeniedException.class,
                () -> offerController.get(1L, SecurityContextHolder.getContext().getAuthentication()));
    }

    private Application createApplication(String email) {
        User student = new User();
        student.setEmail(email);
        student.setName("Student " + email);
        student.setPassword("encoded-password");
        student.setRole(Role.STUDENT);
        student = userRepository.saveAndFlush(student);

        Company company = new Company();
        company.setCompanyName("Offer security company " + email);
        company = companyRepository.saveAndFlush(company);
        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Offer security drive " + email);
        drive.setJobRole("Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7"));
        drive.setMaximumBacklogs(1);
        drive.setEligibleBranches(List.of("Computer Science"));
        drive.setGraduationYear(2027);
        drive.setPackageLpa(new BigDecimal("10"));
        drive.setApplicationStartDate(LocalDate.now());
        drive.setApplicationEndDate(LocalDate.now().plusDays(5));
        drive.setDriveDate(LocalDate.now().plusDays(6));
        drive.setStatus(PlacementDriveStatus.OPEN);
        drive = placementDriveRepository.saveAndFlush(drive);

        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(ApplicationStatus.SELECTED);
        return applicationRepository.saveAndFlush(application);
    }

    private OfferRequest offerRequest(String number) {
        OfferRequest request = new OfferRequest();
        request.setOfferLetterNumber(number);
        request.setJobTitle("Engineer");
        request.setEmploymentType(EmploymentType.FULL_TIME);
        request.setAnnualPackageLpa(new BigDecimal("8.5"));
        request.setJoiningDate(LocalDate.now().plusMonths(2));
        request.setExpiryDate(LocalDate.now().plusDays(20));
        return request;
    }
}
