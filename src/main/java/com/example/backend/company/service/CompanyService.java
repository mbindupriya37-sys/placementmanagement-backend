package com.example.backend.company.service;

import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.company.dto.CompanyRequest;
import com.example.backend.company.dto.CompanyResponse;
import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CompanyResponse assignRecruiter(Long companyId, String recruiterEmail) {
        Company company = findCompany(companyId);
        User recruiter = userRepository.findByEmail(recruiterEmail.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", recruiterEmail));
        if (recruiter.getRole() != Role.COMPANY_RECRUITER) {
            throw new BadRequestException("The selected account is not a company recruiter");
        }
        recruiter.setCompany(company);
        userRepository.saveAndFlush(recruiter);
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (companyRepository.existsByCompanyNameIgnoreCase(request.getCompanyName().trim())) {
            throw new BadRequestException("A company with this name already exists");
        }

        Company company = new Company();
        applyRequest(company, request);
        return toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAll() {
        return companyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(Long id) {
        return toResponse(findCompany(id));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest request) {
        Company company = findCompany(id);
        if (companyRepository.existsByCompanyNameIgnoreCaseAndIdNot(request.getCompanyName().trim(), id)) {
            throw new BadRequestException("A company with this name already exists");
        }

        applyRequest(company, request);
        return toResponse(companyRepository.saveAndFlush(company));
    }

    @Transactional
    public void delete(Long id) {
        Company company = findCompany(id);
        companyRepository.delete(company);
    }

    private Company findCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
    }

    private void applyRequest(Company company, CompanyRequest request) {
        company.setCompanyName(request.getCompanyName().trim());
        company.setDescription(trimToNull(request.getDescription()));
        company.setWebsite(trimToNull(request.getWebsite()));
        company.setIndustry(trimToNull(request.getIndustry()));
        company.setLocation(trimToNull(request.getLocation()));
        company.setCompanySize(trimToNull(request.getCompanySize()));
        company.setContactEmail(trimToNull(request.getContactEmail()));
        company.setContactPhone(trimToNull(request.getContactPhone()));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CompanyResponse toResponse(Company company) {
        CompanyResponse response = new CompanyResponse();
        response.setId(company.getId());
        response.setCompanyName(company.getCompanyName());
        response.setDescription(company.getDescription());
        response.setWebsite(company.getWebsite());
        response.setIndustry(company.getIndustry());
        response.setLocation(company.getLocation());
        response.setCompanySize(company.getCompanySize());
        response.setContactEmail(company.getContactEmail());
        response.setContactPhone(company.getContactPhone());
        response.setCreatedAt(company.getCreatedAt());
        response.setUpdatedAt(company.getUpdatedAt());
        return response;
    }
}
