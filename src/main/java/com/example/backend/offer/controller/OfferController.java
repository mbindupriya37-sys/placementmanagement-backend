package com.example.backend.offer.controller;

import com.example.backend.common.dto.ApiResponse;
import com.example.backend.offer.dto.OfferRequest;
import com.example.backend.offer.dto.OfferResponse;
import com.example.backend.offer.dto.OfferUpdateRequest;
import com.example.backend.offer.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Offers", description = "Manage and respond to job offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/api/applications/{applicationId}/offer")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Create a draft offer for a selected application (TPO_ADMIN only)")
    public ApiResponse<OfferResponse> create(
            @PathVariable Long applicationId,
            @Valid @RequestBody OfferRequest request) {
        return ApiResponse.success(offerService.create(applicationId, request), "Offer created successfully");
    }

    @PutMapping("/api/offers/{offerId}")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Update offer details (TPO_ADMIN only)")
    public ApiResponse<OfferResponse> update(
            @PathVariable Long offerId,
            @Valid @RequestBody OfferUpdateRequest request) {
        return ApiResponse.success(offerService.update(offerId, request), "Offer updated successfully");
    }

    @PatchMapping("/api/offers/{offerId}/issue")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Issue a draft offer (TPO_ADMIN only)")
    public ApiResponse<OfferResponse> issue(@PathVariable Long offerId) {
        return ApiResponse.success(offerService.issue(offerId), "Offer issued successfully");
    }

    @PatchMapping("/api/offers/{offerId}/withdraw")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "Withdraw an issued offer (TPO_ADMIN only)")
    public ApiResponse<OfferResponse> withdraw(@PathVariable Long offerId) {
        return ApiResponse.success(offerService.withdraw(offerId), "Offer withdrawn successfully");
    }

    @PatchMapping("/api/offers/{offerId}/accept")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Accept an issued offer belonging to the authenticated student")
    public ApiResponse<OfferResponse> accept(
            @PathVariable Long offerId,
            Authentication authentication) {
        return ApiResponse.success(offerService.accept(offerId, authentication.getName()),
                "Offer accepted successfully");
    }

    @PatchMapping("/api/offers/{offerId}/decline")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Decline an issued offer belonging to the authenticated student")
    public ApiResponse<OfferResponse> decline(
            @PathVariable Long offerId,
            Authentication authentication) {
        return ApiResponse.success(offerService.decline(offerId, authentication.getName()),
                "Offer declined successfully");
    }

    @GetMapping("/api/offers/{offerId}")
    @PreAuthorize("hasAnyRole('TPO_ADMIN', 'STUDENT')")
    @Operation(summary = "View an offer (TPO_ADMIN or offer-owning STUDENT)")
    public ApiResponse<OfferResponse> get(
            @PathVariable Long offerId,
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TPO_ADMIN"));
        return ApiResponse.success(offerService.get(offerId, authentication.getName(), admin));
    }

    @GetMapping("/api/drives/{driveId}/offers")
    @PreAuthorize("hasRole('TPO_ADMIN')")
    @Operation(summary = "List offers for a drive (TPO_ADMIN only)")
    public ApiResponse<List<OfferResponse>> getForDrive(@PathVariable Long driveId) {
        return ApiResponse.success(offerService.getForDrive(driveId));
    }

    @GetMapping("/api/offers/my")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "List the authenticated student's offers")
    public ApiResponse<List<OfferResponse>> getMine(Authentication authentication) {
        return ApiResponse.success(offerService.getMyOffers(authentication.getName()));
    }
}
