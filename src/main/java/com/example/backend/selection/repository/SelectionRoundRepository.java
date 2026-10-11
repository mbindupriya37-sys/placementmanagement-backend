package com.example.backend.selection.repository;

import com.example.backend.selection.entity.SelectionRound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SelectionRoundRepository extends JpaRepository<SelectionRound, Long> {
    List<SelectionRound> findAllByPlacementDrive_IdOrderByRoundOrderAsc(Long driveId);
    boolean existsByPlacementDrive_IdAndRoundOrder(Long driveId, Integer roundOrder);
    boolean existsByPlacementDrive_IdAndRoundOrderAndIdNot(Long driveId, Integer roundOrder, Long id);
    Optional<SelectionRound> findByIdAndPlacementDrive_Id(Long id, Long driveId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select round from SelectionRound round where round.id = :roundId")
    Optional<SelectionRound> findByIdForUpdate(@Param("roundId") Long roundId);
}
