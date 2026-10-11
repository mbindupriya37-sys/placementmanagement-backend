package com.example.backend.selection.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.selection.dto.SelectionRoundRequest;
import com.example.backend.selection.dto.SelectionRoundResponse;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.repository.RoundResultRepository;
import com.example.backend.selection.repository.SelectionRoundRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SelectionRoundService {

    private final SelectionRoundRepository selectionRoundRepository;
    private final RoundResultRepository roundResultRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public SelectionRoundService(SelectionRoundRepository selectionRoundRepository,
                                 RoundResultRepository roundResultRepository,
                                 PlacementDriveRepository placementDriveRepository,
                                 ApplicationRepository applicationRepository,
                                 NotificationService notificationService,
                                 UserRepository userRepository) {
        this.selectionRoundRepository = selectionRoundRepository;
        this.roundResultRepository = roundResultRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.applicationRepository = applicationRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public SelectionRoundResponse create(Long driveId, SelectionRoundRequest request) {
        PlacementDrive drive = findDrive(driveId);
        if (selectionRoundRepository.existsByPlacementDrive_IdAndRoundOrder(driveId, request.getRoundOrder())) {
            throw duplicateOrder(driveId, request.getRoundOrder());
        }

        SelectionRound round = new SelectionRound();
        round.setPlacementDrive(drive);
        applyRequest(round, request);
        SelectionRound saved;
        try {
            saved = selectionRoundRepository.saveAndFlush(round);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateOrder(driveId, request.getRoundOrder());
        }
        notifyShortlistedApplicants(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SelectionRoundResponse> getForDrive(Long driveId) {
        findDrive(driveId);
        return selectionRoundRepository.findAllByPlacementDrive_IdOrderByRoundOrderAsc(driveId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SelectionRoundResponse> getForDriveForRecruiter(Long driveId, String email) {
        verifyRecruiterOwnsDrive(driveId, email);
        return selectionRoundRepository.findAllByPlacementDrive_IdOrderByRoundOrderAsc(driveId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SelectionRoundResponse getById(Long roundId) {
        return toResponse(findRound(roundId));
    }

    @Transactional(readOnly = true)
    public SelectionRoundResponse getByIdForRecruiter(Long roundId, String email) {
        SelectionRound round = findRound(roundId);
        verifyRecruiterOwnsDrive(round.getPlacementDrive().getId(), email);
        return toResponse(round);
    }

    @Transactional
    public SelectionRoundResponse update(Long roundId, SelectionRoundRequest request) {
        SelectionRound round = findRound(roundId);
        Long driveId = round.getPlacementDrive().getId();
        if (selectionRoundRepository.existsByPlacementDrive_IdAndRoundOrderAndIdNot(
                driveId, request.getRoundOrder(), roundId)) {
            throw duplicateOrder(driveId, request.getRoundOrder());
        }
        boolean scheduledTimeChanged = !round.getScheduledDateTime().equals(request.getScheduledDateTime());
        validateStatusTransition(round.getStatus(), request.getStatus());
        applyRequest(round, request);
        SelectionRound saved;
        try {
            saved = selectionRoundRepository.saveAndFlush(round);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateOrder(driveId, request.getRoundOrder());
        }
        if (scheduledTimeChanged) {
            notifyShortlistedApplicants(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long roundId) {
        SelectionRound round = findRound(roundId);
        if (roundResultRepository.existsBySelectionRound_Id(roundId)) {
            throw new BadRequestException("Selection round cannot be deleted because it already has results");
        }
        selectionRoundRepository.delete(round);
        selectionRoundRepository.flush();
    }

    @Transactional
    public SelectionRoundResponse updateStatus(Long roundId, SelectionRoundStatus status) {
        SelectionRound round = findRound(roundId);
        validateStatusTransition(round.getStatus(), status);
        round.setStatus(status);
        return toResponse(selectionRoundRepository.saveAndFlush(round));
    }

    private PlacementDrive findDrive(Long driveId) {
        return placementDriveRepository.findById(driveId)
                .orElseThrow(() -> new ResourceNotFoundException("Placement drive", "id", driveId));
    }

    private SelectionRound findRound(Long roundId) {
        return selectionRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection round", "id", roundId));
    }

    private void verifyRecruiterOwnsDrive(Long driveId, String email) {
        User recruiter = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (recruiter.getRole() != Role.COMPANY_RECRUITER || recruiter.getCompany() == null) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a company");
        }
        PlacementDrive drive = findDrive(driveId);
        if (!drive.getCompany().getId().equals(recruiter.getCompany().getId())) {
            throw new ResourceNotFoundException("Selection round", "driveId", driveId);
        }
    }

    private void applyRequest(SelectionRound round, SelectionRoundRequest request) {
        round.setRoundName(request.getRoundName().trim());
        round.setRoundType(request.getRoundType());
        round.setRoundOrder(request.getRoundOrder());
        round.setDescription(trimToNull(request.getDescription()));
        round.setScheduledDateTime(request.getScheduledDateTime());
        round.setStatus(request.getStatus());
    }

    private void validateStatusTransition(SelectionRoundStatus current, SelectionRoundStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = (current == SelectionRoundStatus.UPCOMING
                && (target == SelectionRoundStatus.ONGOING || target == SelectionRoundStatus.CANCELLED))
                || (current == SelectionRoundStatus.ONGOING && target == SelectionRoundStatus.COMPLETED);
        if (!allowed) {
            throw new BadRequestException("Invalid selection round status transition from "
                    + current + " to " + target);
        }
    }

    private BadRequestException duplicateOrder(Long driveId, Integer roundOrder) {
        return new BadRequestException("Round order " + roundOrder
                + " is already used for placement drive " + driveId);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notifyShortlistedApplicants(SelectionRound round) {
        List<Application> applications = applicationRepository.findAllByPlacementDrive_IdAndStatus(
                round.getPlacementDrive().getId(), ApplicationStatus.SHORTLISTED);
        for (Application application : applications) {
            notificationService.createNotification(application.getStudent(), "Selection Round Scheduled",
                    round.getRoundName() + " is scheduled for " + round.getScheduledDateTime() + ".",
                    NotificationType.ROUND_SCHEDULED, NotificationReferenceType.SELECTION_ROUND, round.getId());
        }
    }

    private SelectionRoundResponse toResponse(SelectionRound round) {
        SelectionRoundResponse response = new SelectionRoundResponse();
        response.setId(round.getId());
        response.setPlacementDriveId(round.getPlacementDrive().getId());
        response.setDriveTitle(round.getPlacementDrive().getDriveTitle());
        response.setRoundName(round.getRoundName());
        response.setRoundType(round.getRoundType());
        response.setRoundOrder(round.getRoundOrder());
        response.setDescription(round.getDescription());
        response.setScheduledDateTime(round.getScheduledDateTime());
        response.setStatus(round.getStatus());
        response.setCreatedAt(round.getCreatedAt());
        response.setUpdatedAt(round.getUpdatedAt());
        return response;
    }
}
