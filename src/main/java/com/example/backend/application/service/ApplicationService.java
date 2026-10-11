package com.example.backend.application.service;

import com.example.backend.application.dto.ApplicationResponse;
import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.entity.PlacementDriveStatus;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.eligibility.dto.EligibilityResponse;
import com.example.backend.eligibility.exception.StudentProfileMissingException;
import com.example.backend.eligibility.service.EligibilityService;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final EligibilityService eligibilityService;
    private final NotificationService notificationService;

    public ApplicationService(ApplicationRepository applicationRepository,
                              UserRepository userRepository,
                              PlacementDriveRepository placementDriveRepository,
                              EligibilityService eligibilityService,
                              NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.eligibilityService = eligibilityService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ApplicationResponse apply(String email, Long driveId) {
        PlacementDrive drive = placementDriveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", "id", driveId));
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "email", email));

        EligibilityResponse eligibility;
        try {
            eligibility = eligibilityService.checkEligibility(email, driveId);
        } catch (StudentProfileMissingException exception) {
            throw new ResourceNotFoundException("Student profile not found");
        }
        if (!eligibility.isEligible()) {
            throw new BadRequestException("Student is not eligible: "
                    + String.join("; ", eligibility.getReasons()));
        }

        if (drive.getStatus() != PlacementDriveStatus.OPEN) {
            throw new BadRequestException("Drive is not open");
        }

        LocalDate today = LocalDate.now();
        if (today.isBefore(drive.getApplicationStartDate())) {
            throw new BadRequestException("Application period has not started");
        }
        if (today.isAfter(drive.getApplicationEndDate())) {
            throw new BadRequestException("Application period has ended");
        }

        if (applicationRepository.existsByStudent_IdAndPlacementDrive_Id(student.getId(), driveId)) {
            throw new BadRequestException("Student has already applied");
        }

        Application application = new Application();
        application.setStudent(student);
        application.setPlacementDrive(drive);
        application.setStatus(ApplicationStatus.APPLIED);
        Application savedApplication;
        try {
            savedApplication = applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Student has already applied");
        }
        notificationService.createNotification(student, "Application Submitted",
                "Your application for " + drive.getDriveTitle() + " at "
                        + drive.getCompany().getCompanyName() + " was submitted.",
                NotificationType.APPLICATION_SUBMITTED, NotificationReferenceType.APPLICATION,
                savedApplication.getId());
        for (User tpoAdmin : userRepository.findAllByRoleForUpdate(Role.TPO_ADMIN)) {
            notificationService.recordApplicationActivity(tpoAdmin, drive.getId(), drive.getDriveTitle());
        }
        return toResponse(savedApplication);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getMyApplications(String email) {
        return applicationRepository.findAllByStudent_EmailOrderByAppliedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getMyApplication(String email, Long applicationId) {
        Application application = applicationRepository.findByIdAndStudent_Email(applicationId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        return toResponse(application);
    }

    private ApplicationResponse toResponse(Application application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setApplicationId(application.getId());
        response.setDriveId(application.getPlacementDrive().getId());
        response.setDriveTitle(application.getPlacementDrive().getDriveTitle());
        response.setCompanyName(application.getPlacementDrive().getCompany().getCompanyName());
        response.setStatus(application.getStatus());
        response.setAppliedAt(application.getAppliedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }
}
