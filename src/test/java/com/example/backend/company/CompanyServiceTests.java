package com.example.backend.company;

import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.company.dto.CompanyRequest;
import com.example.backend.company.dto.CompanyResponse;
import com.example.backend.company.service.CompanyService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyServiceTests {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private Validator validator;

    @Test
    void createsRetrievesUpdatesAndDeletesCompany() {
        CompanyResponse created = companyService.create(companyRequest("Northstar Labs"));
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals("Northstar Labs", created.getCompanyName());

        CompanyResponse retrieved = companyService.getById(created.getId());
        assertEquals(created.getId(), retrieved.getId());
        List<CompanyResponse> companies = companyService.getAll();
        assertEquals(1, companies.size());
        assertEquals("Northstar Labs", companies.get(0).getCompanyName());

        CompanyRequest updatedRequest = companyRequest("Northstar Technologies");
        updatedRequest.setIndustry("Technology");
        CompanyResponse updated = companyService.update(created.getId(), updatedRequest);
        assertEquals("Northstar Technologies", updated.getCompanyName());
        assertEquals("Technology", updated.getIndustry());
        assertEquals(created.getCreatedAt(), updated.getCreatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(created.getUpdatedAt()));

        companyService.delete(created.getId());
        assertEquals(0, companyService.getAll().size());
        assertThrows(ResourceNotFoundException.class, () -> companyService.getById(created.getId()));
    }

    @Test
    void rejectsDuplicateCompanyNamesIgnoringCase() {
        companyService.create(companyRequest("Northstar Labs"));

        assertThrows(BadRequestException.class,
                () -> companyService.create(companyRequest("  NORTHSTAR LABS ")));
    }

    @Test
    void validatesCompanyNameEmailAndWebsite() {
        CompanyRequest request = companyRequest(" ");
        request.setContactEmail("not-an-email");
        request.setWebsite("not-a-url");

        assertFalse(validator.validate(request).isEmpty());
    }

    private CompanyRequest companyRequest(String name) {
        CompanyRequest request = new CompanyRequest();
        request.setCompanyName(name);
        request.setDescription("A sample company.");
        request.setWebsite("https://northstar.example");
        request.setIndustry("Technology");
        request.setLocation("Remote");
        request.setCompanySize("100-500");
        request.setContactEmail("contact@northstar.example");
        request.setContactPhone("+1 555 123 4567");
        return request;
    }
}
