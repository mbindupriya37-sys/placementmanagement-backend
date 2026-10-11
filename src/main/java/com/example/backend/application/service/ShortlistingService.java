package com.example.backend.application.service;

import com.example.backend.application.dto.BulkApplicationStatusRequest;
import com.example.backend.application.dto.ShortlistApplicationResponse;
import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShortlistingService {

    private final ApplicationRepository applicationRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NotificationService notificationService;

    public ShortlistingService(ApplicationRepository applicationRepository,
                               PlacementDriveRepository placementDriveRepository,
                               StudentProfileRepository studentProfileRepository,
                               NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<ShortlistApplicationResponse> getApplicationsForDrive(Long driveId) {
        requireDriveExists(driveId);
        return applicationRepository.findAllByPlacementDrive_IdOrderByAppliedAtAsc(driveId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShortlistApplicationResponse shortlist(Long applicationId) {
        return updateSingleApplication(applicationId, ApplicationStatus.SHORTLISTED);
    }

    @Transactional
    public ShortlistApplicationResponse reject(Long applicationId) {
        return updateSingleApplication(applicationId, ApplicationStatus.REJECTED);
    }

    @Transactional
    public List<ShortlistApplicationResponse> bulkShortlist(
            Long driveId, BulkApplicationStatusRequest request) {
        return bulkUpdate(driveId, request, ApplicationStatus.SHORTLISTED);
    }

    @Transactional
    public List<ShortlistApplicationResponse> bulkReject(
            Long driveId, BulkApplicationStatusRequest request) {
        return bulkUpdate(driveId, request, ApplicationStatus.REJECTED);
    }

    private ShortlistApplicationResponse updateSingleApplication(Long applicationId, ApplicationStatus targetStatus) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        ensureApplied(application);
        application.setStatus(targetStatus);
        Application saved = applicationRepository.saveAndFlush(application);
        createStatusNotification(saved, targetStatus);
        return toResponse(saved);
    }

    private List<ShortlistApplicationResponse> bulkUpdate(
            Long driveId, BulkApplicationStatusRequest request, ApplicationStatus targetStatus) {
        requireDriveExists(driveId);
        List<Long> applicationIds = request.getApplicationIds();
        Set<Long> uniqueIds = new HashSet<>(applicationIds);
        if (uniqueIds.size() != applicationIds.size()) {
            throw new BadRequestException("Application IDs must not contain duplicates");
        }

        List<Application> applications = applicationRepository.findAllByIdIn(applicationIds);
        Map<Long, Application> applicationsById = new HashMap<>();
        for (Application application : applications) {
            applicationsById.put(application.getId(), application);
        }

        for (Long applicationId : applicationIds) {
            Application application = applicationsById.get(applicationId);
            if (application == null) {
                throw new ResourceNotFoundException("Application", "id", applicationId);
            }
            if (!application.getPlacementDrive().getId().equals(driveId)) {
                throw new BadRequestException(
                        "Application " + applicationId + " does not belong to placement drive " + driveId);
            }
            ensureApplied(application);
        }

        for (Long applicationId : applicationIds) {
            applicationsById.get(applicationId).setStatus(targetStatus);
        }

        List<Application> savedApplications = applicationRepository.saveAllAndFlush(
                applicationIds.stream().map(applicationsById::get).toList());
        Map<Long, Application> savedById = new HashMap<>();
        for (Application application : savedApplications) {
            savedById.put(application.getId(), application);
        }
        List<ShortlistApplicationResponse> responses = new ArrayList<>();
        for (Long applicationId : applicationIds) {
            Application saved = savedById.get(applicationId);
            createStatusNotification(saved, targetStatus);
            responses.add(toResponse(saved));
        }
        return responses;
    }

    private void createStatusNotification(Application application, ApplicationStatus targetStatus) {
        boolean shortlisted = targetStatus == ApplicationStatus.SHORTLISTED;
        String title = shortlisted ? "Application Shortlisted" : "Application Rejected";
        String message = shortlisted
                ? "Your application for " + application.getPlacementDrive().getDriveTitle() + " was shortlisted."
                : "Your application for " + application.getPlacementDrive().getDriveTitle() + " was rejected.";
        notificationService.createNotification(application.getStudent(), title, message,
                shortlisted ? NotificationType.APPLICATION_SHORTLISTED : NotificationType.APPLICATION_REJECTED,
                NotificationReferenceType.APPLICATION, application.getId());
    }

    private void ensureApplied(Application application) {
        if (application.getStatus() != ApplicationStatus.APPLIED) {
            throw new BadRequestException("Application " + application.getId()
                    + " cannot be changed from " + application.getStatus() + "; only APPLIED applications "
                    + "can be shortlisted or rejected");
        }
    }

    private void requireDriveExists(Long driveId) {
        if (!placementDriveRepository.existsById(driveId)) {
            throw new ResourceNotFoundException("Placement drive", "id", driveId);
        }
    }

    private ShortlistApplicationResponse toResponse(Application application) {
        ShortlistApplicationResponse response = new ShortlistApplicationResponse();
        response.setApplicationId(application.getId());
        response.setStudentId(application.getStudent().getId());
        response.setStudentName(application.getStudent().getName());
        response.setStudentEmail(application.getStudent().getEmail());

        StudentProfile profile = studentProfileRepository
                .findByUserEmail(application.getStudent().getEmail()).orElse(null);
        if (profile != null) {
            response.setBranch(profile.getBranch());
            response.setGraduationYear(profile.getGraduationYear());
            response.setCgpa(profile.getCgpa());
            response.setBacklogs(profile.getBacklogs());
        }

        response.setPlacementDriveId(application.getPlacementDrive().getId());
        response.setDriveTitle(application.getPlacementDrive().getDriveTitle());
        response.setApplicationStatus(application.getStatus());
        response.setAppliedAt(application.getAppliedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }
}
