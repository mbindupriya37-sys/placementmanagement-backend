package com.example.backend.application.repository;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByStudent_IdAndPlacementDrive_Id(Long studentId, Long placementDriveId);
    List<Application> findAllByStudent_EmailOrderByAppliedAtDesc(String email);
    Optional<Application> findByIdAndStudent_Email(Long id, String email);
    List<Application> findAllByPlacementDrive_IdOrderByAppliedAtAsc(Long placementDriveId);
    List<Application> findAllByPlacementDrive_IdAndStatus(Long placementDriveId, ApplicationStatus status);
    List<Application> findAllByIdIn(List<Long> ids);
    Optional<Application> findByIdAndPlacementDrive_Company_Id(Long applicationId, Long companyId);

    @EntityGraph(attributePaths = {"student", "student.studentProfile"})
    @Query("select distinct application from Application application "
            + "where application.placementDrive.company.id = :companyId "
            + "and (:driveId is null or application.placementDrive.id = :driveId) "
            + "and (:status is null or application.status = :status) "
            + "order by application.appliedAt desc")
    List<Application> findRecruiterApplications(
            @Param("companyId") Long companyId,
            @Param("driveId") Long driveId,
            @Param("status") ApplicationStatus status);
}
