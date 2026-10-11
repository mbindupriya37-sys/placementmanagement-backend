package com.example.backend.finaldecision.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.finaldecision.dto.FinalDecisionRequest;
import com.example.backend.finaldecision.dto.FinalDecisionResponse;
import com.example.backend.finaldecision.entity.FinalDecision;
import com.example.backend.finaldecision.entity.FinalDecisionRecord;
import com.example.backend.finaldecision.repository.FinalDecisionRecordRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class FinalDecisionService {

    private final FinalDecisionRecordRepository finalDecisionRecordRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final NotificationService notificationService;

    public FinalDecisionService(FinalDecisionRecordRepository finalDecisionRecordRepository,
                                ApplicationRepository applicationRepository,
                                UserRepository userRepository,
                                PlacementDriveRepository placementDriveRepository,
                                NotificationService notificationService) {
        this.finalDecisionRecordRepository = finalDecisionRecordRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public FinalDecisionResponse decide(Long applicationId, String tpoEmail, FinalDecisionRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        if (application.getStatus() != ApplicationStatus.SHORTLISTED) {
            throw new BadRequestException("Final decision can only be made for SHORTLISTED applications");
        }
        if (finalDecisionRecordRepository.existsByApplication_Id(applicationId)) {
            throw new BadRequestException("A final decision already exists for this application");
        }
        User decidedBy = userRepository.findByEmail(tpoEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated TPO user not found"));

        FinalDecisionRecord record = new FinalDecisionRecord();
        record.setApplication(application);
        record.setDecision(request.getDecision());
        record.setRemarks(trimToNull(request.getRemarks()));
        record.setDecidedBy(decidedBy);
        application.setStatus(request.getDecision() == FinalDecision.SELECTED
                ? ApplicationStatus.SELECTED : ApplicationStatus.REJECTED);
        FinalDecisionRecord savedRecord;
        try {
            savedRecord = finalDecisionRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            if (finalDecisionRecordRepository.existsByApplication_Id(applicationId)) {
                throw new BadRequestException("A final decision already exists for this application");
            }
            throw exception;
        }
        boolean selected = request.getDecision() == FinalDecision.SELECTED;
        notificationService.createNotification(application.getStudent(),
                selected ? "Congratulations! You are selected" : "Placement Application Update",
                selected
                        ? "You have been selected for " + application.getPlacementDrive().getDriveTitle() + "."
                        : "A final decision has been recorded for your application to "
                                + application.getPlacementDrive().getDriveTitle() + ".",
                NotificationType.FINAL_DECISION, NotificationReferenceType.APPLICATION, application.getId());
        return toResponse(savedRecord);
    }

    @Transactional(readOnly = true)
    public FinalDecisionResponse getForApplication(Long applicationId, String email, boolean admin) {
        FinalDecisionRecord record;
        if (admin) {
            record = finalDecisionRecordRepository.findByApplication_Id(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Final decision", "applicationId", applicationId));
        } else {
            record = finalDecisionRecordRepository
                    .findByApplication_IdAndApplication_Student_Email(applicationId, email)
                    .orElseThrow(() -> new ResourceNotFoundException("Final decision", "applicationId", applicationId));
        }
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public List<FinalDecisionResponse> getForDrive(Long driveId) {
        if (!placementDriveRepository.existsById(driveId)) {
            throw new ResourceNotFoundException("Placement drive", "id", driveId);
        }
        List<FinalDecisionRecord> records = finalDecisionRecordRepository
                .findAllByApplication_PlacementDrive_IdOrderByDecidedAtDesc(driveId);
        Set<Long> recordedApplicationIds = records.stream()
                .map(record -> record.getApplication().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<FinalDecisionResponse> pending = applicationRepository
                .findAllByPlacementDrive_IdAndStatus(driveId, ApplicationStatus.SHORTLISTED)
                .stream()
                .filter(application -> !recordedApplicationIds.contains(application.getId()))
                .map(this::toPendingResponse)
                .toList();

        return Stream.concat(pending.stream(), records.stream().map(this::toResponse)).toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private FinalDecisionResponse toResponse(FinalDecisionRecord record) {
        FinalDecisionResponse response = new FinalDecisionResponse();
        response.setDecisionId(record.getId());
        response.setApplicationId(record.getApplication().getId());
        response.setStudentName(record.getApplication().getStudent().getName());
        response.setStudentEmail(record.getApplication().getStudent().getEmail());
        response.setDriveId(record.getApplication().getPlacementDrive().getId());
        response.setDriveTitle(record.getApplication().getPlacementDrive().getDriveTitle());
        response.setDecision(record.getDecision());
        response.setRemarks(record.getRemarks());
        response.setDecidedAt(record.getDecidedAt());
        response.setDecidedByName(record.getDecidedBy().getName());
        response.setDecidedByEmail(record.getDecidedBy().getEmail());
        return response;
    }

    private FinalDecisionResponse toPendingResponse(Application application) {
        FinalDecisionResponse response = new FinalDecisionResponse();
        response.setApplicationId(application.getId());
        response.setStudentName(application.getStudent().getName());
        response.setStudentEmail(application.getStudent().getEmail());
        response.setDriveId(application.getPlacementDrive().getId());
        response.setDriveTitle(application.getPlacementDrive().getDriveTitle());
        return response;
    }
}
