package com.example.backend.finaldecision.repository;

import com.example.backend.finaldecision.entity.FinalDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinalDecisionRecordRepository extends JpaRepository<FinalDecisionRecord, Long> {
    boolean existsByApplication_Id(Long applicationId);
    Optional<FinalDecisionRecord> findByApplication_Id(Long applicationId);
    Optional<FinalDecisionRecord> findByApplication_IdAndApplication_Student_Email(Long applicationId, String email);
    List<FinalDecisionRecord> findAllByApplication_PlacementDrive_IdOrderByDecidedAtDesc(Long driveId);
}
