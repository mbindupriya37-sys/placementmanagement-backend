package com.example.backend.offer.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.drive.repository.PlacementDriveRepository;
import com.example.backend.offer.dto.OfferRequest;
import com.example.backend.offer.dto.OfferResponse;
import com.example.backend.offer.dto.OfferUpdateRequest;
import com.example.backend.offer.entity.Offer;
import com.example.backend.offer.entity.OfferStatus;
import com.example.backend.offer.repository.OfferRepository;
import com.example.backend.notification.entity.NotificationReferenceType;
import com.example.backend.notification.entity.NotificationType;
import com.example.backend.notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public OfferService(OfferRepository offerRepository,
                        ApplicationRepository applicationRepository,
                        PlacementDriveRepository placementDriveRepository,
                        NotificationService notificationService,
                        UserRepository userRepository) {
        this.offerRepository = offerRepository;
        this.applicationRepository = applicationRepository;
        this.placementDriveRepository = placementDriveRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public OfferResponse create(Long applicationId, OfferRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        if (application.getStatus() != ApplicationStatus.SELECTED) {
            throw new BadRequestException("Offers can only be created for SELECTED applications");
        }
        if (offerRepository.existsByApplication_Id(applicationId)) {
            throw new BadRequestException("An offer already exists for this application");
        }
        if (offerRepository.existsByOfferLetterNumberIgnoreCase(request.getOfferLetterNumber().trim())) {
            throw new BadRequestException("Offer letter number is already in use");
        }
        validateDates(request.getJoiningDate(), request.getExpiryDate(), LocalDate.now());

        Offer offer = new Offer();
        offer.setApplication(application);
        offer.setOfferLetterNumber(request.getOfferLetterNumber().trim());
        offer.setJobTitle(request.getJobTitle().trim());
        offer.setEmploymentType(request.getEmploymentType());
        offer.setLocation(trimToNull(request.getLocation()));
        offer.setAnnualPackageLpa(request.getAnnualPackageLpa());
        offer.setJoiningDate(request.getJoiningDate());
        offer.setExpiryDate(request.getExpiryDate());
        offer.setStatus(OfferStatus.DRAFT);
        offer.setNotes(trimToNull(request.getNotes()));
        try {
            return toResponse(offerRepository.saveAndFlush(offer));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Offer application or offer letter number must be unique");
        }
    }

    @Transactional
    public OfferResponse update(Long offerId, OfferUpdateRequest request) {
        Offer offer = findOffer(offerId);
        if (offer.getStatus() == OfferStatus.ACCEPTED || offer.getStatus() == OfferStatus.DECLINED
                || offer.getStatus() == OfferStatus.WITHDRAWN) {
            throw new BadRequestException("A " + offer.getStatus() + " offer cannot be updated");
        }
        LocalDate effectiveOfferDate = offer.getOfferDate() == null ? LocalDate.now() : offer.getOfferDate();
        validateDates(request.getJoiningDate(), request.getExpiryDate(), effectiveOfferDate);
        offer.setJobTitle(request.getJobTitle().trim());
        offer.setEmploymentType(request.getEmploymentType());
        offer.setLocation(trimToNull(request.getLocation()));
        offer.setAnnualPackageLpa(request.getAnnualPackageLpa());
        offer.setJoiningDate(request.getJoiningDate());
        offer.setExpiryDate(request.getExpiryDate());
        offer.setNotes(trimToNull(request.getNotes()));
        return toResponse(offerRepository.saveAndFlush(offer));
    }

    @Transactional
    public OfferResponse issue(Long offerId) {
        Offer offer = findOffer(offerId);
        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT offers can be issued");
        }
        if (offer.getApplication().getStatus() != ApplicationStatus.SELECTED) {
            throw new BadRequestException("The application must still be SELECTED to issue an offer");
        }
        LocalDate offerDate = LocalDate.now();
        validateDates(offer.getJoiningDate(), offer.getExpiryDate(), offerDate);
        offer.setOfferDate(offerDate);
        offer.setStatus(OfferStatus.ISSUED);
        Offer saved = offerRepository.saveAndFlush(offer);
        notifyStudent(saved, NotificationType.OFFER_ISSUED, "Offer Issued",
                "An offer for " + saved.getJobTitle() + " has been issued.");
        return toResponse(saved);
    }

    @Transactional
    public OfferResponse accept(Long offerId, String studentEmail) {
        Offer offer = offerRepository.findByIdAndApplication_Student_EmailForUpdate(offerId, studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));
        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new BadRequestException("Only ISSUED offers can be accepted");
        }
        if (offer.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Offer has expired and cannot be accepted");
        }
        offer.setStatus(OfferStatus.ACCEPTED);
        Offer saved = offerRepository.saveAndFlush(offer);
        String message = saved.getApplication().getStudent().getName() + " accepted the offer for "
                + saved.getJobTitle() + ".";
        notificationService.createNotificationIfAbsent(saved.getApplication().getStudent(), "Offer Accepted",
                "You accepted the offer for " + saved.getJobTitle() + ".", NotificationType.OFFER_ACCEPTED,
                NotificationReferenceType.OFFER, saved.getId());
        userRepository.findAllByRole(Role.TPO_ADMIN).forEach(tpo ->
                notificationService.createNotificationIfAbsent(tpo, "Offer Accepted", message,
                        NotificationType.OFFER_ACCEPTED, NotificationReferenceType.OFFER, saved.getId()));
        return toResponse(saved);
    }

    @Transactional
    public OfferResponse decline(Long offerId, String studentEmail) {
        Offer offer = findOfferForStudent(offerId, studentEmail);
        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new BadRequestException("Only ISSUED offers can be declined");
        }
        offer.setStatus(OfferStatus.DECLINED);
        Offer saved = offerRepository.saveAndFlush(offer);
        notifyStudent(saved, NotificationType.OFFER_DECLINED, "Offer Declined",
                "You declined the offer for " + saved.getJobTitle() + ".");
        return toResponse(saved);
    }

    @Transactional
    public OfferResponse withdraw(Long offerId) {
        Offer offer = findOffer(offerId);
        if (offer.getStatus() != OfferStatus.ISSUED) {
            throw new BadRequestException("Only ISSUED offers can be withdrawn");
        }
        offer.setStatus(OfferStatus.WITHDRAWN);
        return toResponse(offerRepository.saveAndFlush(offer));
    }

    @Transactional(readOnly = true)
    public OfferResponse get(Long offerId, String email, boolean admin) {
        Offer offer = admin ? findOffer(offerId) : findOfferForStudent(offerId, email);
        return toResponse(offer);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getForDrive(Long driveId) {
        if (!placementDriveRepository.existsById(driveId)) {
            throw new ResourceNotFoundException("Placement drive", "id", driveId);
        }
        return offerRepository.findAllByApplication_PlacementDrive_IdOrderByOfferDateDesc(driveId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getMyOffers(String email) {
        return offerRepository.findAllByApplication_Student_EmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    private void validateDates(LocalDate joiningDate, LocalDate expiryDate, LocalDate offerDate) {
        if (expiryDate == null || expiryDate.isBefore(offerDate)) {
            throw new BadRequestException("Expiry date must be on or after the offer date");
        }
        if (joiningDate == null || joiningDate.isBefore(offerDate)) {
            throw new BadRequestException("Joining date must be on or after the offer date");
        }
    }

    private void notifyStudent(Offer offer, NotificationType type, String title, String message) {
        // The offer model has no safe association to the TPO who issued it, so these status events stay with the student.
        notificationService.createNotification(offer.getApplication().getStudent(), title, message, type,
                NotificationReferenceType.OFFER, offer.getId());
    }

    private Offer findOffer(Long offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));
    }

    private Offer findOfferForStudent(Long offerId, String email) {
        return offerRepository.findByIdAndApplication_Student_Email(offerId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OfferResponse toResponse(Offer offer) {
        OfferResponse response = new OfferResponse();
        response.setOfferId(offer.getId());
        response.setApplicationId(offer.getApplication().getId());
        response.setStudentName(offer.getApplication().getStudent().getName());
        response.setStudentEmail(offer.getApplication().getStudent().getEmail());
        response.setDriveId(offer.getApplication().getPlacementDrive().getId());
        response.setDriveTitle(offer.getApplication().getPlacementDrive().getDriveTitle());
        response.setOfferLetterNumber(offer.getOfferLetterNumber());
        response.setJobTitle(offer.getJobTitle());
        response.setEmploymentType(offer.getEmploymentType());
        response.setLocation(offer.getLocation());
        response.setAnnualPackageLpa(offer.getAnnualPackageLpa());
        response.setJoiningDate(offer.getJoiningDate());
        response.setOfferDate(offer.getOfferDate());
        response.setExpiryDate(offer.getExpiryDate());
        response.setStatus(offer.getStatus());
        response.setNotes(offer.getNotes());
        response.setCreatedAt(offer.getCreatedAt());
        response.setUpdatedAt(offer.getUpdatedAt());
        return response;
    }
}
