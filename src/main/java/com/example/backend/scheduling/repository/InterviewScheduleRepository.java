package com.example.backend.scheduling.repository;

import com.example.backend.scheduling.entity.InterviewSchedule;
import com.example.backend.scheduling.entity.InterviewScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {

    List<InterviewSchedule> findAllBySelectionRound_IdOrderByScheduledStartTimeAsc(Long roundId);

    List<InterviewSchedule> findAllByApplication_IdOrderByScheduledStartTimeAsc(Long applicationId);

    Optional<InterviewSchedule> findByIdAndApplication_Student_Email(Long id, String email);

    boolean existsByApplication_IdAndSelectionRound_IdAndStatusIn(
            Long applicationId, Long roundId, List<InterviewScheduleStatus> statuses);

    @Query("""
            select count(schedule) > 0
            from InterviewSchedule schedule
            where lower(schedule.interviewerEmail) = lower(:interviewerEmail)
              and schedule.status in :statuses
              and schedule.scheduledStartTime < :endTime
              and schedule.scheduledEndTime > :startTime
              and (:excludedScheduleId is null or schedule.id <> :excludedScheduleId)
            """)
    boolean existsInterviewerConflict(
            @Param("interviewerEmail") String interviewerEmail,
            @Param("statuses") List<InterviewScheduleStatus> statuses,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedScheduleId") Long excludedScheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from InterviewSchedule schedule where schedule.id = :scheduleId")
    Optional<InterviewSchedule> findByIdForUpdate(@Param("scheduleId") Long scheduleId);
}
