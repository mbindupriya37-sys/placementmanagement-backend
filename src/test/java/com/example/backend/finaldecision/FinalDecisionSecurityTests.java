package com.example.backend.finaldecision;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.finaldecision.controller.FinalDecisionController;
import com.example.backend.finaldecision.dto.FinalDecisionRequest;
import com.example.backend.finaldecision.entity.FinalDecision;
import com.example.backend.finaldecision.service.FinalDecisionService;
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
class FinalDecisionSecurityTests {

    @Autowired
    private FinalDecisionController finalDecisionController;

    @Autowired
    private FinalDecisionService finalDecisionService;

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
    void tpoCanDecideAndListFinalDecisions() {
        User tpo = saveUser("security-tpo@example.com", Role.TPO_ADMIN);
        Application application = createApplication("security-selected@example.com", ApplicationStatus.SHORTLISTED);
        FinalDecisionRequest request = new FinalDecisionRequest();
        request.setDecision(FinalDecision.SELECTED);
        request.setRemarks("Selected");

        assertEquals(FinalDecision.SELECTED, finalDecisionController.decide(application.getId(), request,
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        tpo.getEmail(), null)).getData().getDecision());
        assertEquals(1, finalDecisionController.getForDrive(
                application.getPlacementDrive().getId()).getData().size());
    }

    @Test
    @WithMockUser(username = "final-owner@example.com", roles = "STUDENT")
    void studentCannotMakeFinalDecisionAndCanViewOwnOnly() {
        Application owner = createApplication("final-owner@example.com", ApplicationStatus.SHORTLISTED);
        Application other = createApplication("final-other@example.com", ApplicationStatus.SHORTLISTED);
        User tpo = saveUser("decision-admin@example.com", Role.TPO_ADMIN);
        FinalDecisionRequest request = new FinalDecisionRequest();
        request.setDecision(FinalDecision.REJECTED);
        finalDecisionService.decide(owner.getId(), tpo.getEmail(), request);

        assertEquals(owner.getId(), finalDecisionController.getForApplication(owner.getId(),
                SecurityContextHolder.getContext().getAuthentication()).getData().getApplicationId());
        assertThrows(ResourceNotFoundException.class, () -> finalDecisionController.getForApplication(other.getId(),
                SecurityContextHolder.getContext().getAuthentication()));
        assertThrows(AuthorizationDeniedException.class, () -> finalDecisionController.decide(
                other.getId(), request, SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    @WithMockUser(roles = "COMPANY_RECRUITER")
    void recruiterCannotAccessFinalDecisionEndpoints() {
        assertThrows(AuthorizationDeniedException.class, () -> finalDecisionController.getForDrive(1L));
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setName("User " + email);
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Application createApplication(String email, ApplicationStatus status) {
        User student = saveUser(email, Role.STUDENT);
        Company company = new Company();
        company.setCompanyName("Final security company " + email);
        company = companyRepository.saveAndFlush(company);
        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        drive.setDriveTitle("Final security drive " + email);
        drive.setJobRole("Engineer");
        drive.setEmploymentType(EmploymentType.FULL_TIME);
        drive.setMinimumCgpa(new BigDecimal("7"));
        drive.setMaximumBacklogs(0);
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
        application.setStatus(status);
        return applicationRepository.saveAndFlush(application);
    }
}
