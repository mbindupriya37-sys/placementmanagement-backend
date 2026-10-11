package com.example.backend.offer.repository;

import com.example.backend.offer.entity.Offer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    boolean existsByApplication_Id(Long applicationId);
    boolean existsByOfferLetterNumberIgnoreCase(String offerLetterNumber);
    List<Offer> findAllByApplication_PlacementDrive_IdOrderByOfferDateDesc(Long driveId);
    List<Offer> findAllByApplication_Student_EmailOrderByCreatedAtDesc(String email);
    Optional<Offer> findByIdAndApplication_Student_Email(Long id, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from Offer offer where offer.id = :id and offer.application.student.email = :email")
    Optional<Offer> findByIdAndApplication_Student_EmailForUpdate(
            @Param("id") Long id, @Param("email") String email);
}
