package com.example.backend.recruiter.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.dto.PlacementDriveResponse;
import com.example.backend.drive.service.PlacementDriveService;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import com.example.backend.recruiter.dto.RecommendationRequest;
import com.example.backend.recruiter.dto.RecommendationResponse;
import com.example.backend.recruiter.dto.RecruiterApplicationResponse;
import com.example.backend.recruiter.entity.CandidateRecommendation;
import com.example.backend.recruiter.entity.RecommendationStatus;
import com.example.backend.recruiter.repository.CandidateRecommendationRepository;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecruiterService {

    private final UserRepository userRepository;
    private final PlacementDriveService placementDriveService;
    private final ApplicationRepository applicationRepository;
    private final CandidateRecommendationRepository recommendationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NotificationService notificationService;

    public RecruiterService(
            UserRepository userRepository,
            PlacementDriveService placementDriveService,
            ApplicationRepository applicationRepository,
            CandidateRecommendationRepository recommendationRepository,
            StudentProfileRepository studentProfileRepository,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.placementDriveService = placementDriveService;
        this.applicationRepository = applicationRepository;
        this.recommendationRepository = recommendationRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PlacementDriveResponse> getMyDrives(String email) {
        User recruiter = getAssignedRecruiter(email);
        return placementDriveService.getAllByCompanyId(recruiter.getCompany().getId());
    }

    @Transactional(readOnly = true)
    public List<RecruiterApplicationResponse> getApplications(
            String email, Long driveId, ApplicationStatus status) {
        User recruiter = getAssignedRecruiter(email);
        List<Application> applications = applicationRepository.findRecruiterApplications(
                recruiter.getCompany().getId(), driveId, status);
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<String, StudentProfile> profilesByEmail = studentProfileRepository.findAllByUser_EmailIn(
                        applications.stream().map(application -> application.getStudent().getEmail()).toList())
                .stream().collect(Collectors.toMap(profile -> profile.getUser().getEmail(), Function.identity()));
        return applications.stream()
                .map(application -> toApplicationResponse(
                        application, profilesByEmail.get(application.getStudent().getEmail())))
                .toList();
    }

    @Transactional
    public RecommendationResponse submitRecommendation(String email, RecommendationRequest request) {
        User recruiter = getAssignedRecruiter(email);
        if (request == null || request.getApplicationId() == null) {
            throw new BadRequestException("Application ID is required");
        }
        if (request.getReason() == null || request.getReason().isBlank()
                || request.getReason().trim().length() > 500) {
            throw new BadRequestException("Recommendation reason is required and must not exceed 500 characters");
        }
        Application application = applicationRepository.findByIdAndPlacementDrive_Company_Id(
                        request.getApplicationId(), recruiter.getCompany().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", "id", request.getApplicationId()));
        if (recommendationRepository.existsByApplication_IdAndRecruiter_Email(application.getId(), email)) {
            throw new BadRequestException("You have already submitted a recommendation for this application");
        }

        CandidateRecommendation recommendation = new CandidateRecommendation();
        recommendation.setApplication(application);
        recommendation.setRecruiter(recruiter);
        recommendation.setReason(request.getReason().trim());
        recommendation.setStatus(RecommendationStatus.PENDING);
        recommendation = recommendationRepository.saveAndFlush(recommendation);
        for (User tpoAdmin : userRepository.findAllByRoleForUpdate(Role.TPO_ADMIN)) {
            notificationService.createNotification(tpoAdmin, "Candidate recommendation received",
                    recruiter.getName() + " recommended " + application.getStudent().getName()
                            + " for " + application.getPlacementDrive().getDriveTitle() + ".",
                    NotificationType.CANDIDATE_RECOMMENDATION,
                    NotificationReferenceType.RECOMMENDATION, recommendation.getId());
        }
        return toRecommendationResponse(recommendation);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> getMyRecommendations(String email) {
        getAssignedRecruiter(email);
        return recommendationRepository.findAllByRecruiter_EmailOrderBySubmittedAtDesc(email)
                .stream().map(this::toRecommendationResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> getAllRecommendations() {
        return recommendationRepository.findAllByOrderBySubmittedAtDesc()
                .stream().map(this::toRecommendationResponse).toList();
    }

    @Transactional
    public RecommendationResponse updateRecommendationStatus(Long recommendationId, RecommendationStatus status) {
        if (status == null || status == RecommendationStatus.PENDING) {
            throw new BadRequestException("A recommendation can only be approved or declined");
        }
        CandidateRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recommendation", "id", recommendationId));
        if (recommendation.getStatus() != RecommendationStatus.PENDING) {
            throw new BadRequestException("Only pending recommendations can be reviewed");
        }
        recommendation.setStatus(status);
        recommendation = recommendationRepository.saveAndFlush(recommendation);
        String decision = status == RecommendationStatus.APPROVED ? "approved" : "declined";
        notificationService.createNotification(recommendation.getRecruiter(),
                "Candidate recommendation " + decision,
                "The placement office has " + decision + " your recommendation for "
                        + recommendation.getApplication().getStudent().getName() + ".",
                NotificationType.CANDIDATE_RECOMMENDATION,
                NotificationReferenceType.RECOMMENDATION, recommendation.getId());
        return toRecommendationResponse(recommendation);
    }

    private User getAssignedRecruiter(String email) {
        User recruiter = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (recruiter.getRole() != Role.COMPANY_RECRUITER) {
            throw new AccessDeniedException("Company recruiter role is required");
        }
        if (recruiter.getCompany() == null) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a company");
        }
        return recruiter;
    }

    private RecruiterApplicationResponse toApplicationResponse(Application application, StudentProfile profile) {
        RecruiterApplicationResponse response = new RecruiterApplicationResponse();
        response.setApplicationId(application.getId());
        response.setDriveId(application.getPlacementDrive().getId());
        response.setDriveTitle(application.getPlacementDrive().getDriveTitle());
        response.setStudentName(application.getStudent().getName());
        response.setStatus(application.getStatus());
        response.setAppliedAt(application.getAppliedAt());
        if (profile != null) {
            response.setBranch(profile.getBranch());
            response.setCgpa(profile.getCgpa());
            response.setSkills(List.copyOf(profile.getSkills()));
            String resumeUrl = profile.getResumeUrl();
            response.setResumeAvailable(resumeUrl != null && !resumeUrl.isBlank());
        } else {
            response.setSkills(List.of());
        }
        return response;
    }

    private RecommendationResponse toRecommendationResponse(CandidateRecommendation recommendation) {
        RecommendationResponse response = new RecommendationResponse();
        response.setRecommendationId(recommendation.getId());
        response.setApplicationId(recommendation.getApplication().getId());
        response.setDriveId(recommendation.getApplication().getPlacementDrive().getId());
        response.setDriveTitle(recommendation.getApplication().getPlacementDrive().getDriveTitle());
        response.setStudentName(recommendation.getApplication().getStudent().getName());
        response.setRecruiterEmail(recommendation.getRecruiter().getEmail());
        response.setReason(recommendation.getReason());
        response.setStatus(recommendation.getStatus());
        response.setSubmittedAt(recommendation.getSubmittedAt());
        response.setReviewedAt(recommendation.getReviewedAt());
        return response;
    }
}
