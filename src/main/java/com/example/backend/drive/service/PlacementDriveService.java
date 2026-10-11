package com.example.backend.drive.service;

import com.example.backend.company.entity.Company;
import com.example.backend.company.repository.CompanyRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.dto.PlacementDriveRequest;
import com.example.backend.drive.dto.PlacementDriveResponse;
import com.example.backend.drive.entity.EmploymentType;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlacementDriveService {

    private final PlacementDriveRepository placementDriveRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public PlacementDriveService(PlacementDriveRepository placementDriveRepository,
                                 CompanyRepository companyRepository,
                                 UserRepository userRepository) {
        this.placementDriveRepository = placementDriveRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PlacementDriveResponse create(PlacementDriveRequest request) {
        Company company = findCompany(request.getCompanyId());
        PlacementDrive drive = new PlacementDrive();
        drive.setCompany(company);
        applyRequest(drive, request);
        return toResponse(placementDriveRepository.save(drive));
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveResponse> getAll() {
        return placementDriveRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveResponse> getAllByCompanyId(Long companyId) {
        return placementDriveRepository.findAllByCompany_IdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveResponse> getAllForRecruiter(String email) {
        User recruiter = findAssignedRecruiter(email);
        return getAllByCompanyId(recruiter.getCompany().getId());
    }

    @Transactional(readOnly = true)
    public PlacementDriveResponse getById(Long id) {
        return toResponse(findDrive(id));
    }

    @Transactional(readOnly = true)
    public PlacementDriveResponse getByIdForRecruiter(Long id, String email) {
        User recruiter = findAssignedRecruiter(email);
        PlacementDrive drive = findDrive(id);
        if (!drive.getCompany().getId().equals(recruiter.getCompany().getId())) {
            throw new ResourceNotFoundException("Placement drive", "id", id);
        }
        return toResponse(drive);
    }

    @Transactional
    public PlacementDriveResponse update(Long id, PlacementDriveRequest request) {
        PlacementDrive drive = findDrive(id);
        Company company = findCompany(request.getCompanyId());
        drive.setCompany(company);
        applyRequest(drive, request);
        return toResponse(placementDriveRepository.saveAndFlush(drive));
    }

    @Transactional
    public void delete(Long id) {
        PlacementDrive drive = findDrive(id);
        try {
            placementDriveRepository.delete(drive);
            placementDriveRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Placement drive cannot be deleted because it is referenced elsewhere");
        }
    }

    @Transactional
    public PlacementDriveResponse updateStatus(Long id, String status) {
        PlacementDrive drive = findDrive(id);
        drive.setStatus(PlacementDriveStatus.valueOf(status));
        return toResponse(placementDriveRepository.saveAndFlush(drive));
    }

    private PlacementDrive findDrive(Long id) {
        return placementDriveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", "id", id));
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
    }

    private User findAssignedRecruiter(String email) {
        User recruiter = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (recruiter.getRole() != Role.COMPANY_RECRUITER || recruiter.getCompany() == null) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a company");
        }
        return recruiter;
    }

    private void applyRequest(PlacementDrive drive, PlacementDriveRequest request) {
        drive.setDriveTitle(request.getDriveTitle().trim());
        drive.setJobRole(request.getJobRole().trim());
        drive.setJobDescription(trimToNull(request.getJobDescription()));
        drive.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        drive.setLocation(trimToNull(request.getLocation()));
        drive.setMinimumCgpa(request.getMinimumCgpa());
        drive.setMaximumBacklogs(request.getMaximumBacklogs());
        drive.setEligibleBranches(request.getEligibleBranches().stream().map(String::trim).toList());
        drive.setGraduationYear(request.getGraduationYear());
        drive.setPackageLpa(request.getPackageLpa());
        drive.setApplicationStartDate(request.getApplicationStartDate());
        drive.setApplicationEndDate(request.getApplicationEndDate());
        drive.setDriveDate(request.getDriveDate());
        drive.setStatus(PlacementDriveStatus.valueOf(request.getStatus()));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PlacementDriveResponse toResponse(PlacementDrive drive) {
        PlacementDriveResponse response = new PlacementDriveResponse();
        response.setId(drive.getId());
        response.setCompanyId(drive.getCompany().getId());
        response.setCompanyName(drive.getCompany().getCompanyName());
        response.setDriveTitle(drive.getDriveTitle());
        response.setJobRole(drive.getJobRole());
        response.setJobDescription(drive.getJobDescription());
        response.setEmploymentType(drive.getEmploymentType());
        response.setLocation(drive.getLocation());
        response.setMinimumCgpa(drive.getMinimumCgpa());
        response.setMaximumBacklogs(drive.getMaximumBacklogs());
        response.setEligibleBranches(List.copyOf(drive.getEligibleBranches()));
        response.setGraduationYear(drive.getGraduationYear());
        response.setPackageLpa(drive.getPackageLpa());
        response.setApplicationStartDate(drive.getApplicationStartDate());
        response.setApplicationEndDate(drive.getApplicationEndDate());
        response.setDriveDate(drive.getDriveDate());
        response.setStatus(drive.getStatus());
        response.setCreatedAt(drive.getCreatedAt());
        response.setUpdatedAt(drive.getUpdatedAt());
        return response;
    }
}
