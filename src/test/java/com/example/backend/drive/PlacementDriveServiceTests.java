package com.example.backend.drive;

import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.dto.PlacementDriveRequest;
import com.example.backend.drive.dto.PlacementDriveResponse;
import com.example.backend.drive.dto.PlacementDriveStatusRequest;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.service.PlacementDriveService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlacementDriveServiceTests {

    @Autowired
    private PlacementDriveService placementDriveService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private Validator validator;

    @Test
    void createsRetrievesUpdatesAndDeletesDrive() {
        Long companyId = createCompany();
        PlacementDriveResponse created = placementDriveService.create(driveRequest(companyId));

        assertNotNull(created.getId());
        assertEquals(companyId, created.getCompanyId());
        assertEquals("Example Company", created.getCompanyName());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());

        PlacementDriveResponse fetched = placementDriveService.getById(created.getId());
        assertEquals(created.getId(), fetched.getId());
        assertEquals(1, placementDriveService.getAll().size());

        PlacementDriveRequest updateRequest = driveRequest(companyId);
        updateRequest.setDriveTitle("Updated Software Engineer Drive");
        PlacementDriveResponse updated = placementDriveService.update(created.getId(), updateRequest);
        assertEquals("Updated Software Engineer Drive", updated.getDriveTitle());
        assertEquals(created.getCreatedAt(), updated.getCreatedAt());

        placementDriveService.delete(created.getId());
        assertEquals(0, placementDriveService.getAll().size());
        assertThrows(ResourceNotFoundException.class, () -> placementDriveService.getById(created.getId()));
    }

    @Test
    void rejectsCreationWhenCompanyDoesNotExist() {
        PlacementDriveRequest request = driveRequest(Long.MAX_VALUE);
        assertThrows(ResourceNotFoundException.class, () -> placementDriveService.create(request));
    }

    @Test
    void updatesStatus() {
        Long companyId = createCompany();
        PlacementDriveResponse created = placementDriveService.create(driveRequest(companyId));

        PlacementDriveResponse updated = placementDriveService.updateStatus(created.getId(), "OPEN");

        assertEquals(PlacementDriveStatus.OPEN, updated.getStatus());
    }

    @Test
    void validatesCgpaDateRangeAndRequiredFields() {
        PlacementDriveRequest request = driveRequest(1L);
        request.setMinimumCgpa(new BigDecimal("10.01"));
        assertFalse(validator.validate(request).isEmpty());

        request = driveRequest(1L);
        request.setApplicationStartDate(request.getApplicationEndDate());
        assertFalse(validator.validate(request).isEmpty());

        request = driveRequest(1L);
        request.setDriveDate(request.getApplicationEndDate().minusDays(1));
        assertFalse(validator.validate(request).isEmpty());

        request = driveRequest(1L);
        request.setDriveTitle(" ");
        request.setJobRole("");
        request.setCompanyId(null);
        request.setEligibleBranches(List.of());
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void validatesBacklogsPackageAndStatus() {
        PlacementDriveRequest request = driveRequest(1L);
        request.setMaximumBacklogs(-1);
        request.setPackageLpa(new BigDecimal("-0.01"));
        request.setStatus("INVALID");
        assertFalse(validator.validate(request).isEmpty());

        PlacementDriveStatusRequest statusRequest = new PlacementDriveStatusRequest();
        statusRequest.setStatus("INVALID");
        assertFalse(validator.validate(statusRequest).isEmpty());
    }

    private Long createCompany() {
        Company company = new Company();
        company.setCompanyName("Example Company");
        return companyRepository.save(company).getId();
    }

    private PlacementDriveRequest driveRequest(Long companyId) {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(7);

        PlacementDriveRequest request = new PlacementDriveRequest();
        request.setCompanyId(companyId);
        request.setDriveTitle("Software Engineer Drive");
        request.setJobRole("Software Engineer");
        request.setJobDescription("Build and maintain software.");
        request.setEmploymentType("FULL_TIME");
        request.setLocation("Remote");
        request.setMinimumCgpa(new BigDecimal("7.50"));
        request.setMaximumBacklogs(0);
        request.setEligibleBranches(List.of("Computer Science", "Information Technology"));
        request.setGraduationYear(Year.now().getValue() + 2);
        request.setPackageLpa(new BigDecimal("12.50"));
        request.setApplicationStartDate(start);
        request.setApplicationEndDate(end);
        request.setDriveDate(end.plusDays(1));
        request.setStatus("DRAFT");
        return request;
    }
}
