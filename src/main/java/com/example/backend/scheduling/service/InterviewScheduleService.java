package com.example.backend.scheduling.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.scheduling.dto.InterviewScheduleRequest;
import com.example.backend.scheduling.dto.InterviewScheduleResponse;
import com.example.backend.scheduling.dto.InterviewScheduleUpdateRequest;
import com.example.backend.scheduling.entity.InterviewMode;
import com.example.backend.scheduling.entity.InterviewSchedule;
import com.example.backend.scheduling.entity.InterviewScheduleStatus;
import com.example.backend.scheduling.repository.InterviewScheduleRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.entity.SelectionRoundStatus;
import com.example.backend.selection.repository.SelectionRoundRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewScheduleService {

    private static final List<InterviewScheduleStatus> ACTIVE_STATUSES =
            List.of(InterviewScheduleStatus.SCHEDULED, InterviewScheduleStatus.RESCHEDULED);

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final ApplicationRepository applicationRepository;
    private final SelectionRoundRepository selectionRoundRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public InterviewScheduleService(InterviewScheduleRepository interviewScheduleRepository,
                                    ApplicationRepository applicationRepository,
                                    SelectionRoundRepository selectionRoundRepository,
                                    NotificationService notificationService,
                                    UserRepository userRepository) {
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.applicationRepository = applicationRepository;
        this.selectionRoundRepository = selectionRoundRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public InterviewScheduleResponse create(Long roundId, InterviewScheduleRequest request) {
        SelectionRound round = selectionRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection round", "id", roundId));
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", request.getApplicationId()));
        validateApplicationAndRound(application, round);
        validateScheduleDetails(request.getScheduledStartTime(), request.getScheduledEndTime(),
                request.getMode(), request.getMeetingLink(), request.getVenue());
        ensureNoApplicationScheduleConflict(application.getId(), roundId, null);
        ensureNoInterviewerConflict(request.getInterviewerEmail(), request.getScheduledStartTime(),
                request.getScheduledEndTime(), null);

        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setApplication(application);
        schedule.setSelectionRound(round);
        applyDetails(schedule, request);
        schedule.setStatus(InterviewScheduleStatus.SCHEDULED);
        InterviewSchedule saved;
        try {
            saved = interviewScheduleRepository.saveAndFlush(schedule);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("An active schedule already exists for this application and round");
        }
        createNotification(saved, NotificationType.INTERVIEW_SCHEDULED, "Interview Scheduled",
                "Your interview for " + saved.getSelectionRound().getRoundName() + " is scheduled for "
                        + saved.getScheduledStartTime() + " (" + saved.getMode() + ").");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InterviewScheduleResponse getById(Long scheduleId) {
        return toResponse(findSchedule(scheduleId));
    }

    @Transactional(readOnly = true)
    public InterviewScheduleResponse getByIdForRecruiter(Long scheduleId, String email) {
        InterviewSchedule schedule = findSchedule(scheduleId);
        verifyRecruiterOwnsCompany(schedule.getSelectionRound().getPlacementDrive().getCompany().getId(), email);
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public InterviewScheduleResponse getByIdForStudent(Long scheduleId, String email) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdAndApplication_Student_Email(scheduleId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Interview schedule", "id", scheduleId));
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<InterviewScheduleResponse> getForRound(Long roundId) {
        if (!selectionRoundRepository.existsById(roundId)) {
            throw new ResourceNotFoundException("Selection round", "id", roundId);
        }
        return interviewScheduleRepository.findAllBySelectionRound_IdOrderByScheduledStartTimeAsc(roundId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewScheduleResponse> getForRoundForRecruiter(Long roundId, String email) {
        SelectionRound round = selectionRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection round", "id", roundId));
        verifyRecruiterOwnsCompany(round.getPlacementDrive().getCompany().getId(), email);
        return interviewScheduleRepository.findAllBySelectionRound_IdOrderByScheduledStartTimeAsc(roundId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewScheduleResponse> getForStudentApplication(Long applicationId, String email) {
        applicationRepository.findByIdAndStudent_Email(applicationId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        return interviewScheduleRepository.findAllByApplication_IdOrderByScheduledStartTimeAsc(applicationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public InterviewScheduleResponse reschedule(Long scheduleId, InterviewScheduleUpdateRequest request) {
        InterviewSchedule schedule = findScheduleForUpdate(scheduleId);
        if (schedule.getStatus() == InterviewScheduleStatus.CANCELLED
                || schedule.getStatus() == InterviewScheduleStatus.COMPLETED) {
            throw new BadRequestException("A " + schedule.getStatus() + " schedule cannot be rescheduled");
        }

        validateApplicationAndRound(schedule.getApplication(), schedule.getSelectionRound());
        validateScheduleDetails(request.getScheduledStartTime(), request.getScheduledEndTime(),
                request.getMode(), request.getMeetingLink(), request.getVenue());
        ensureNoApplicationScheduleConflict(schedule.getApplication().getId(),
                schedule.getSelectionRound().getId(), scheduleId);
        ensureNoInterviewerConflict(request.getInterviewerEmail(), request.getScheduledStartTime(),
                request.getScheduledEndTime(), scheduleId);

        applyDetails(schedule, request);
        schedule.setStatus(InterviewScheduleStatus.RESCHEDULED);
        InterviewSchedule saved = interviewScheduleRepository.saveAndFlush(schedule);
        createNotification(saved, NotificationType.INTERVIEW_RESCHEDULED, "Interview Rescheduled",
                "Your interview for " + saved.getSelectionRound().getRoundName() + " has been rescheduled to "
                        + saved.getScheduledStartTime() + " (" + saved.getMode() + ").");
        return toResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse cancel(Long scheduleId) {
        InterviewSchedule schedule = findScheduleForUpdate(scheduleId);
        if (schedule.getStatus() == InterviewScheduleStatus.COMPLETED) {
            throw new BadRequestException("A completed schedule cannot be cancelled");
        }
        if (schedule.getStatus() == InterviewScheduleStatus.CANCELLED) {
            throw new BadRequestException("Schedule is already cancelled");
        }
        schedule.setStatus(InterviewScheduleStatus.CANCELLED);
        InterviewSchedule saved = interviewScheduleRepository.saveAndFlush(schedule);
        notifyCancellation(saved);
        return toResponse(saved);
    }

    @Transactional
    public InterviewScheduleResponse updateStatus(Long scheduleId, InterviewScheduleStatus targetStatus) {
        InterviewSchedule schedule = findScheduleForUpdate(scheduleId);
        validateStatusTransition(schedule.getStatus(), targetStatus);
        schedule.setStatus(targetStatus);
        InterviewSchedule saved = interviewScheduleRepository.saveAndFlush(schedule);
        if (targetStatus == InterviewScheduleStatus.CANCELLED) {
            notifyCancellation(saved);
        } else if (targetStatus == InterviewScheduleStatus.RESCHEDULED) {
            createNotification(saved, NotificationType.INTERVIEW_RESCHEDULED, "Interview Rescheduled",
                    "Your interview for " + saved.getSelectionRound().getRoundName() + " is scheduled for "
                            + saved.getScheduledStartTime() + " (" + saved.getMode() + ").");
        }
        return toResponse(saved);
    }

    private void notifyCancellation(InterviewSchedule schedule) {
        createNotification(schedule, NotificationType.INTERVIEW_CANCELLED, "Interview Cancelled",
                "Your interview for " + schedule.getSelectionRound().getRoundName() + " has been cancelled.");
    }

    private void createNotification(
            InterviewSchedule schedule, NotificationType type, String title, String message) {
        notificationService.createNotification(schedule.getApplication().getStudent(), title, message, type,
                NotificationReferenceType.INTERVIEW_SCHEDULE, schedule.getId());
    }

    private void validateApplicationAndRound(Application application, SelectionRound round) {
        if (application.getStatus() != ApplicationStatus.SHORTLISTED) {
            throw new BadRequestException("Only SHORTLISTED applications can be scheduled");
        }
        if (round.getRoundType() == null || !round.getRoundType().isInterviewType()) {
            throw new BadRequestException("Only interview-type selection rounds can have interview schedules");
        }
        if (!application.getPlacementDrive().getId().equals(round.getPlacementDrive().getId())) {
            throw new BadRequestException("Application and selection round must belong to the same drive");
        }
        if (round.getStatus() == SelectionRoundStatus.CANCELLED
                || round.getStatus() == SelectionRoundStatus.COMPLETED) {
            throw new BadRequestException("Candidates cannot be scheduled for a "
                    + round.getStatus() + " selection round");
        }
    }

    private void validateScheduleDetails(
            LocalDateTime start, LocalDateTime end, InterviewMode mode, String meetingLink, String venue) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BadRequestException("Scheduled start time must be before scheduled end time");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Scheduled start time cannot be in the past");
        }
        if (mode == null) {
            throw new BadRequestException("Interview mode is required");
        }
        if (mode == InterviewMode.ONLINE && (meetingLink == null || meetingLink.isBlank())) {
            throw new BadRequestException("Online schedules require a meeting link");
        }
        if (mode == InterviewMode.OFFLINE && (venue == null || venue.isBlank())) {
            throw new BadRequestException("Offline schedules require a venue");
        }
    }

    private void ensureNoApplicationScheduleConflict(Long applicationId, Long roundId, Long excludedScheduleId) {
        List<InterviewSchedule> schedules = interviewScheduleRepository
                .findAllByApplication_IdOrderByScheduledStartTimeAsc(applicationId);
        boolean conflict = schedules.stream().anyMatch(schedule ->
                schedule.getSelectionRound().getId().equals(roundId)
                        && ACTIVE_STATUSES.contains(schedule.getStatus())
                        && (excludedScheduleId == null || !schedule.getId().equals(excludedScheduleId)));
        if (conflict) {
            throw new BadRequestException(
                    "An active schedule already exists for this application and round");
        }
    }

    private void ensureNoInterviewerConflict(
            String interviewerEmail, LocalDateTime start, LocalDateTime end, Long excludedScheduleId) {
        if (interviewerEmail != null && !interviewerEmail.isBlank()
                && interviewScheduleRepository.existsInterviewerConflict(
                interviewerEmail.trim(), ACTIVE_STATUSES, start, end, excludedScheduleId)) {
            throw new BadRequestException("Interviewer already has an overlapping active interview schedule");
        }
    }

    private void validateStatusTransition(
            InterviewScheduleStatus current, InterviewScheduleStatus target) {
        boolean allowed = (current == InterviewScheduleStatus.SCHEDULED
                && (target == InterviewScheduleStatus.COMPLETED
                || target == InterviewScheduleStatus.CANCELLED
                || target == InterviewScheduleStatus.RESCHEDULED))
                || (current == InterviewScheduleStatus.RESCHEDULED
                && (target == InterviewScheduleStatus.COMPLETED
                || target == InterviewScheduleStatus.CANCELLED));
        if (!allowed) {
            throw new BadRequestException(
                    "Invalid interview schedule status transition from " + current + " to " + target);
        }
    }

    private InterviewSchedule findSchedule(Long scheduleId) {
        return interviewScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview schedule", "id", scheduleId));
    }

    private InterviewSchedule findScheduleForUpdate(Long scheduleId) {
        return interviewScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview schedule", "id", scheduleId));
    }

    private void verifyRecruiterOwnsCompany(Long companyId, String email) {
        User recruiter = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (recruiter.getRole() != Role.COMPANY_RECRUITER || recruiter.getCompany() == null) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a company");
        }
        if (!recruiter.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("You do not have access to this placement drive");
        }
    }

    private void applyDetails(InterviewSchedule schedule, InterviewScheduleRequest request) {
        schedule.setScheduledStartTime(request.getScheduledStartTime());
        schedule.setScheduledEndTime(request.getScheduledEndTime());
        schedule.setMode(request.getMode());
        schedule.setMeetingLink(trimToNull(request.getMeetingLink()));
        schedule.setVenue(trimToNull(request.getVenue()));
        schedule.setInterviewerName(trimToNull(request.getInterviewerName()));
        schedule.setInterviewerEmail(trimToNull(request.getInterviewerEmail()));
        schedule.setNotes(trimToNull(request.getNotes()));
    }

    private void applyDetails(InterviewSchedule schedule, InterviewScheduleUpdateRequest request) {
        schedule.setScheduledStartTime(request.getScheduledStartTime());
        schedule.setScheduledEndTime(request.getScheduledEndTime());
        schedule.setMode(request.getMode());
        schedule.setMeetingLink(trimToNull(request.getMeetingLink()));
        schedule.setVenue(trimToNull(request.getVenue()));
        schedule.setInterviewerName(trimToNull(request.getInterviewerName()));
        schedule.setInterviewerEmail(trimToNull(request.getInterviewerEmail()));
        schedule.setNotes(trimToNull(request.getNotes()));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InterviewScheduleResponse toResponse(InterviewSchedule schedule) {
        InterviewScheduleResponse response = new InterviewScheduleResponse();
        response.setScheduleId(schedule.getId());
        response.setApplicationId(schedule.getApplication().getId());
        response.setStudentName(schedule.getApplication().getStudent().getName());
        response.setStudentEmail(schedule.getApplication().getStudent().getEmail());
        response.setRoundId(schedule.getSelectionRound().getId());
        response.setRoundName(schedule.getSelectionRound().getRoundName());
        response.setScheduledStartTime(schedule.getScheduledStartTime());
        response.setScheduledEndTime(schedule.getScheduledEndTime());
        response.setMode(schedule.getMode());
        response.setMeetingLink(schedule.getMeetingLink());
        response.setVenue(schedule.getVenue());
        response.setInterviewerName(schedule.getInterviewerName());
        response.setInterviewerEmail(schedule.getInterviewerEmail());
        response.setStatus(schedule.getStatus());
        response.setNotes(schedule.getNotes());
        response.setCreatedAt(schedule.getCreatedAt());
        response.setUpdatedAt(schedule.getUpdatedAt());
        return response;
    }
}
