package com.example.backend.selection.repository;

import com.example.backend.selection.entity.RoundResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoundResultRepository extends JpaRepository<RoundResult, Long> {
    boolean existsBySelectionRound_Id(Long roundId);
    boolean existsByApplication_IdAndSelectionRound_Id(Long applicationId, Long roundId);
    List<RoundResult> findAllBySelectionRound_IdOrderByApplication_Student_NameAsc(Long roundId);
    List<RoundResult> findAllByApplication_IdAndApplication_Student_EmailOrderBySelectionRound_RoundOrderAsc(
            Long applicationId, String email);
}
